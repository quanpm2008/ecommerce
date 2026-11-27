# identity-service — `SessionService.refresh` flow

How `SessionService.refresh(refreshToken)` (`src/main/java/com/example/identity_service/service/SessionService.java`) exchanges a refresh token for a new token pair. Spec: API §3.2 and §6.1 `POST /identity/auth/refresh`, [decision 0003](../docs/decisions/0003-mot-phien-refresh-tren-user.md) (one session per User, on `users`), DB §6 `RotatedRefreshToken`.

The whole method runs in one transaction with `noRollbackFor = ApiException.class`, so ending a session on token reuse is committed even though the call then fails with `INVALID_REFRESH_TOKEN`.

```mermaid
flowchart TD
    start(["refresh(refreshToken)"]) --> hash["now = clock.instant()<br/>hash = SHA-256(refreshToken)"]
    hash --> findCurrent["users: find by refresh_token_hash = hash<br/>(PESSIMISTIC_WRITE lock)"]
    findCurrent --> isCurrent{"Is it a user's<br/>current token?"}

    %% Not current: unknown token or an already rotated one
    isCurrent -- No --> findRotated["rotated_refresh_tokens: find by token_hash = hash"]
    findRotated --> wasRotated{"Rotated away<br/>before?"}
    wasRotated -- "No: unknown token" --> invalid
    wasRotated -- "Yes" --> lockUser["users: find by mark.user_id<br/>(PESSIMISTIC_WRITE lock)"]
    lockUser --> sameSession{"users.session_id =<br/>mark.session_id?"}
    sameSession -- "No: session already replaced or ended" --> invalid
    sameSession -- "Yes: token reuse" --> revoke["Log a warning<br/>user.endSession() + delete the user's marks<br/>(the whole chain dies)"]
    revoke --> invalid

    %% Current token: check session and user, then rotate
    isCurrent -- Yes --> canRefresh{"user.canRefresh(now)?<br/>session set and now before refresh_token_expires_at"}
    canRefresh -- No --> invalid
    canRefresh -- Yes --> active{"User ACTIVE?"}
    active -- "No: DISABLED" --> invalid
    active -- Yes --> signIn["signIn.accept(user)<br/>seller approval, ban rules"]
    signIn --> keepOld["rotated_refresh_tokens: insert (hash, user_id, session_id, now)"]
    keepOld --> rotate["next = 256 random bits, base64url<br/>user.rotateRefreshToken(SHA-256(next), now + refresh-ttl)<br/>session_id unchanged"]
    rotate --> issue["AccessTokenIssuer.issue(user, sessionId)<br/>RS256 JWT with the user's current authzVersion"]
    issue --> ok(["Return IssuedTokens<br/>userId, sessionId, accessToken,<br/>accessTokenExpiresIn, refreshToken = next"])

    invalid(["throw ApiException INVALID_REFRESH_TOKEN (401)<br/>transaction still commits"])

    classDef fail fill:#fde2e1,stroke:#c0392b,color:#7b241c
    classDef success fill:#dff5e3,stroke:#1e8449,color:#145a32
    classDef danger fill:#fff3cd,stroke:#b7950b,color:#7d6608
    class invalid fail
    class ok success
    class revoke danger
```

## Outcomes

| Situation | Result | Side effect |
|---|---|---|
| Current token, session not expired, user ACTIVE | New `IssuedTokens` | Old hash saved in `rotated_refresh_tokens`; `users` gets the new hash and `refresh_token_expires_at` = now + TTL; `session_id` kept |
| Unknown token | `INVALID_REFRESH_TOKEN` | None |
| Rotated token of the current session (reuse) | `INVALID_REFRESH_TOKEN` | Session ended (three `users` fields null), so the newest refresh token and every access token of that session stop working |
| Token of a session replaced by a newer login | `INVALID_REFRESH_TOKEN` | None: it cannot end the new session |
| Current token, but expired | `INVALID_REFRESH_TOKEN` | None |
| Current token, but user DISABLED | `INVALID_REFRESH_TOKEN` | None (disabling the user ends its session separately) |

## Notes

- Only hashes are stored or looked up; the raw refresh token never touches the database.
- Login (`start`) locks the user row, sets a new `session_id` and deletes the marks of older sessions; the previous session's access tokens fail the validator (`users.session_id` no longer matches) on their next request.
- The lock on the user row serializes two refreshes with the same token. The second one no longer finds the token as current, finds its mark for the current session, and ends the session: a client racing itself must log in again (decision 0003, no grace period).
- Tests: `service/SessionServiceTests.java` (rotation, expiry move, reuse, replaced session, logout with a rotated token, expired, disabled, unknown, current `authzVersion`), `controller/AuthEndpointsTests.java` (a new login replaces the previous session).
