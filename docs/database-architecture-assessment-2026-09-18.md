# Báo cáo đánh giá kiến trúc cơ sở dữ liệu

Ngày đánh giá: 18/09/2026  
Cơ sở dữ liệu: `pji_dev`  
Hệ quản trị: PostgreSQL 16.2  
Chế độ đánh giá: chỉ đọc

## 1. Kết luận điều hành

Cơ sở dữ liệu hiện tại mô hình hóa khá tốt luồng nghiệp vụ cốt lõi:

`Bệnh nhân → Đợt điều trị → Dữ liệu lâm sàng/Bản chụp → Lần chạy khuyến nghị → Quyết định bác sĩ/dược sĩ → Lựa chọn cuối cùng`

Thiết kế phù hợp với quy mô phát triển hiện tại và mô hình một cơ sở y tế. Tuy nhiên, hệ thống chưa đạt mức hoàn thiện để có thể xem là cơ sở dữ liệu production đã được gia cố đầy đủ cho dữ liệu y tế, lịch sử lớn hoặc triển khai nhiều bệnh viện.

| Hạng mục | Đánh giá |
| --- | --- |
| Đáp ứng nghiệp vụ | Khá tốt, nhưng live DB đang chậm repository một migration |
| Toàn vẹn dữ liệu | Trung bình; FK riêng lẻ đầy đủ nhưng thiếu ràng buộc chéo giữa các aggregate |
| Hiệu năng hiện tại | Đủ dùng với lượng dữ liệu đang rất nhỏ |
| Khả năng mở rộng dọc | Khá, sau khi điều chỉnh query và index |
| Mở rộng nhiều bệnh viện | Chưa sẵn sàng |
| Dư thừa schema | Có một bảng orphan rõ cùng nhiều enum, index và cột legacy |
| Audit và retention y tế | Chưa được định nghĩa và cưỡng chế đủ chặt |

Rủi ro chính hiện không nằm ở kích thước database hay sequence. Các vấn đề quan trọng hơn là schema drift, thiếu invariant chéo, business key chưa được unique, chính sách xóa/lưu trữ chưa rõ và thiếu số liệu workload production.

## 2. Phạm vi và phương pháp

Đánh giá được thực hiện bằng:

- Truy vấn catalog trực tiếp trên `pji_dev` qua MCP `postgres-local`, hoàn toàn chỉ đọc.
- Đối chiếu lịch sử Flyway và migration.
- Kiểm tra entity JPA, repository query và các service liên quan.
- Đối chiếu các ADR đã được chấp nhận, đặc biệt ADR 0003–0007.
- Kiểm tra constraint, index, số dòng, kích thước và trạng thái sức khỏe PostgreSQL.

Không có dữ liệu, cấu trúc database hoặc mã nguồn nghiệp vụ nào bị thay đổi. File báo cáo này là thay đổi duy nhất.

## 3. Hiện trạng live database

- Một schema ứng dụng: `public`.
- 30 bảng thường; không có bảng partition và không có view.
- 26 sequence, đều thuộc đúng cột ID.
- 30/30 bảng có primary key.
- 45/45 foreign key đã được validate.
- 12 unique constraint và 8 check constraint.
- Flyway có 33 migration thành công, mới nhất là V33.
- Tổng kích thước database khoảng 10,22 MB.
- Tổng heap và index ứng dụng khoảng 1,46 MB.
- Phần lớn bảng nghiệp vụ có từ 0 đến 1 dòng.
- `permissions=120`, `role_permissions=125`, `roles=2`, `users=2`.

Không phát hiện orphan row, duplicate business key hoặc sai lệch chéo trong dữ liệu hiện có. Tuy nhiên, do phần lớn bảng đang rỗng, đây chưa phải bằng chứng cho tải production.

## 4. Những điểm thiết kế tốt

- Mô hình patient, episode, clinical record, lab, culture, sensitivity, image và surgery có quan hệ sở hữu khá rõ.
- `culture_results → sensitivity_results` là cách tách hợp lý cho dữ liệu kháng sinh đồ.
- Snapshot unique theo `(episode_id, snapshot_no)`.
- Recommendation run unique theo `(episode_id, run_no)`.
- Mỗi run có tối đa một `rule_based_diagnostic_results`.
- Doctor và pharmacist decision được tách lane, có status check và optimistic version.
- Final selection unique theo `(episode_id, recommendation_scope)`.
- V33 tạo durable outbox có retry metadata, index `(available_at, id)` và cơ chế `SKIP LOCKED` phù hợp nhiều dispatcher.

