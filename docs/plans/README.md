# Execution Plans

Execution plans are Git-native working memory for complex tasks. They preserve
enough context for another agent or human to resume work without reconstructing
intent from chat history or a partial diff.

## When To Create A Plan

Use an ephemeral plan for bounded, single-session work.

Create one durable plan when work spans sessions, coordinates contributors, has
meaningful dependencies or ordering, requires recovery steps, or would be unsafe
to resume from the diff alone.

Use `docs/templates/exec-plan.md` and place the file under `active/`.
For an explicitly authorized baseline-to-rerun Harness experiment, use
`docs/templates/harness-improvement.md` instead.

## Lifecycle

```text
docs/plans/active/<slug>.md
  -> update progress and decisions during implementation
  -> record final validation and result
  -> move to docs/plans/completed/<slug>.md
```

The plan is the primary task artifact. Promote a lasting product or architecture
decision into `docs/decisions/`; keep task-local choices in the plan.

## Active Plans

- [Đồng bộ identity-service theo đặc tả v1.1 và hoàn thiện Gateway trên WebFlux](active/identity-gateway-alignment.md): bước 0 xong; tiếp theo G1 chuyển Gateway sang WebFlux.
- [Đồng bộ Thẻ 15 — tiến độ và bản vá remote](active/the-15-docs-remote-patches.md): repository và 12 thẻ Google Docs đã sửa/đối chiếu sau lưu; chỉ Figma/drawio còn Blocked. Bằng chứng từng thẻ và bản vá thiết kế nằm trong cùng file.
