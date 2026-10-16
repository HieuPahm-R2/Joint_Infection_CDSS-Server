# Backend Architecture Review — SOLID, Design Patterns, Clean Code

Date: 2026-10-14

## Status

Remediated for the findings marked **Fixed**. Findings marked **Needs owner
decision** are documented, not changed.

## What This Document Is

An evidence-based review of `Backend_Server` against SOLID, the implemented
design patterns, and clean-code expectations, plus the refactorings applied in
the same change. Every claim below is anchored to a file and, where it matters,
a test.

Read-only policy authority for anything authorization-related stays with
[Decision 0008](../decisions/0008-general-authorization-policy.md) and the
[authorization hardening plan](../plans/active/authorization-hardening.md).

## 1. SOLID Assessment

### 1.1 Single Responsibility — the main weakness found

| Finding | Evidence | Disposition |
| --- | --- | --- |
| `PendingLabTaskServiceImpl` was one 600-line class with 8 injected repositories owning five unrelated responsibilities: field identity/alias knowledge, lab-row JSON shape, completeness-payload parsing, lab auto-fulfilment matching, and clinical auto-fulfilment matching | old `PendingLabTaskServiceImpl.java` lines 46-153 (catalog), 220-257 (`switch` over sections), 384-401 and 442-495 (matching) | **Fixed** — split into `LabFieldCatalog`, `LabFieldFulfillmentDetector`, `PendingLabTaskLabWriter`; the service is now 280 lines and orchestrates only |
| `AiRecommendationServiceImpl` owned run creation, AI transport, result persistence, run-detail DTO assembly and caching. Its result write was a `@Transactional protected` method called through `this.`, so the annotation never applied and a run, its items and its citations were written non-atomically | old `AiRecommendationServiceImpl.java` 101 + 152-253 | **Fixed** — extracted `RecommendationResultWriter` (243 lines). The write now crosses a bean boundary, so the transaction is real |
| Episode/patient lazy loading and snapshot-cache invalidation were open-coded into every episode-scoped writer | 24 `Hibernate.initialize` calls and 23 `redisService.evictSnapshotCache` calls across 9 services | **Fixed** — `EpisodeGraphLoader` is the single owner |
| Pagination envelope arithmetic copy-pasted into 14 service methods | 14 identical `Meta`/`result` blocks | **Fixed** — `PaginationResultDTO.of(Page)` / `of(Page, List)` |
| `AuthController` (502 lines) is a service in a controller: it injects `UserTrustedDeviceRepository`, performs the trusted-device upsert, mints tokens, writes Redis session/refresh state and persists the user | `AuthController.java:78`, `:105-108`, `:137-145`, `:175-185`, `:429-438` | **Needs owner decision** — extracting an `AuthenticationService` moves an externally observable login/refresh contract; see §5.1 |
| `RedisService` is a god interface spanning 8 unrelated concerns (refresh tokens, active sessions, access-token blacklist, permission cache, snapshot cache, run-detail cache, run-cancellation flag) | `services/feat/RedisService.java:11-115` | **Needs owner decision** — ISP split changes 15 injection sites; see §5.2 |

### 1.2 Open/Closed — partial

Extension is genuinely open where it matters: MapStruct mappers, the diagnosis
pipeline collaborators and the outbox dispatcher can gain behaviour by adding a
collaborator rather than editing a `switch`. It is closed where scope changes:
`switch (scope)` over `RecommendationScope` appears at
`ClinicalDecisionServiceImpl` 289, 293, 407 and the same scope↔category
validation is written twice — `AiRecommendationServiceImpl` (item validation)
and `RabbitMQConsumer` 134-147. An enum-owned
`supportsDoctorDecision`-style member already exists, so the remaining
`switch`es are candidates for the same treatment. **Needs owner decision**
(clinical workflow policy).

### 1.3 Liskov Substitution — clean

No inheritance hierarchy in the codebase violates substitutability. There are
zero `abstract class` orchestration bases and one `AbstractEntity` mapped
superclass; the only `extends` relationships are Spring Data repository
interfaces, exception types, and DTO payload subtypes
(`LockedErrorResponse extends ErrorResponse`). No finding.

### 1.4 Interface Segregation — one real violation

Dependency injection is constructor-based (70 `@RequiredArgsConstructor`
classes; 4 stray field `@Autowired`). Generic repository interfaces are used
correctly. The counter-example is `RedisService` (§1.1) and, secondarily,
`UserService`, which publishes CRUD, the user-lookup chokepoint, refresh-token
persistence, login stamping and self-service profile/password on one interface
(`services/auth/UserService.java:24-45`).

