# Execution Plan: Medical Record Model Renewal

Date: 2026-10-16

## Status

Active

## Outcome

`Backend_Server` names and models the patient medical record the way the product
already describes it: an `Admission` root that holds only identity, lifecycle and
access; typed clinical children instead of free text and blobs; and
decision-support artifacts that reference the record instead of cascading from
it. The rename reaches Java types, API routes and schema names in sequenced,
independently shippable phases.

## Context

- Authority: [decision 0010](../decisions/0010-admission-rooted-medical-record-model.md).
- Database findings and priorities:
  [database-architecture-assessment-2026-09-18.md](../database-architecture-assessment-2026-09-18.md).
- Code findings and owner decisions:
  [architecture-review-2026-10-14.md](../architecture-review-2026-10-14.md).
- Authorization vocabulary: `CONTEXT.md`.
- Workflow: `docs/WORKFLOW.md`.
- Consumers of the current contract: `Frontend_Client` (`/episodes` routes and
  `IEpisode*` types) and `Rag_Agentic` (literal snapshot keys).

## Scope

In scope:

- Rename the medical-record entity set across Java types, routes, and schema.
- Split `ClinicalRecord` into `ClinicalAssessment` and `VitalSignsMeasurement`.
- Separate clinical-record, diagnostic-workup and decision-support aggregates.
- Make snapshot payload schema versioned before any payload key changes.
- Move identifier generation into the owning service.

Out of scope:

- Multi-hospital tenancy. No `tenant_id`/`facility_id` is added in this plan.
- Coded diagnosis (ICD-10) and a first-class diagnosis entity.
- Renaming `Frontend_Client` domain types.
- Changing the editor from one atomic save to per-child saves; that is a
  user-visible behaviour change with its own plan.
- Any snapshot payload key change before `schema_version` exists.

## Approach

Each phase ships on its own and keeps the other two repositories working.

1. **Authority** — decision 0010 accepted; the naming table in it is the
   vocabulary contract for every later phase.
2. **Java-type rename, contract-neutral** — rename types and packages only. The
   `@Table` names, API paths, JSON field names, and snapshot payload keys stay
   exactly as they are, so `Frontend_Client` and `Rag_Agentic` are untouched.
   This is the phase that removes the misleading vocabulary from the code.
3. **Route and contract rename** — `/episodes` becomes `/admissions` behind a
   deprecation window; the frontend path constants, `ALL_PERMISSIONS`, and the
   global-search property paths move with it, and the known frontend contract
   defects are fixed in the same change.
4. **Schema rename** — forward-only Flyway migration renaming tables and columns,
   with compatibility views for any consumer outside these repositories.
5. **Aggregate separation** — replace the shared snapshot-cache side effect with
   a domain event, change cascades to restrict, add optimistic locking, and stop
   returning entities from the aggregate response DTO.
6. **Granularity** — introduce `VitalSignsMeasurement`, `RiskFactor`,
   `WardTransfer`, and coded diagnosis, each with a backfill and a
   dual-write/read-new window.
7. **Snapshot contract v2** — add `schema_version` and a versioned cache key,
   teach `Rag_Agentic` to read both shapes, then retire v1.

### Phase 2 rename inventory (measured 2026-10-16)

GitNexus impact analysis, upstream direction, against the rebuilt index:

| Target | Impacted | Risk | Direct | Depth 2 |
| --- | --- | --- | --- | --- |
| `PjiEpisode` | 61 | **CRITICAL** | 32 | 29 |
| `LabResult` | 20 | MEDIUM | 12 | 8 |
| `MedicalHistory` | 15 | MEDIUM | 10 | 5 |
| `CultureResult` | 14 | MEDIUM | 12 | 2 |
| `ClinicalRecord` | 13 | MEDIUM | 11 | 2 |
| `SensitivityResult` | 13 | MEDIUM | 9 | 4 |
| `ImageResult` | 7 | MEDIUM | 5 | 2 |
| `CaseClinicalSnapshot` | 6 | MEDIUM | 5 | 1 |
| `DirectEnum` | 0 | UNKNOWN | 0 | — |
| `ImplantType` | 0 | UNKNOWN | 0 | — |
| `EpisodeResult` | 0 | UNKNOWN | 0 | — |
| `InfectionType` | 0 | UNKNOWN | 0 | — |

`PjiEpisode` is CRITICAL and the count is a lower bound: the walk reports
`AbstractEntity` as an interface with 26 implementations, so dispatch-boundary
callers are not traced. The rating is acceptable for this phase because a rename
is verified by the compiler rather than by behaviour, and the only dependencies
the compiler cannot check were enumerated and confirmed below.

The four `UNKNOWN` rows are **not** evidence of low risk. The graph does not model
type-reference edges, so they were confirmed by text search instead:

- `DirectEnum` — 5 sites: its own declaration, plus the `direct` field in
  `PjiEpisode` and `EpisodeRequestDTO`. Live; rename in phase 2.
- `ImplantType` — 14 sites: `ClinicalRecord.implantStability`,
  `LabFieldFulfillmentDetector`, `ClinicalRecordMapper` (including an
  `ImplantType.class` literal, which the compiler validates), and two tests.
  Live; rename in phase 2.
- `EpisodeResult`, `InfectionType` — one site each, their own declaration.
  Confirmed dead; delete rather than rename.

Class-name occurrences across `src/`, measured **case-sensitively**:

