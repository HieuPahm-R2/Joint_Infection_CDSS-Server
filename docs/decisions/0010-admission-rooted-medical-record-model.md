# 0010 Admission-rooted Medical Record Model and Vocabulary

Date: 2026-10-16

## Status

Accepted

## Context

`pji_episodes` is the row every clinical and decision-support table points at,
but it does not behave like an aggregate root, and its name does not match what
it holds.

Three layers are conflated in one ~30-column table:

- **Encounter logistics** — `admission_date`, `discharge_date`, `department`,
  `referral_source`, `hospital_transfer_*`, `discharge_disposition`,
  `admission_count`, `treatment_days`
  ([PjiEpisode.java](../../src/main/java/com/vietnam/pji/model/medical/PjiEpisode.java)).
- **Medical-record content** — `reason`, six free-text diagnosis columns,
  `has_complication`, `accompanying_disease`, `result`, `status`.
- **Record identity** — `medical_record_code` (`BA` + year + random),
  generated inside a JPA lifecycle callback
  ([V24__episode_medical_record_code.sql](../../src/main/resources/db/migration/V24__episode_medical_record_code.sql)).

Evidence that the model has stopped expressing the domain:

1. **Ownership is not in the aggregate.** `EpisodeRepository` declares no
   ownership or access query; record access lives in `episode_care_team` plus a
   transitional fallback on the free-text `created_by` email column.
2. **No cross-aggregate invariant is enforced by the database.** A run and its
   snapshot, a final selection and its run, and a doctor decision and its
   review can disagree while every individual foreign key stays valid
   ([database-architecture-assessment-2026-09-18.md](../database-architecture-assessment-2026-09-18.md) §5.2).
3. **The record has no temporal versioning.** `clinical_records` is 1:N in
   schema but 1:1 in behaviour ("latest upsert"), and the `recorded_at` column is
   orphaned. Because a past clinical picture cannot be reconstructed from the
   clinical tables, the AI layer re-materialises a full copy of the record as a
   snapshot on every generation.
4. **One-1-vs-1-N is still undecided per child**, and the decision leaks into the
   AI contract: the snapshot key `clinical_records` (plural) holds a single
   object.
5. **Names contradict their concept** — `LabResult` is a lab report with three
   JSONB panels, `ImageResult` is an uploaded study, `CaseClinicalSnapshot`
   means "episode clinical snapshot".
6. **Structured clinical concepts are stored as free text or blobs** — six
   diagnosis columns (V13 added and V14 removed their `_code` twins),
   `department_transfers` JSONB, and four `is_*`/`*_note` column pairs in
   `medical_histories`.
7. **Dead and drifted vocabulary** — `EpisodeResult` is an empty enum,
   `InfectionType` is unreferenced, `ImplantType` names an implant while being
   used for implant *stability*, and four PostgreSQL enum types are declared but
   are not the type of any column.

## Decision

The clinical case unit is an **Admission**: one inpatient stay, which is also one
medical record inside this product. The aggregate root keeps only identity,
lifecycle, and access. Record content moves to typed children, and the
decision-support artifacts stop being children of the record.

### 1. Root and identity

- `PjiEpisode` becomes `Admission`; table `pji_episodes` becomes `admissions`.
- One admission is one medical record. `medicalRecordCode` becomes
  `recordNumber` on `Admission`. No separate `MedicalRecord` entity is
  introduced while the relationship stays 1:1.
- Identifier generation moves out of the JPA lifecycle callback into the owning
  service, with a documented format and a retry on unique-constraint violation.

### 2. Vocabulary

| Current | Adopted | Note |
| --- | --- | --- |
| `PjiEpisode` / `pji_episodes` | `Admission` / `admissions` | Inpatient stay, not a serialised episode |
| `medicalRecordCode` | `recordNumber` | Record identity stays on `Admission` |
| `CaseClinicalSnapshot` | `ClinicalSnapshot` | "Case" here only ever meant the episode |
| `ClinicalRecord` | `ClinicalAssessment` (1:1) + `VitalSignsMeasurement` (1:N) | Splits assessment from time-series vitals |
| `LabResult` | `LabReport` | A row is a report with panels, not one result |
| `CultureResult` | `SpecimenCulture` | Specimen plus culture |
| `SensitivityResult` | `AntibioticSusceptibility` | CLSI/EUCAST naming; `sensitivityCode` becomes `interpretation` |
| `ImageResult` | `ImagingStudy` | An uploaded study, not a "result" |
| `DirectEnum` | `AdmissionRoute` | No `*Enum` suffix |
| `ImplantType` (used for `implantStability`) | `ImplantStability` | Enum named for the concept it holds |
| `EpisodeResult`, `InfectionType` | Deleted | Empty and unreferenced |

