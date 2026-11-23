# Decisions

Decision records preserve lasting product, architecture, data ownership,
security, compatibility, and validation choices that future work must inherit.

Use `docs/templates/decision.md`. Task-local implementation choices remain in
the active execution plan and do not require a separate decision.

An installed consumer begins with no fabricated decisions. Add local decision
documents here as real choices are accepted, then index them in this file.

## Index

- [0001 Refresh token trong cookie HttpOnly và kiểm tra Origin](0001-refresh-cookie-va-origin.md) — Accepted 2026-11-20
- [0002 Giới hạn tần suất và kích thước body tại Gateway](0002-gioi-han-tan-suat-gateway.md) — Accepted 2026-11-20
- [0003 Một phiên refresh trên User](0003-mot-phien-refresh-tren-user.md) — Accepted 2026-11-23; đặc tả mới, chưa triển khai code/database