| Class | Sites | Class | Sites |
| --- | --- | --- | --- |
| `PjiEpisode` | 143 | `EpisodeCareTeam` | 23 |
| `CultureResult` | 68 | `CaseClinicalSnapshot` | 20 |
| `EpisodeRepository` | 55 | `EpisodeFullRequestDTO` | 20 |
| `LabResult` | 53 | `EpisodeRequestDTO` | 18 |
| `ClinicalRecord` | 50 | `EpisodeService` | 16 |
| `SensitivityResult` | 44 | `EpisodeGraphLoader` | 15 |
| `ImageResult` | 24 | `ImplantType` | 14 |
| `EpisodeSnapshotAssemblerService` | 23 | `EpisodeFullResponseDTO` | 14 |
| `EpisodeRecordAccess` | 13 | `EpisodeLockService` | 10 |
| `EpisodeAggregateService` | 8 | `EpisodeDepartmentTransfer` | 7 |
| `DirectEnum` | 5 | `EpisodeMapper` | 5 |
| `EpisodeController` | 3 | `EpisodeLockController` | 1 |
| `EpisodeResult` | 1 | `InfectionType` | 1 |

Measurement caution: a case-insensitive count conflates class references with
field and variable references (`ClinicalRecord` with `clinicalRecord`,
`EpisodeRepository` with `episodeRepository`) and inflates the surface. Only the
class-name occurrences move in this phase; every lowerCamelCase field, variable
and JSON key stays.

Why phase 2 is contract-neutral, verified rather than assumed:

- All seven rename targets declare an explicit `@Table(name = ...)`
  (`pji_episodes`, `clinical_records`, `lab_results`, `culture_results`,
  `sensitivity_results`, `image_results`, `case_clinical_snapshots`), so a class
  rename cannot move a table.
- Exactly two class-name dependencies live inside query strings, and both are
  updated by the same word-boundary replacement:
  `AdmissionRepository` `SELECT episode FROM Admission episode WHERE episode.id = :id`
  and `ClinicalSnapshotRepository`
  `SELECT COALESCE(MAX(s.snapshotNo), 0) FROM ClinicalSnapshot s WHERE s.episode.id = :episodeId`.
  A first pass missed the second one, which is why the residual check exists.
  There is no `@NamedQuery`, no `@NamedEntityGraph`, no `#entityName`, and no
  `@JsonTypeInfo`/`@JsonSubTypes` anywhere in `src/`.
- JSON keys come from field names, and phase 2 keeps every field name (`episode`,
  `episodeId`, `labResult`, `labResults`, `imageResults`, `cultureResults`,
  `sensitivityMap`), so the REST payload and the hand-written snapshot keys in
  `EpisodeSnapshotAssemblerServiceImpl` are unchanged.
- Spring Data derived queries are field-based (`findByEpisodeId...`), so they
  survive a type rename.
- No `Class.forName`, no string literal holding a rename-target class name, and
  no `application.yml`/`application.properties` entry referencing one. The only
  case-insensitive hit (`"cultureResult"` in `PjiDiagnosticEvaluationRequestDTOTest`)
  is a JSON payload key, not a class name.
- `@EntityGraph` appears only in `UserRepository`; `getSimpleName()` appears only
  in `RecommendationJobOutboxDispatcher` (an exception name) and in
  `ImageResultServiceTransactionContractTest`, which derives the name from the
  class literal and therefore follows the rename automatically.

## Risks And Recovery

- **Precondition: the working tree is not clean.** `Backend_Server` carried an
  in-flight authorization-hardening change when this plan was written. Phase 2
  must not start until it is committed, because a rename diff mixed with it can
  be reviewed, reverted, and bisected only as one unit.
- **`Frontend_Client` is also dirty** (HEAD `d1693fd`, two modified files:
  `LocalAntibioticTreatment.tsx` and `routes/index.tsx`). Phase 3 changes route
  constants there, so it inherits the same clean-tree precondition. Phase 2 needs
  no change in that repository.
- **`Rag_Agentic` is clean** (HEAD `cad4889`) and needs no change until phase 7,
  which is why phase 2 must keep every snapshot key byte-for-byte identical.
- **Three repositories share the contract.** Phase 2 is safe only because it
  changes no route, no JSON key, and no table name; any phase that changes a
  route or a payload key must ship the consumer change with it.
- **GitNexus index was stale at plan start** (`indexedAt 2026-09-19` against a
  later HEAD plus uncommitted work). Every rename needs impact analysis against a
  rebuilt index.
- **Type rename blast radius is large**: 74 classes and roughly 600 class-name
  references. The rehearsal proves the rename is mechanical, but it must still be
  applied as one reviewed commit and never as an unreviewed find-and-replace.
- **Two JPQL strings carry entity names and the compiler cannot check them**
  (`AdmissionRepository` and `ClinicalSnapshotRepository`). The test suite does
  not catch a stale name either, because JPQL is validated at Hibernate bootstrap
  and `contextLoads` needs a database this environment does not have. The
  residual-identifier check above is therefore mandatory proof, not a nicety.
- **Spring Data derived query names were the weakest link, and that link is now
  closed.** They resolve from strings at context startup, not by the compiler, so
  renaming the entity field `episode` to `admission` renames every
  `findByEpisodeId...` method and a mistake surfaces as a startup failure. The
  database-backed rehearsal below proves the rename survives it: `contextLoads`
  passes with `ddl-auto: validate`, which is only possible when every derived
  query resolves, every JPQL string parses, and every renamed entity still maps
  to its unchanged table. Keep the same proof for the contract phases.
- **The shell is Windows PowerShell 5.1, not PowerShell 7.** `Set-Content
  -Encoding UTF8` would add a BOM to every rewritten Java file. Rewrite with
  `[System.IO.File]::WriteAllText($path, $text, (New-Object
  System.Text.UTF8Encoding($false)))` and verify the BOM count is still zero.
- **The GitNexus `rename` command is not available** in this environment — the
  CLI exposes `impact`, `detect-changes`, `query` and `context`, but no `rename`,
  and the MCP tools are not reachable from this session. This is the documented
  fallback path: whole-word case-sensitive replacement, then compiler and test
  feedback, then a reviewed search confirming zero residual identifiers.
- **Rollback.** Phase 2 is a pure source rename and reverts as one commit.
  Phase 4 needs a reverse migration or the compatibility views retained for at
  least one release.