Rule: no `Pji` prefix and no `*Enum` suffix on domain types in this bounded
context; an enum is named for the concept it classifies.

### 3. Aggregate boundaries

Three aggregates replace one:

- **Clinical record** — `Admission`, `ClinicalAssessment`,
  `VitalSignsMeasurement`, `MedicalHistory`, `Surgery`. Holds the record's
  transactional invariants and its edit lock.
- **Diagnostic workup** — `LabReport`, `SpecimenCulture` +
  `AntibioticSusceptibility`, `ImagingStudy`. Append-only clinical facts written
  by several actors; must not require the record's edit lock.
- **Decision support** — `ClinicalSnapshot`, `AiRecommendationRun`, items,
  citations, reviews, doctor and pharmacist decisions, final selection. Refers to
  an admission by identifier only.

### 4. Lifecycle and integrity

- Foreign keys from the record aggregate down to the two lower aggregates become
  `ON DELETE RESTRICT`. A patient or admission delete must go through an explicit
  retention, anonymisation, or amendment procedure rather than cascading through
  signed decisions and snapshots.
- Snapshot, run, review, and signed decisions are append-only and amended, never
  updated in place, and that boundary is enforced in the database rather than
  only in services.
- Clinical child entities that can be written concurrently gain optimistic
  locking.
- Snapshot payloads carry an explicit `schema_version`.

### 5. Deferred decisions

- **Single hospital now.** No `tenant_id`/`facility_id` is added yet. When
  multi-hospital work starts, the facility key enters every tenant-scoped unique
  key, including `recordNumber`.
- **Coded diagnosis (ICD-10) later**, as a first-class diagnosis entity, not as
  additional columns on the root.
- **Frontend types are not renamed** in the first phase; only Java types,
  routes, and schema names change together per phase.
- **Switching the editor from one atomic aggregate save to per-child saves** is a
  later phase, because it changes user-visible behaviour and not only structure.

## Alternatives Considered

1. **Keep `Episode` and rename nothing.** Rejected: the vocabulary is the part
   that keeps producing the mismatch — the entity is read as a medical record in
   the product and written as an encounter in the schema.
2. **Split `MedicalRecord` from `Admission` immediately.** Rejected for now: the
   relationship is 1:1, so the split adds a table, a join, and a migration
   without changing any question the system can answer. It becomes correct only
   when one record must span several admissions.
3. **`Encounter` instead of `Admission`.** Rejected: every field is inpatient
   logistics, and `Encounter` promises outpatient and telehealth breadth the
   schema does not have.
4. **Keep one aggregate and fix only the names.** Rejected: the absence of
   database-enforced cross-aggregate invariants, the cascade into signed
   decisions, and the full-record snapshot copy are structural, not lexical.
5. **Version the clinical tables instead of extracting children.** Rejected as
   the first move: splitting vitals from the assessment is required before any
   temporal model can be expressed, and the split is independently useful.

## Consequences

Positive:

- The root describes one thing, so "what is this row?" has one answer.
- The decision-support context stops depending on record-table lifecycle, and
  clinical writers stop knowing about the AI snapshot cache.
- Immutable history stops being deletable by a patient cascade.
- A record can be reconstructed as of a past time without copying it, which is
  the precondition for removing the snapshot-as-copy design.

Tradeoffs:

- This is a breaking rename across Java types, routes, and schema names in three
  repositories, and it must be sequenced, not done at once.
- `VitalSignsMeasurement` splits a table that the editor currently edits as a
  single form, so the frontend needs a phase of its own.
- Restricting cascades makes some deletes fail until the retention procedure
  exists; that failure is deliberate, but it needs an operator path.
- Existing snapshot consumers in `Rag_Agentic` read literal keys; the payload
  contract must be versioned before any key changes.

## Follow-Up

- Execute [medical-record-model-renewal.md](../plans/active/medical-record-model-renewal.md).
- Keep the snapshot payload key set stable until `schema_version` exists, so
  renaming the record model does not silently change AI input.
- Supersede the stale `remove-pharmacist-final-decision` plan, which contradicts
  the live `pharmacist_final_decisions` schema (V21 dropped it, V28 recreated it).
- Resolve the duplicate `0007` entries in the decisions index.