### 1.5 Dependency Inversion — one real violation

Service-layer code depends on repository interfaces (good), and the diagnosis
pipeline depends on four injected collaborators behind a facade (good). The
violations are:

- `AiRecommendationController` injects another **controller**
  (`AiRecommendationStreamController`) instead of an abstraction;
  `NotificationServiceImpl` and `RabbitMQConsumer` inject a controller for SSE
  fan-out. **Needs owner decision** — extracting an `SseEmitterRegistry` service
  changes the streaming endpoint wiring; see §5.3.
- `SecurityUtils` is a `@Service` that mixes static, thread-local reads
  (`getCurrentUserLogin`, `getCurrentUserId`) with instance JWT minting and
  `@Value` configuration. Consumers cannot substitute the principal source in a
  unit test, which is why every service re-implements "resolve the caller".

## 2. Implemented Design Patterns — Inventory

Verified by reading the implementation, not by name.

| Pattern | Where | Genuine? |
| --- | --- | --- |
| Dependency Injection / IoC | 89 `@Service`/`@Component`/`@Configuration` beans, constructor injection dominant | Yes |
| Builder | Lombok `@Builder` on ~40 DTOs/entities; `RecommendationRunCreator.CreatedRecommendationRun` | Yes |
| Mapper / DTO separation | `utils/mapper/*` — MapStruct 1.6.3, generic `EntityMapper<D,E>`, 13 generated `*MapperImpl` | Yes |
| Repository | 27 Spring Data JPA interfaces + one hand-written `RedisUploadSessionRepository` (Lua scripts, TTL policy) | Yes |
| Facade | `PjiDiagnosticRuleEngine` orchestrates four collaborators and documents doing so | Yes |
| Adapter | `PjiDiagnosticRequestAdapter`, `ExtractImagesClient`, `AiServiceClient` | Yes |
| Observer / Publish-Subscribe | RabbitMQ topics + DLQ (`RabbitMQConfig`), SSE fan-out in the stream controllers | Yes |
| Transactional Outbox | `RecommendationRunCreator` writes the job row in the same transaction as run+snapshot; `RecommendationJobOutboxDispatcher` polls with `FOR UPDATE SKIP LOCKED`, exponential backoff, publisher confirms | Yes (partial — see §5.4) |
| Cache-Aside | `EpisodeSnapshotAssemblerServiceImpl`, `RedisServiceImpl`, `AiRecommendationServiceImpl` get→miss→load→put with TTL + explicit eviction | Yes (hand-rolled) |
| Proxy (framework AOP) | 97 `@Transactional` sites | Yes, framework only — no custom `@Aspect` |
| Chain (interceptor/filter) | `AuthorityIntercepter` + `InterceptorConfiguration`; `ActiveSessionFilter`, `RateLimitFilter` as `OncePerRequestFilter` | Yes (single-link chain) |
| Rate limiter (token bucket) | bucket4j `Bandwidth.capacity().refillGreedy()` over a Redis-backed proxy manager | Yes |
| State (enum-driven) | `RecommendationScope`, `UploadSessionStatus`, `UserStatus`, `RunStatus` | Partial — no State classes; transitions live in services |
| **Strategy** | — | **Absent.** Behaviour selection is `switch`/`if` on enums |
| **Template Method** | — | **Absent.** No overridable orchestration base |
| **Factory (GoF)** | — | **Absent.** Only Spring `@Bean` factory methods and zero `@ConditionalOn*` |
| **Circuit Breaker** | — | **Absent.** Resilience is a `RestTemplate` read timeout plus outbox backoff |

The absent patterns are not automatically defects. Strategy is the one that
would remove real duplication today (§1.2).

## 3. Clean Code — Findings And Dispositions

### 3.1 Fixed