- **Recovery for cascade restriction.** Once restrict is enforced, deletes can
  fail until the retention procedure exists; the operator path must be documented
  before that phase merges.

## Progress

- [x] Record authority: decision 0010 and the vocabulary table.
- [x] Rebuild the GitNexus index and record upstream impact and risk for every
      rename target, including text-search confirmation of the four `UNKNOWN`
      rows.
- [x] Verify phase 2 is contract-neutral: explicit `@Table` names, two JPQL
      strings, no reflection or literal class names, no configuration coupling.
- [x] Rehearse phase 2 end to end on a scratch copy and reach the recorded
      baseline.
- [x] Inventory the phase 3 route and permission surface, including how the
      permission sync would revoke non-`ADMIN` grants on a bare rename.
- [x] Inventory the phase 4-5 coupling: the eleven cascading keys into the
      episode, the nine classes coupled to the AI snapshot cache, the 40
      remaining `Hibernate.initialize` sites, and the three entities that have
      optimistic locking.
- [x] Confirm a clean working tree in `Backend_Server` (commit `34d9c21`).
- [x] Phase 2: Java-type rename, contract-neutral, with impact analysis.
      Committed as `dec14a0`.
- [x] Rehearse phase 4 (schema rename) end to end on a scratch copy and a
      throwaway database, including catalog verification and a green suite
      against the renamed schema.
- [ ] Clear the two phase 4 gates: external consumers, and the ordering plus
      `pji_dev` version.
- [x] Phase 3a: serve every episode-scoped route on `/admissions` as well, with
      an invariant test. Committed as `e1bf52d`.
- [ ] Phase 3b: the JSON body keys. Blocked on a frontend-first move, because
      key renames cannot be made additive the way routes can.
      Rehearsed 2026-10-16 and proven **not** to be a mechanical rename: four
      collision classes plus a silent `Rag_Agentic` break. Needs a curated
      mapping per DTO, and coordination across all three repositories.
- [ ] Phase 3c: retire `/episodes` once the frontend has migrated, and migrate
      the `permissions` rows for non-`ADMIN` roles in the same change.
- [ ] Phase 4: schema rename with compatibility views.
- [ ] Phase 5: aggregate separation.
- [ ] Phase 6: granularity and backfills.
- [ ] Phase 7: versioned snapshot contract.

## Decisions

- 2026-10-16: One admission is one medical record; no separate `MedicalRecord`
  entity while the relationship is 1:1.
- 2026-10-16: Root type is `Admission`, not `Encounter`, because the schema is
  inpatient-only.
- 2026-10-16: `ClinicalRecord` splits into `ClinicalAssessment` (1:1) plus
  `VitalSignsMeasurement` (1:N) so clinical history stops being a copy.
- 2026-10-16: No tenant column yet; the facility key enters unique keys only when
  multi-hospital work starts.
- 2026-10-16: Immutability of snapshots, runs and signed decisions is enforced in
  the database, because service-only enforcement has already failed here
  (fail-open ownership, cross-aggregate agreement checked only in Java).
- 2026-10-16: Coded diagnosis is deferred to its own entity rather than more
  columns on the root.
- 2026-10-16: Frontend domain types are not renamed in phase 2.
- 2026-10-16: The editor keeps one atomic aggregate save until a later phase,
  because the change is user-visible, not structural.
- 2026-10-16: The repository owner commits the in-flight authorization-hardening
  change themselves; phase 2 waits for `git status` to be clean rather than
  carrying a rename on top of an unreviewed diff.
- 2026-10-16: Phase 2 deliberately keeps every route string, table name, column
  name, JSON key, and snapshot key, and renames only Java types and packages, so
  `Frontend_Client` and `Rag_Agentic` need no change in that phase.
- 2026-10-16: The owner extended the scope beyond the original option list: the
  later contract phase renames JSON body keys as well as routes, not routes only.
  `episodeId` → `admissionId`, `medicalRecordCode` → `recordNumber`,
  `clinicalRecord` → `clinicalAssessment`, `labResult`/`labResults` →
  `labReport`/`labReports`, and the corresponding entity and DTO field names.
  Because those keys come from Java field names, this also renames the entity
  field `episode` on every child, which in turn renames every Spring Data derived
  query method (`findByEpisodeId...` → `findByAdmissionId...`).
- 2026-10-16: Route rename and key rename are still executed as separate commits
  even though both are in scope, so each change moves exactly one axis and can be
  reverted on its own.
- 2026-10-16: The route rename is **additive**, not a switch: 34 mappings across
  15 controllers answer on `/admissions` and `/episodes` at once. Chosen so that
  `Frontend_Client` needs no change and no role loses a grant during the window.
  `AdmissionRouteAliasTest` encodes the invariant against the registered handler
  mappings, so a new episode-scoped route without its alias fails the suite.
- 2026-10-16: **Open tension to resolve before phase 3b.** The owner asked for
  JSON body keys to be renamed (`episodeId` → `admissionId`, `medicalRecordCode`
  → `recordNumber`, and so on) *and* for the route change to be non-breaking.
  Those pull in opposite directions: a route can answer on two paths, but a JSON
  key cannot silently be two keys without duplicating every affected property.
  Phase 3b therefore needs one of: a dual-key window (`@JsonProperty` plus
  `@JsonAlias` on the affected properties), a frontend-first move, or accepting
  a breaking change with a coordinated deploy.

## Validation

- Focused proof: the mapper, repository, service and controller tests that touch
  the renamed types; a new contract test asserting the phase-2 payload and route
  surface is byte-for-byte unchanged.
- Integration or end-to-end proof: the aggregate save/read path against a real
  PostgreSQL instance, plus a `Rag_Agentic` snapshot round-trip proving the
  payload keys are unchanged by the rename.
- Repository-required checks: `./mvnw.cmd test`, GitNexus impact analysis before
  each rename, and `detect-changes` before each commit.