Thiết kế versioning và decision bám khá sát [ADR 0003](decisions/0003-run-scoped-rule-diagnostics.md), [ADR 0005](decisions/0005-run-scoped-clinical-decisions.md) và [ADR 0006](decisions/0006-role-scoped-treatment-recommendations.md).

## 5. Phát hiện ưu tiên cao

### 5.1. Live DB chưa đồng bộ business contract mới nhất

Live database mới ở V33, trong khi repository đã có [V34](../src/main/resources/db/migration/V34__remove_legacy_combined_recommendation_scope.sql). [ADR 0007](decisions/0007-retire-legacy-combined-recommendation-scope.md) đã loại bỏ `LEGACY_COMBINED` và chỉ cho phép `SURGERY`, `ANTIBIOTIC`.

Catalog live vẫn cho phép và mặc định `LEGACY_COMBINED`. Vì vậy database đang chạy chưa đáp ứng đầy đủ contract mới nhất. Cần áp dụng V34 qua quy trình Flyway chuẩn, không sửa catalog thủ công.

### 5.2. Thiếu ràng buộc nhất quán chéo giữa các aggregate

DB chưa bảo đảm rằng:

- Run và snapshot thuộc cùng episode.
- Doctor review và run thuộc cùng episode.
- Doctor decision dùng đúng run của review.
- Final selection có run cùng episode và cùng scope.
- Chat session, run và current item cùng một ngữ cảnh.
- Patient của pending lab task sở hữu episode đó.
- Fulfilled lab result thuộc đúng episode của task.

Các FK riêng lẻ vẫn có thể hợp lệ trong khi bản ghi sai nghiệp vụ. Nên loại ID dẫn xuất trùng lặp hoặc thêm composite unique/FK như `(run_id, episode_id)`, `(run_id, recommendation_scope)`.

### 5.3. Business key quan trọng chưa được unique ở DB

DB hiện cho phép trùng:

- `permissions(api_path, method)`.
- `patients(identity_card)`; mới chỉ có index thường.
- `ai_recommendation_runs(request_id)`; chưa có index.
- Email chỉ khác chữ hoa/chữ thường.

Kiểm tra `exists...` ở application không thay thế được unique constraint do race condition. Migration cần chạy preflight chuẩn hóa và kiểm tra duplicate trước khi thêm constraint.

### 5.4. Quan hệ nghiệp vụ lưu dưới dạng ID tự do

`pending_lab_tasks.assigned_to_user_id` và `created_from_run_id` chưa có foreign key. Cột assignment còn nằm trên access path thường xuyên nhưng thiếu index phù hợp. Điều này cho phép tham chiếu không hợp lệ và làm truy vấn task chậm dần.

### 5.5. Tính bất biến chỉ được bảo vệ ở service

ADR coi snapshot, run, diagnosis và signed decision là lịch sử bất biến, nhưng DB vẫn cho phép update/delete trực tiếp. `version_no` chỉ hỗ trợ optimistic locking, không ngăn SQL trực tiếp, không tạo amendment history và không cung cấp audit trail trước/sau đầy đủ.

Cần quyết định rõ invariant này chỉ do service bảo vệ hay phải bổ sung database role hạn chế quyền, history table hoặc trigger được thiết kế chặt.

### 5.6. Retention và cascade delete chưa có authority rõ

Patient dùng soft delete ở application, nhưng FK patient → episode dùng `ON DELETE CASCADE`; episode tiếp tục cascade xuống nhiều dữ liệu clinical và AI. Xóa vật lý patient có thể xóa cả lịch sử chẩn đoán và quyết định.

Trước khi đổi cascade cần chốt:

- Thời gian lưu hồ sơ.
- Xóa, ẩn danh hay lưu trữ lạnh.
- Quy trình amendment cho quyết định đã ký.
- Quyền thực hiện thao tác phá hủy.
- Yêu cầu phục hồi và legal hold.

### 5.7. Audit identity chưa nhất quán

Phần lớn `created_by` và `updated_by` là chuỗi nullable; các bảng decision mới dùng user ID có FK. Lớp dùng chung còn ghi một chuỗi dấu cách khi không có principal. Cách này không tạo định danh audit chặt hoặc lịch sử thay đổi đầy đủ.

Xem [AbstractEntity.java](../src/main/java/com/vietnam/pji/model/AbstractEntity.java).

### 5.8. Chưa hỗ trợ cô lập nhiều bệnh viện

Không bảng nghiệp vụ nào có `tenant_id`, `organization_id` hoặc `hospital_id`; toàn bộ 30 bảng chưa bật RLS. Nếu nhiều bệnh viện dùng chung database, cần tenant model, tenant-scoped key và cơ chế cô lập ở DB. Role application đơn thuần là chưa đủ.