| # | Finding | Evidence | Proof |
| --- | --- | --- | --- |
| C1 | A 403 was answered with **HTTP 200** and a 403 body, because `handleForbiddenException` had no `@ResponseStatus` while the advice's `@ExceptionHandler(Exception.class)` never applied to it | `GlobalExceptionHandler.handleForbiddenException` | `GlobalExceptionHandlerTest.forbiddenExceptionIsStatedAsForbiddenAndAnnotatedSoTheHttpStatusMatches` |
| C2 | `@Valid @RequestBody` failures produced **500**, because the class imported `org.springframework.messaging.handler.annotation.support.MethodArgumentNotValidException` — a different hierarchy from the web `MethodArgumentNotValidException`, so the 400 handler could never match and its `instanceof` branch was dead code | `GlobalExceptionHandler` import + 66-105 | focused handler test suite green |
| C3 | `UploadSessionGoneException` (410) and `UploadSessionUnauthorizedException` (401) were downgraded to **500** for the whole QR-upload flow; their class-level `@ResponseStatus` is outranked by the advice-local catch-all | `GlobalExceptionHandler.handleUploadSessionFailure` | `GlobalExceptionHandlerTest.uploadSessionFailuresKeepTheir410And401Statuses`, `...WouldDowngradeTo500` |
| C4 | The generic 500 handler echoed `e.getMessage()` to clients, leaking SQL text, JDBC internals, parser context and credentials. `DataIntegrityViolationException` already suppressed its message, so the pattern existed and was applied once | `GlobalExceptionHandler.handleException` | `GlobalExceptionHandlerTest.unmappedThrowableDoesNotLeakItsInternalMessage` |
| C5 | `BusinessException(String, String, Exception)` had an **empty body**, silently discarding the message and cause of every MinIO upload/download failure | `BusinessException` | compiles; `MinioChannel` call sites now carry a message and cause |
| C6 | `PaginationResultDTO.Meta` was built by hand in 14 places; the 1-based page convention could drift per endpoint | `PaginationResultDTO.of` | `PaginationResultDTOTest` (3 tests); grep for `new PaginationResultDTO` now returns the factory itself only |
| C7 | The AI result write ran with **no transaction** (self-invocation of a `@Transactional` method), so a mid-loop failure could leave a `SUCCESS`/`PARTIAL` run with partial items | `RecommendationResultWriter.save` | `AiRecommendationServiceTransactionContractTest.aiResultWriteIsTransactionalOnItsOwnBean` |
| C8 | `getOrCreateLatestLabResult` re-implemented "latest lab result" with a hand-built `PageRequest`, one of three ways to ask the same question | `PendingLabTaskLabWriter` now uses `findFirstByEpisodeIdOrderByCreatedAtDesc` | `LabFieldFulfillmentDetectorTest` |
| C9 | `specFor`/`isFieldPresent` would throw `NullPointerException` on a completeness item with no `field`, because the mapping is an immutable `Map.ofEntries` whose lookup rejects a null key | `LabFieldCatalog.declaredSpecFor`, `fallbackSpec` | `LabFieldFulfillmentDetectorTest.unmappedFieldsFallBackToASelfDescribingHematologyRow` |
| C10 | `AiRecommendationServiceImpl` rebuilt the run-detail DTO in three hand-written mapper methods although `AiRecommendationRunMapper` existed | `RecommendationResultWriter.toRunDetailDto` | compile + existing tests |
| C11 | `ImageResultServiceImpl.create`/`update` persisted the row and evicted the episode snapshot cache with **no transaction**, unlike every sibling episode-scoped writer, which only annotated `delete` | `ImageResultServiceImpl` | `ImageResultServiceTransactionContractTest` |

Net effect on the metrics this review tracked:

| Metric | Before | After |
| --- | --- | --- |
| Hand-built `PaginationResultDTO` blocks | 14 | 1 (the factory) |
| `Hibernate.initialize` call sites | 82 | 42 |
| Largest service class | 600 lines, 8 repositories | 280 lines, 8 collaborators with distinct roles |
| AI result write transaction | none (advice skipped) | one transaction on its own bean |

### 3.2 Needs owner decision (documented, not changed)