- **Baseline, corrected 2026-10-16.** Without a database `./mvnw.cmd test` ends
  `138 tests / 0 failures / 1 error`, the error being
  `PjiApplicationTests.contextLoads`. With the `pji-postgres` container running
  and a throwaway database, the same command ends
  `138 tests / 0 failures / 0 errors`, `BUILD SUCCESS`, **exit code 0**. The
  "1 error" was purely environmental; `LASTEXITCODE` is the authoritative signal,
  because piping Maven's stderr into PowerShell converts warning lines into error
  records and makes the shell report a non-zero code for a successful build.

### Which services the proof needs

Only PostgreSQL. `pji-postgres` is the single hard dependency: with it up,
`contextLoads` passes while Redis, RabbitMQ and MinIO stay down, and the
application logs their connection failures as non-fatal warnings. That matters
because `contextLoads` is the strongest single check in this programme — with
`spring.jpa.hibernate.ddl-auto: validate` it validates entity-to-schema
agreement, parses every JPQL query, and resolves every Spring Data derived query
method at once.

Reproducing it:

```bash
podman start pji-postgres            # mapped to 127.0.0.1:5433
podman exec pji-postgres psql -U user -d postgres -c "CREATE DATABASE pji_rehearsal"
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5433/pji_rehearsal ./mvnw.cmd -B test
```

`pji_dev` is never touched by this: the throwaway database receives the 36
migrations instead, and `pji_dev` keeps its existing 30 tables.

Live-state correction measured 2026-10-16: `pji_dev` is at Flyway **v35**, not the
v33 the September assessment recorded. So only **V36** (`episode_care_team`) is
still pending there, and that migration is part of the uncommitted work waiting
to be committed. The schema-rename phase must not start before `pji_dev` reaches
the same version as the repository.
- **Rehearsal recorded 2026-10-16.** Phase 2 was executed against a scratch copy
  (`src`, `pom.xml`, `mvnw`, `.mvn` copied out of the repository) so the dirty
  working tree was never touched. Result: 74 classes renamed, 105 files
  rewritten, 74 files renamed, both dead enums deleted, **0 residual old class
  identifiers** under `src`, **0 files gained a BOM**, and `./mvnw.cmd test`
  reproduced the baseline exactly — `Tests run: 138, Failures: 0, Errors: 1`,
  the same `contextLoads` error. Verified unchanged after the rename: every
  `@Table(name = ...)`, every `/episodes...` route string, the
  `medicalRecordCode` field name, and the snapshot payload keys.
- **Rehearsal repeated with a database, 2026-10-16 — the strongest proof
  recorded so far.** The same renamed scratch copy was tested against the
  throwaway database: `MAVEN_EXIT=0`,
  `Tests run: 138, Failures: 0, Errors: 0, Skipped: 0`, `BUILD SUCCESS`. Because
  `contextLoads` passed with `ddl-auto: validate`, this simultaneously proves the
  74 renamed classes still compile, every derived query method resolves, every
  JPQL string parses, and every entity still maps to its unchanged table. Phase 2
  is therefore verified, not merely rehearsed.

### Phase 2 verification commands

Run after the rename, before committing:

```powershell
# 1. no old class identifier may survive anywhere under src (catches the
#    non-compiler-checked JPQL strings)
Select-String -Path (Get-ChildItem -Recurse -Filter *.java src).FullName `
  -CaseSensitive -Pattern '\b(PjiEpisode|CaseClinicalSnapshot|ClinicalRecord|LabResult|CultureResult|SensitivityResult|ImageResult|Episode[A-Za-z]*|DirectEnum|ImplantType|EpisodeResult|InfectionType)\b'

# 2. no file may gain a BOM
Get-ChildItem -Recurse -Filter *.java src | ForEach-Object {
  $b = [System.IO.File]::ReadAllBytes($_.FullName)
  if ($b.Length -ge 3 -and $b[0] -eq 0xEF -and $b[1] -eq 0xBB -and $b[2] -eq 0xBF) { $_.FullName }
}

