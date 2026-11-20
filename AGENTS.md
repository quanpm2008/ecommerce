# Agent Instructions

<!-- HARNESS:BEGIN -->
## Harness

Start with the requested outcome and use the repository as the system of record.
Read `docs/WORKFLOW.md` and only relevant product, design, plan, code, and
validation material.

- Answers, explanations, reviews, diagnoses, plans, and status reports are
  read-only. Inspect only what is needed; change nothing.
- For a bounded change, inspect affected behavior and proof, implement, and
  validate. No control-plane operation is required.
- Use one `docs/plans/active/` file when work spans sessions, coordinates
  contributors, has dependencies, or needs recovery. Move it to
  `docs/plans/completed/` only after validation.
- Before editing, identify repository authority for each new externally
  observable policy. If materially different choices remain open, stop before
  edits; configurable defaults are not authority.
- For architecture, reliability, security, or quality invariant work, read
  `docs/patterns/encoding-invariants.md` and enforce only accepted rules.
- Report reusable agent friction. Change guidance, tools, runbooks, or validation
  for that purpose only when explicitly asked to use `$improve-harness`.
- Also pause when product intent remains ambiguous, recovery is difficult,
  validation is weakened, or authority is insufficient.
- Claim completion only with executable or observable evidence. Report outcome,
  changes, validation, and unresolved risks.

Harness has no task database or orchestration lifecycle. Use repository plans
and behavior-level proof; do not create parallel control-plane state.
<!-- HARNESS:END -->

## Ecommerce

- Khi sửa tính năng, bắt đầu ở [docs/README.md](docs/README.md), chọn hồ sơ
  nghiệp vụ và đọc yêu cầu/ngoại lệ/AC cùng bản đồ code/API/schema/test.
- Khi chọn library, sửa ownership/transaction hoặc kiểm chứng, đọc
  [docs/engineering.md](docs/engineering.md).
- Baseline: [SAD v1.1 §7.1/D.1](docs/requirements/software-architecture.md);
  [System Architecture](docs/requirements/system-architecture.md) mô tả thành phần/boundary.
  [Coverage](docs/requirements/coverage.md) ghi nguồn, phần bị thay thế và câu hỏi mở.
  Nguồn chi tiết: [FRD v5.5](docs/requirements/FRD.md),
  [API v1.1](docs/requirements/api_specs.md), [Database v5.1](docs/requirements/database.md).
  Giữ nội dung nguồn nếu task không yêu cầu sửa; code/test hiện tại không tự là
  yêu cầu nghiệp vụ đúng.
- Ba nguồn ngày 16/11/2026 chưa đồng nghĩa hồ sơ tóm tắt theo baseline
  11/11/2026 hoặc code đã đồng bộ. Khi khác nhau, đối chiếu nguồn/phiên bản và
  ghi nhận xung đột ở coverage; không tự suy diễn quyết định thay thế.