| Finding | Why it is not a safe unilateral fix |
| --- | --- |
| **`RecommendationAccessService.assertCanGenerateEpisode` fails open for pharmacists.** The `PHARMACIST` branch returns before `validateOwnerOrAdmin`, so any pharmacist holding the route permission can generate an antibiotic recommendation on any patient's episode. Admin is checked *more* strictly than pharmacist — the branches are inverted | Preserving or removing the pharmacist exemption is a clinical-workflow policy choice. Decision 0008 requires care-team membership, but Decision 0006 defines the pharmacist lane; the two must be reconciled by the owner |
| **Blank owner metadata grants access to non-admins.** `hasOwnerMetadata && !sameUser(...)` with no `else`, so an episode whose creator metadata is blank is readable by everyone. `AbstractEntity` writes the literal `" "` when no principal is present, which is exactly that condition | The plan states such records "remain admin-only"; flipping the branch changes who can read existing records. Needs an explicit migration decision |
| **`AuthorityIntercepter` fails open for a `null` permission set** and caches the `NO_ROLE` sentinel for 600 s, so an account whose role was deactivated keeps invoking its routes for up to 10 minutes | This is the core of the active-principal work in the active plan; changing it is that plan's job, with its own tests |
| **User status and role-active are checked at only 2 of 9 principal-resolution sites** | Same workstream as above |
| Entities returned directly from 13 CRUD controllers, and `Map<String,Object>` as the default exchange type (218 occurrences) | A typed-contract change alters the documented REST payloads; needs a versioning/contract decision |
| `EpisodeFullResponseDTO` embeds raw JPA entities, which is what forces the remaining 42 `Hibernate.initialize` calls. The real fix is `@EntityGraph`/fetch joins or projections, plus `ApplicationEvent`-free ordinary DTOs | Changes the response shape and query plan; needs a contract + performance decision |
| `@Transactional` on the per-request `AuthorityIntercepter` pins a Hikari connection across Redis I/O | Removing it changes connection-pool behaviour under load; needs a load-tested decision |
| `RedisTemplate` injected directly alongside `RedisService` in 5 classes; SSE registry duplicated across 3 controllers; `AuthController` orchestrating tokens and sessions | Structural extractions that change wiring and the externally observable auth/streaming surface |

## 4. Validation

- Focused proof: `LabFieldFulfillmentDetectorTest` (9), `PaginationResultDTOTest`
  (3), `GlobalExceptionHandlerTest` (9), `AiRecommendationServiceTransactionContractTest`
  (2), `AiRecommendationServiceImplTest` (2), `RecommendationRunCreatorTest` (2),
  `ClinicalDecisionServiceImplTest` (2), `ImageResultServiceTransactionContractTest`
  (1), `UserDetailsConfigTest` (7), `CurrentActorProviderTest` (8),
  `EpisodeRecordAccessTest` (10), `RecommendationAccessServiceTest` (9).
- Migration proof (2026-10-15): all 36 migrations applied to an empty PostgreSQL
  16.2 database created for the check. `episode_care_team` verified for columns,
  both unique indexes and both foreign keys; the V36 owner backfill was replayed
  against fixtures and enrolled exactly the resolvable creators, matched a padded
  mixed-case email, left `' '` / `''` / unresolvable creators admin-only, and was
  idempotent. Both unique indexes were confirmed by an insert that raised
  `duplicate key value violates unique constraint`.
- Repository-required check: `./mvnw.cmd test` → **138 tests, 0 failures,
  0 errors** (baseline before this work: 98 tests, 0 failures, 1 error).
- Known environmental error: `PjiApplicationTests.contextLoads` fails because
  no PostgreSQL is listening on `localhost:5433`
  (`Connection to localhost:5433 refused`). It failed identically before this
  change and is unrelated to it.
- GitNexus `detect-changes --scope all` could not run in this environment
  (`No indexed repositories found` with the workspace-local registry). Per
  `AGENTS.md`, the fallback was used: compiler feedback, `git diff --stat`, and
  a repository-wide grep proving the duplicated constructs are gone.

## 5. Recommended Next Steps

1. **Broaden record enforcement** to the episode-scoped clinical CRUD endpoints
   once care-team data exists across environments, per Decision 0009. This is the
   largest remaining authorization gap.
2. **Finish the identity/JWT and route-permission items** still open in the
   `authorization-hardening` plan.
3. **Split `RedisService`** by consumer capability (session/refresh, permission
   cache, snapshot cache, run cache, cancellation flag).
4. **Extract `SseEmitterRegistry`** out of the three stream controllers. This
   also removes the controller→controller dependency.
5. **Replace the 42 `Hibernate.initialize` calls** with fetch graphs or
   projections, and retype the `Map<String,Object>` contracts.
6. **Surface care-team assignment in the frontend** so membership can be granted
   without calling the API by hand.

## 6. Limits

- The review is source-based. GitNexus impact analysis was unavailable, so
  caller evidence comes from repository-wide search plus compiler/test feedback.
- Behavior preservation is proven by the existing suite, not by end-to-end
  interaction against a running database, which this environment cannot reach.
- Refactoring was deliberately restricted to behavior-preserving changes.
  Findings that alter externally observable contracts or security policy are
  listed in §3.2 rather than changed.