# 3. behaviour baseline
./mvnw.cmd test
```

### Phase 3 contract surface (measured 2026-10-16)

Where the `/episodes` route literal lives:

| Source | Sites | Nature |
| --- | --- | --- |
| `Backend_Server` controllers | 32 | `@*Mapping("/episodes...")` on the class and methods |
| `Backend_Server` `DatabaseInitializer` | ~21 | hardcoded permission seed, fresh-install path only |
| `Backend_Server` migrations V20, V28, V29 | 12 | permission seed rows for existing databases |
| `Backend_Server` `GlobalExceptionHandler` | 1 | example string inside an error payload, cosmetic |
| `Frontend_Client` `apis/api.ts` | 31 | request paths |
| `Frontend_Client` `constants/permission.ts` | 25 | the `ALL_PERMISSIONS` catalogue |
| `Frontend_Client` `treatment_plan/utils/permissions.ts` | 2 | exact-string gate for the aggregate save |
| `Frontend_Client` `check_completeness/DataCompletenessStep.tsx` | 2 | request path |
| `Frontend_Client` `apis/pendingLabTasks.ts` | 1 | request path |
| `Frontend_Client` `types/backend.d.ts` | 1 | doc comment |

**The permission mechanism decides how this rename must be done.**
`PermissionSyncer` scans the live controller mappings on every startup, inserts
any `(method, path)` that has no row, and auto-grants the new rows to `ADMIN`. It
never deletes a row whose endpoint disappeared. `role_permissions` references
`permissions.id`, not the path.

So a bare route rename would:

- keep `ADMIN` working, because the new rows are auto-granted;
- **silently revoke the renamed endpoints from every other role**, whose curated
  grants still point at the old rows;
- leave dead `/api/v1/episodes/...` rows that the IAM screen still offers, and
  no controller serves.

The phase 3 migration must therefore be an **id-preserving**
`UPDATE permissions SET api_path = replace(api_path, '/episodes', '/admissions')`,
so existing grants follow their permission ids and nothing is revoked, followed
by deleting rows whose endpoint genuinely no longer exists. Deleting and
re-seeding instead would drop every non-`ADMIN` grant.

Note also that `DatabaseInitializer`'s hardcoded permission list is effectively
dead on a modern bootstrap: `PermissionSyncer` is `@Order(1)`, runs first, and
leaves the table non-empty, so the initializer skips. It is updated anyway so a
later ordering change cannot turn it into a trap.

### Phase 4-5 coupling inventory (measured 2026-10-16)

Foreign-key behaviour across all 37 migrations: **27 CASCADE, 10 NO ACTION,
3 SET NULL, 1 RESTRICT**. Thirteen keys reach `pji_episodes`:

| Action | Keys reaching the episode |
| --- | --- |
| CASCADE (11) | `ai_recommendation_runs`, `case_clinical_snapshots`, `clinical_records`, `culture_results`, `doctor_recommendation_reviews`, `image_results`, `lab_results`, `medical_histories`, `surgeries`, `treatment_plan_versions`, `episode_care_team` |
| NO ACTION (2) | `ai_chat_sessions.episode_id`, `pending_lab_tasks.episode_id` |

So the aggregate separation has a bounded target: the eleven cascading keys, of
which `treatment_plan_versions` disappears with the orphan table that already has
no entity, repository, or service. `ai_chat_sessions` and `pending_lab_tasks` are
already non-cascading, which is also why a run cannot be deleted while a chat
session points at it.

The snapshot-cache coupling is what has to move behind a domain event.
`EpisodeGraphLoader` is injected into **nine** classes: the seven clinical child
services (`ClinicalRecordService`, `CultureResultService`, `ImageResultService`,
`LabResultService`, `MedicalHistoryService`, `SensitivityResultService`,
`SurgeryService`) plus `EpisodeAggregateServiceImpl` and `EpisodeServiceImpl`.
That is the concrete size of "clinical writes know about the AI read model".

Two supporting counts:

- **40** `Hibernate.initialize` sites remain (down from 82 per the October
  architecture review). They exist because the aggregate response DTO embeds raw
  entities; they disappear with that DTO, not before.
- **`@Version` exists on exactly three entities**, all decision-support
  (`DoctorFinalDecision`, `PharmacistFinalDecision`,
  `RecommendationFinalSelection`). **No clinical child has optimistic locking**,
  so concurrent writers outside the episode edit lock can still lose an update.
  Optimistic locking on `ClinicalAssessment`, `LabReport`, `SpecimenCulture`,
  `ImagingStudy` and `Surgery` belongs with the aggregate separation.
- Entity-level JPA cascades: exactly one, `DoctorRecommendationReview.doctorFinalDecision`.
  Child cleanup is otherwise entirely a database concern.

### Phase 4 rehearsal (2026-10-16)

Rehearsed on a scratch copy plus a throwaway database, so neither the repository
nor `pji_dev` changed.

Surface, read from the live v36 catalog rather than from recollection: **8
tables** (`pji_episodes` → `admissions`, `case_clinical_snapshots` →
`clinical_snapshots`, `clinical_records` → `clinical_assessments`, `lab_results`
→ `lab_reports`, `culture_results` → `specimen_cultures`, `sensitivity_results` →
`antibiotic_susceptibilities`, `image_results` → `imaging_studies`,
`episode_care_team` → `admission_care_team`), **16 columns** (`episode_id` →
`admission_id` on fourteen tables, `medical_record_code` → `record_number`,
`culture_id` → `specimen_culture_id`), **8 sequences**, **34 indexes** and **19
foreign-key and check constraints**.

The migration is generated from the catalog by a query instead of being written
by hand, so the object list is evidence rather than recollection. It is 85
statements.

Two things the rehearsal proved that planning would have missed:

- **Statement order is load-bearing.** Column, constraint and index statements
  are all addressed by their current table name, so they must run *before* the
  table renames. Generating the tables first fails on the first column statement.
- **Zero old names survive.** After applying, a catalog query for tables,
  columns, constraints, indexes and sequences matching the old vocabulary returns
  **0**, and all eight renamed tables are present.

Proof: retarget the JPA annotations — replacing only double-quoted literals, so
camelCase field names such as `episodeId` stay put, because those are phase 3's
axis — then run the suite against the renamed schema. Result: `MAVEN_EXIT=0`,
`138 tests / 0 failures / 0 errors`, `BUILD SUCCESS`, with `contextLoads` passing
under `ddl-auto: validate` against the renamed tables and columns.

Artifacts are held outside the repository until the gates below are cleared:
`.tmp/V37__rename_medical_record_objects.sql`, `.tmp/phase4-generate.sql`,
`.tmp/phase4-verify.sql`, `.tmp/phase4-annotations.ps1`.

Two gates stand before this is executed for real:

1. **External consumers.** The database assessment asks for confirmation that no
   SQL, BI or integration consumer reads these table and column names before a
   schema rename. That confirmation does not exist yet.
2. **Ordering and live version.** This plan sequences phase 3 before phase 4, and
   `pji_dev` sits at v35 while the repository is at v36, so applying V36 and V37
   there is still pending.

### Phase 3b rehearsal (2026-10-16) — a rule-based rename provably fails

Attempted on a scratch copy: rename the Java identifiers that carry the JSON
contract, about **1170 occurrences** (`episodeId` 527, `episode` 455, `Episode`
64, `sensitivities` 32, `labResult` 29, `cultureId` 21, and the rest).

Four collision classes appeared, and every one of them was found only by
compiling and loading the context, never by reading the rule list:

1. **Schema and wire-key literals.** The bare-word `episode` rule rewrote
   `@Table(name = "pji_episodes")`, `"episode_care_team"`,
   `uq_pending_episode_field` and the AI chat key `"episode_summary"`. All four
   belong to phase 4 or to the `Rag_Agentic` contract. Symptom:
   `Schema-validation: missing table [pji_admissions]`, then
   `[admission_care_team]`.
2. **Capitalised accessors.** Rules written for fields (`clinicalRecord`,
   `labResult`, `cultureId`) missed `getClinicalRecord()`, `getLabResult()`,
   `setCultureId()` and friends: 24 compile errors, all `cannot find symbol`.
3. **Wire values that merely share the English word.**
   `PjiDiagnosticEvaluationRequestDTO.cultureResult` is a category value
   (`negative` / `singlePositive` / `multipleDifferentOrganisms`), not the entity
   vocabulary. Renaming it changes the manual-calculator contract and buys
   nothing.
4. **Field versus derived-query mismatch.** `cultureId` → `specimenCultureId`
   renamed the query method `findByCultureId` but not the entity field `culture`
   it traverses, giving
   `No property 'specimenCultureId' found for type 'AntibioticSusceptibility'`.

Conclusion: phase 3b must be a **curated mapping per DTO**, not a rule set. Rules
cannot distinguish "this word is the entity vocabulary" from "this word is a wire
value, a table name, or a category".

Cross-repository exposure measured in the same rehearsal:

| Contract | Field | `@JsonProperty` | Effect of renaming the field |
| --- | --- | --- | --- |
| `AiRecommendationGenerateRequestDTO` (HTTP to `Rag_Agentic`) | `episodeId` | `episode_id` | Wire key unchanged, so a new Java-versus-wire mismatch appears |
| `AiChatRequestDTO` | `episodeSummary` | `episode_summary` | Would break the chat contract |
| `RabbitMQRecommendationMessage` (queue to the worker) | `episodeId` | none | Wire key becomes `admissionId`; `rabbitmq_worker.py:228` reads `body.get("episodeId")` and would receive **`None` silently** |

So the coordination set for phase 3b is backend **plus frontend plus
`Rag_Agentic`**, and one of the three degrades silently rather than failing
loudly.

Tooling defect found and fixed during the rehearsal: PowerShell hashtables
compare keys case-insensitively. Sharing one mask map across all files collapsed
`"clinical_records"` and `"CLINICAL_RECORDS"` onto a single placeholder and
restored the first-seen casing everywhere, silently rewriting the
manual-calculator adapter's lowercase keys into the initializer's uppercase module
labels; the same defect merged the `Episode` and `episode` rule counters, which is
why their counts looked identical. The script now uses
`System.Collections.Generic.Dictionary[string,string]`.

Status: the rehearsal does **not** reach green. The next step is a curated
mapping, not another rule iteration.

Owner decision, 2026-10-16: before any phase 3b code is written, a per-DTO
mapping table — old key, new key, and the reason each stays or moves — is drafted
for approval, together with the exception list (wire values, table and constraint
names, category values) and the `Rag_Agentic` changes that must ship in the same
change. Coding 3b without that table is what the rehearsal proved unsafe.

### Phase 3b key mapping — draft for approval (2026-10-16)

Derived from the actual field declarations of the 43 DTO and entity files that
carry the old vocabulary, not from recall. Three tiers, because they carry very
different risk.

**Tier 1 — DTO-only fields (pure API surface, nothing else references them)**

| Surface | Old key | New key |
| --- | --- | --- |
| `AdmissionFullResponseDTO` | `episode` | `admission` |
| `AdmissionFullResponseDTO` | `clinicalRecord` | `clinicalAssessment` |
| `AdmissionFullResponseDTO` | `labResults` | `labReports` |
| `AdmissionFullResponseDTO` | `imageResults` | `imagingStudies` |
| `AdmissionFullResponseDTO` | `cultureResults` | `specimenCultures` |
| `AdmissionFullResponseDTO` | `sensitivityMap` | `susceptibilityMap` |
| `AdmissionFullRequestDTO` | `episode` | `admission` |
| `AdmissionFullRequestDTO` | `clinicalRecord` | `clinicalAssessment` |
| `AdmissionFullRequestDTO` | `labResult` | `labReport` |
| `AdmissionFullRequestDTO.CultureItem` | `sensitivities` | `susceptibilities` |
| `AdmissionLockResponseDTO`, `ImagingStudyResponseDTO`, `AiRecommendationRunDTO`, `AntibioticCarePlanResponseDTO`, `ClinicalDecisionWorkspaceDTO`, `AiPredictionResponseDTO` | `episodeId` | `admissionId` |
| `ClinicalAssessmentRequestDTO`, `LabReportRequestDTO`, `SpecimenCultureRequestDTO`, `ImagingStudyRequestDTO`, `SurgeryRequestDTO`, `CreateChatSessionRequestDTO`, `AiAnalysisRequestDTO` | `episodeId` | `admissionId` |
| `AiAnalysisRequestDTO` | `clinicalRecords` | `clinicalAssessments` |
| `AiAnalysisRequestDTO` | `labResults` | `labReports` |
| `AiAnalysisRequestDTO` | `imageResults` | `imagingStudies` |
| `AiAnalysisRequestDTO` | `cultureResults` | `specimenCultures` |
| `AiAnalysisRequestDTO` | `sensitivityResults` | `antibioticSusceptibilities` |
| `SendChatMessageRequestDTO` | `useEpisodeContext` | `useAdmissionContext` |

Unchanged in this tier because they carry no old vocabulary: `medicalHistory`,
`surgeries`, `images`, `cultures`, `patientId`, `snapshotId`, `runId`.

**Tier 2 — entity fields whose names are also wire keys (rename drags JPQL and derived queries)**

| Entity | Old | New | Drags |
| --- | --- | --- | --- |
| `Admission` | `medicalRecordCode` | `recordNumber` | the `BA`-code generator and its test |
| `ClinicalAssessment`, `LabReport`, `ImagingStudy`, `SpecimenCulture`, `Surgery`, `ClinicalSnapshot`, `AiRecommendationRun`, `DoctorRecommendationReview`, `PendingLabTask`, `AiChatSession` | `episode` | `admission` | every `findByEpisodeId…`, `getEpisode()`, `.episode(...)`, JPQL `episode.id` |
| `AntibioticSusceptibility` | `culture` | `specimenCulture` | `findByCultureId` → `findBySpecimenCultureId`; the nested `culture` key in `susceptibilityMap` values |
| `AiChatMessage`, `AiChatSession` | `@JsonIgnoreProperties({… "episode" …})` | `"admission"` | serialization filter lists |

`RecommendationFinalSelection.episode` is `@JsonIgnore`, so it never reaches a
payload; it is renamed for vocabulary consistency only.

**Tier 3 — must NOT change in phase 3b**

| Item | Why it stays |
| --- | --- |
| `@Table`, `@JoinColumn`, `@Column`, `@UniqueConstraint` names (`pji_episodes`, `episode_care_team`, `episode_id`, `uq_pending_episode_field`, `medical_record_code`) | phase 4 owns schema names; changing them here is what broke `contextLoads` in the rehearsal |
| `@JsonProperty("episode_id")` on `AiRecommendationGenerateRequestDTO` and `AiAntibioticCarePlanRequestDTO` | the `Rag_Agentic` HTTP contract; the wire key is fixed by an explicit annotation |
| `@JsonProperty("episode_summary")` on `AiChatRequestDTO` | the `Rag_Agentic` chat contract |
| `metadata.put("episode_id", …)` in `AdmissionSnapshotAssemblerServiceImpl` | the snapshot payload contract read by `Rag_Agentic`; versioning it is phase 7 |
| Route segments `/episodes`, path variables `{episodeId}` and `{cultureId}` | the phase 3a alias and the `permissions.api_path` rows; retiring them is phase 3c |
| `PjiDiagnosticEvaluationRequestDTO.cultureResult` and the `input_type` value `"culture"` | category values, not entity vocabulary; renaming them buys nothing and breaks the manual calculator and the pending-task rendering |
| Uppercase IAM module labels (`"CLINICAL_RECORDS"`, `"LAB_RESULTS"`, …) | permission module names, unrelated to the wire contract |

**Open decisions inside this draft**

Owner approved the whole table on 2026-10-16, including all four recommendations
below. Execution waits only on `Frontend_Client` being clean, which the owner is
doing themselves.

| # | Question | Recommendation |
| --- | --- | --- |
| 1 | Rename `AntibioticSusceptibility.culture` → `specimenCulture`, which also renames the derived query and the nested key? | Yes; the rehearsal only failed because the query moved without the field |
| 2 | Rename `RabbitMQRecommendationMessage.episodeId` → `admissionId`, which requires the one-line change in `rabbitmq_worker.py:228` in the same commit? | Yes; otherwise the wire keeps `episodeId` forever and the worker stays silently coupled to the old name |
| 3 | Rename `AntibioticSusceptibility.sensitivityCode` → `interpretation`? | No for now; it is an AST interpretation code, a separate vocabulary decision for phase 6 |
| 4 | Rename response flag `useEpisodeContext` → `useAdmissionContext`? | Yes; it is a request flag with no other meaning |

**The other two repositories in the same change**

`Frontend_Client` — every renamed key is consumed there, and `episodeId` is not
just a field: it is the identifier behind the episode edit lock, the SSE/AI
stream scope, the deep links (`?patientId&episodeId&tab=…`), the diagnosis
workflow session, and the `sessionStorage` AI-regimen tasks that also persist
`medicalRecordCode` for display. Renaming it is the widest part of this change.
The concrete surfaces are `types/backend.d.ts` (all `IEpisode*`, the six child
interfaces and the four aggregate item types), the server-side property paths in
`features/global-search/globalMedicalSearchUtils.ts` (`medicalRecordCode`,
`patient.patientCode`, `inpatientDiagnosis`, `status`), and the seven places that
fall back to a `BA #{id}` display string.

