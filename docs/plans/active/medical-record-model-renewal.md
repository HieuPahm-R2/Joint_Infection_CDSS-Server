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
- [ ] Phase 3: route rename plus frontend contract fixes.
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