## 6. Hiệu năng và khả năng mở rộng

### 6.1. Composite index chưa khớp query

| Bảng | Access path cần hỗ trợ |
| --- | --- |
| `ai_recommendation_runs` | `(episode_id, created_at DESC)` |
| `ai_chat_sessions` | `(episode_id, created_at DESC)` |
| `doctor_recommendation_reviews` | `(episode_id, created_at DESC)` |
| `pending_lab_tasks` | `(assigned_to_user_id, status, created_at DESC)` |
| `pending_lab_tasks` | `(episode_id, status)` |
| `ai_recommendation_items` | `(run_id, priority_order)` |

Index `(episode_id, recommendation_scope, created_at DESC)` không cung cấp thứ tự theo thời gian hiệu quả khi query không lọc scope.

### 6.2. Có 11 FK chưa có leading index

Các FK thiếu leading index gồm `ai_chat_sessions.current_item_id`, `ai_rag_citations.item_id`, hai cột user của `image_results`, hai FK phụ của `pending_lab_tasks`, `role_permissions.permission_id`, ba FK phụ của `treatment_plan_versions` và `users.role_id`.

Không nên thêm toàn bộ một cách máy móc. Ưu tiên theo workload: `users.role_id`, `role_permissions.permission_id`, `ai_rag_citations.item_id`, `ai_chat_sessions.current_item_id`.

### 6.3. Index bị bao phủ hoặc có khả năng dư

Health audit xác định năm index bị B-tree khác bao phủ:

- `idx_ai_runs_episode_id`
- `idx_snapshots_episode_id`
- `idx_reviews_run_id`
- `idx_rule_diagnostic_run_id`
- `idx_plan_versions_episode_id`

`idx_rule_diagnostic_run_id` dư rõ nhất vì `run_id` đã có unique index. Các index còn lại chỉ nên drop sau khi có workload đại diện.

`idx_ai_items_json_gin` cũng là ứng viên dư vì code không có JSON operator query. Cần kiểm tra external SQL/BI trước khi bỏ.

### 6.4. N+1 và phân trang

Recommendation history initialize episode, patient và snapshot theo từng run, tạo nguy cơ N+1. Role listing cũng có thể tải permissions theo từng role.

Phần lớn controller nhận `Pageable` mà không có giới hạn thống nhất. Khi lịch sử lớn, nên dùng projection/entity graph hoặc batch fetching, giới hạn page size, và chuyển chat/notification/run history sang cursor theo `(created_at, id)`.

### 6.5. Đánh số run/snapshot an toàn nhưng tuần tự hóa theo episode

Code dùng `MAX(...) + 1`, nhưng đã khóa episode bằng pessimistic write lock và có unique constraint làm lớp bảo vệ cuối. Cách làm đúng về tính nhất quán, nhưng mọi generation đồng thời trên cùng episode sẽ bị tuần tự hóa.

### 6.6. JSONB và lịch sử sẽ tăng nhanh

Schema có 23 cột JSONB trên 14 bảng. JSONB phù hợp cho AI payload và snapshot, nhưng clinical snapshot còn được sao chép vào outbox và nhiều kết quả JSON khác. Cần có `schema_version` thống nhất và chính sách retention trước khi cân nhắc partition.

### 6.7. Outbox có thể giữ transaction lâu

Dispatcher giữ DB transaction, connection và row lock trong lúc chờ RabbitMQ confirm. Khi broker chậm, connection pool có thể bị chiếm dụng. Cần theo dõi transaction duration, pool saturation, outbox backlog, retry và broker-confirm latency.

## 7. Thành phần dư thừa và legacy

### 7.1. Ứng viên orphan rõ nhất: `treatment_plan_versions`

- Live có 0 dòng.
- Có 5 index.
- Chỉ xuất hiện trong baseline migration.
- Không có entity, repository, service, DTO hoặc test sử dụng.
- Care plan hiện thuộc `pharmacist_final_decisions.care_plan_json`.

Đây là ứng viên bảng dư rõ nhất, nhưng vẫn phải xác nhận không có SQL, BI hoặc integration consumer bên ngoài repository trước khi xóa.

### 7.2. Enum PostgreSQL không còn được dùng

Mười enum hoàn toàn không được cột nào sử dụng: `ai_run_trigger_type`, `culturestatus`, `direct_enum`, `genderenum`, `implant_stability_type`, `implanttype`, `infectiontype`, `recommendation_item_category`, `review_status_type`, `userstatus`.