`Rag_Agentic` — one required change (`rabbitmq_worker.py:228`, `body.get("episodeId")`),
and nothing else: the HTTP keys are pinned by `@JsonProperty`, the snapshot
payload keys are pinned by the assembler's literals, and the worker's own
`session_id` naming is internal.

Sequencing: backend, frontend and worker land as one coordinated change, because
every key in tiers 1 and 2 is a breaking rename on at least one consumer, and
decision 2 is the one that fails silently if the worker is forgotten.

### Phase 3b implementation constraint (found 2026-10-16, after the table was approved)

Applying the approved table as **global tokens** still fails, and the failure is
instructive. Five collision classes are now known, and the fifth is the decisive
one:

1. Schema, constraint and wire-key literals (fixed by masking every quoted
   snake_case literal).
2. Capitalised accessors (fixed by listing both cases).
3. Wire values that merely share the English word (fixed by per-file exclusion
   for `PjiDiagnosticEvaluationRequestDTO` and its adapter and test).
4. Derived query renamed without the field it traverses (fixed by renaming the
   field too, which is decision 1).
5. **A bare English word cannot be a rename token at all.** Global
   `culture` / `Culture` also rewrote `CultureStatus`, the microbiology result
   enum, to `SpecimenCultureStatus` while the file kept its name. That is one
   syntax error and 196 cascading `cannot find symbol` errors, because Lombok
   stops generating accessors for the classes that reference it.

The earlier single-pass redesign removed the ordering and double-application
defects (a `Culture` token matching inside `SpecimenCulture` produced 94
`SpecimenSpecimen…` names until a negative lookbehind was added). What it cannot
remove is over-reach: the same English word is an entity association in one file
and an unrelated domain concept in another.

Therefore phase 3b must be applied **per file, not per token**, and the
`culture` entry in the approved table is the one that needs that treatment. The
places where `culture` means the `AntibioticSusceptibility` → `SpecimenCulture`
association, and therefore the only places it is renamed, are:
`AntibioticSusceptibility`, `AntibioticSusceptibilityRepository`,
`AntibioticSusceptibilityServiceImpl`, `AntibioticSusceptibilityMapper`,
`AdmissionAggregateServiceImpl`, `AdmissionSnapshotAssemblerServiceImpl`, and the
tests that reference `getCulture` on that entity.

Everything else from the table — the DTO keys, `episodeId` → `admissionId`,
`medicalRecordCode` → `recordNumber`, and the child-entity `episode` → `admission`
fields — is unaffected by this constraint and can still be tokenised.

### Phase 3b result (2026-10-16): rehearsed to green

The approved table, implemented as a single-pass rename with the protections
below, reaches `MAVEN_EXIT=0`, **140 tests / 0 failures / 0 errors / BUILD
SUCCESS** against PostgreSQL 16 with all 36 migrations applied, including
`contextLoads` under `ddl-auto: validate`. 162 files rewritten on the scratch
copy. Nothing has been applied to the repository: the change is coordinated with
`Frontend_Client` and `Rag_Agentic`, and the frontend tree is still dirty.