`ai_run_status` không còn là type của cột; nó chỉ còn bị default expression của cột varchar tham chiếu. Đây là schema debt và gây hiểu nhầm về mức độ DB đang kiểm soát lifecycle.

### 7.3. Cột legacy hoặc dễ drift

- `users.is_active` và `last_login_at` tồn tại song song với `status`, `last_login`.
- `users.avatar` vẫn là compatibility fallback nên chưa thể xóa ngay.
- `clinical_records.days_since_index_arthroplasty` được đánh dấu legacy nhưng snapshot compatibility path vẫn đọc.
- `pji_episodes.surgery_count` có thể lệch khỏi số dòng `surgeries`.
- `treatment_days`, `admission_count` và các giá trị dẫn xuất khác chưa có quy tắc đồng bộ ở DB.

Cần xác định chúng là dữ liệu hồ sơ nguồn hay giá trị dẫn xuất trước khi xóa hoặc tự động tính lại.

## 8. Thứ tự khuyến nghị

### Ưu tiên 0 — Đồng bộ contract

1. Validate và áp dụng V34 qua Flyway.
2. Kiểm tra không còn row/default `LEGACY_COMBINED`.
3. Xác nhận backend và worker dùng cùng scope contract.

### Ưu tiên 1 — Chốt chính sách dữ liệu

1. Định nghĩa retention, xóa, ẩn danh, amendment và phục hồi dữ liệu lâm sàng.
2. Quyết định single-hospital hay multi-tenant.
3. Xác định invariant bất biến nào phải được DB bảo vệ.

### Ưu tiên 2 — Tăng toàn vẹn

1. Chạy preflight duplicate và chuẩn hóa dữ liệu.
2. Thêm unique constraint cho permission route, identity card, request ID và email chuẩn hóa nếu được nghiệp vụ chấp thuận.
3. Thêm FK cho owner/source của pending task.
4. Loại ID aggregate trùng lặp hoặc thêm composite FK.
5. Siết owner `NOT NULL` sau khi xử lý legacy.
6. Thêm check constraint cho lifecycle có authority rõ.

### Ưu tiên 3 — Điều chỉnh query và index

1. Sửa N+1.
2. Thêm composite index khớp repository query.
3. Chỉ thêm FK index có bằng chứng từ join/delete workload.
4. Giới hạn page size và áp dụng cursor pagination khi cần.

### Ưu tiên 4 — Thu thập workload đại diện

1. Bật và lưu số liệu `pg_stat_statements`.
2. Theo dõi slow query, transaction duration, pool saturation, outbox backlog và tăng trưởng bảng/index.
3. Sau thời gian quan sát mới drop index bị bao phủ hoặc GIN không dùng.
4. Chỉ thiết kế partition/replica khi đã có volume, SLA và retention.

### Ưu tiên 5 — Dọn schema debt

1. Xác nhận không có external consumer của `treatment_plan_versions`.
2. Xóa bảng orphan bằng forward-only migration.
3. Loại enum không dùng.
4. Xóa cột legacy sau khi hoàn tất migration compatibility.

## 9. Giới hạn của kết luận hiệu năng

PostgreSQL mới chạy khoảng tám phút tại thời điểm thu thập. Vì vậy:

- `idx_scan = 0` không chứng minh index không dùng.
- Cache-hit ratio chưa đủ mẫu để sizing production.
- Chưa có `pg_stat_statements`, nên không có top/slow-query evidence.
- Chưa có `hypopg`, nên chưa mô phỏng được index giả định.
- Database dev gần như không có dữ liệu nghiệp vụ, nên chưa thể đo selectivity và query plan ở quy mô thật.

Mọi quyết định drop index, partition hoặc scale-out cần dựa trên workload đại diện.

## 10. Kết luận cuối cùng

Database có nền tảng quan hệ tốt. Snapshot có phiên bản, decision tách theo vai trò, final selection theo scope và durable outbox là những lựa chọn kiến trúc đúng hướng.

Tuy nhiên, database vẫn nên được xem là development schema cho đến khi:

- Live DB được đồng bộ lên V34.
- Invariant chéo giữa aggregate được bảo vệ.
- Business key được cưỡng chế ở DB.
- Chính sách retention và audit y tế được phê duyệt.
- Query/index mismatch quan trọng được xử lý.
- Có workload production-like để kiểm chứng hiệu năng.

Không nên tối ưu sớm bằng partition hoặc replica khi chưa có volume, SLA và retention rõ. Ưu tiên đúng lúc này là đồng bộ contract, củng cố toàn vẹn dữ liệu và xây dựng khả năng quan sát workload.