What "curated" turned out to mean in practice — nine protections, each one
discovered by a failing rehearsal rather than by inspection:

1. `/episodes` — the phase 3a alias path segment.
2. `{episodeId}`, `{cultureId}` — route template path variables, whose rename
   would desynchronise `permissions.api_path` and silently revoke grants.
3. Every double-quoted snake_case literal — schema object names, constraint
   names and the `Rag_Agentic` wire keys.
4. `CultureStatus` — a different concept, and the cause of one syntax error plus
   196 cascading failures.
5. `CultureEvidence`, `cultureEvidenceEvaluator`, `totalCultureCount` — the
   rule-engine evidence concept, outside the approved list.
6. Negative lookbehind on `Culture`/`culture` so they do not match inside
   `SpecimenCulture`/`specimenCulture` (94 `SpecimenSpecimen…` names otherwise).
7. The whole `PjiDiagnostic*` family excluded from the `cultureResult` tokens,
   because there `cultureResult` is a category value, not the entity vocabulary.
8. A single-pass replacement, which removed the ordering and
   double-application defects outright.
9. `"culture"` deliberately **not** protected: a full inventory showed the only
   two occurrences are MapStruct targets that must follow the renamed field,
   and the `input_type` value never appears as a Java literal.

Honest limit of that green run: passing 140 tests proves the change compiles,
wires up, and still maps to the unchanged schema. It does **not** prove any
consumer still works — every tier 1 and tier 2 key is renamed on purpose, so
`Frontend_Client` and `Rag_Agentic` are expected to break until they land in the
same change.

### Phase 3b frontend cost (measured 2026-10-16)

Method: copy `Frontend_Client/src` to a scratch, junction `node_modules` to avoid
copying it, rename **only** the contract fields in `types/backend.d.ts`, then run
`tsc --noEmit`. The result is the compiler-derived size of the required change.

- **The frontend has no working typecheck.** `tsconfig.json` sets
  `ignoreDeprecations: "6.0"`, which TypeScript 5.8 rejects with `TS5103`, so
  `tsc` does not start at all. Correcting that value to `"5.0"` is a prerequisite
  for the frontend change to be verifiable, and it belongs in the same change.
- Baseline with the corrected config: **9 pre-existing errors** in 4 files —
  `NotificationBell` (4), `PatientModal` (3), `AntibiogramAiVersionTab` (1, where
  `carePlanJson` is missing from the pharmacist decision type — the same gap the
  earlier audit found), and one more.
- After renaming the contract fields: **34 errors, so 25 new, across 15 files**.
  They concentrate in `MedicalExamDetail.tsx` (10), `doctorDiagnosisModel.ts` (2),
  `useTreatmentPlanData.ts` (2) and `LayoutClient.tsx` (1).
- Those 25 are a **lower bound**. Any value that reaches a consumer through an
  `any`-typed path still compiles and breaks at runtime instead. The raw token
  count in the frontend is over 560 across 35 files, most of it internal naming
  the type system never connects to the contract.


## Result

Phase 0 and phase 2 are complete; the goal stays active.

**Phase 0** — decision 0010 is accepted and indexed, this plan is the durable
record, and the vocabulary, aggregate boundaries and open questions are all
written down before any code moved.

**Phase 2** — executed and committed as `dec14a0` (rename) after `34d9c21`
(the in-flight authorization and review work the tree was carrying). 74 classes
renamed, 105 files rewritten, 74 files renamed, 2 dead enums deleted. Verified
in this order: pre-flight clean tree, 0 residual old class identifiers under
`src`, 0 files carrying a BOM, `./mvnw.cmd test` on the live tree against
PostgreSQL 16 with all 36 migrations applied returning
`138 tests / 0 failures / 0 errors / BUILD SUCCESS / exit 0`, with
`PjiApplicationTests.contextLoads` passing under `ddl-auto: validate`, and
`gitnexus detect-changes --scope all` reporting neither `partial` nor
`truncated`.

Phase 2 was deliberately contract-neutral. Everything that still says "Episode"
where a user can see it — the `@Tag`, `@ApiResponse` and response-message
wording, the `/episodes` routes, and the JSON body keys — is phase 3 work, and
the residual-identifier check confirms none of it moved.

Known limits of this result:

- `detect-changes` rates the rename **critical** (31 files, 120 symbols, 104
  affected flows). That rating reflects breadth, not observed breakage: the
  rename is compiler- and context-verified, and the graph counts type changes on
  properties whose names did not move.
- The GitNexus process catalogue is truncated (312 of 512 candidate entry points
  never ranked, 257 flows dropped at `maxProcesses`), so an absent flow in the
  graph is not evidence the path does not exist. `detect-changes` itself reported
  no `partial` or `truncated` flag.
- Phase 3 onward remains unstarted, and the schema rename additionally waits for
  `pji_dev` to reach the repository's Flyway version.
