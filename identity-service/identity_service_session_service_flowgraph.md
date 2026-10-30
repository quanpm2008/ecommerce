# identity-service — `SessionService.refresh` flow

How `SessionService.refresh(refreshToken)` (`src/main/java/com/example/identity_service/session/SessionService.java`) exchanges a refresh token for a new token pair. Spec: API §3.2 and §6.1 `POST /identity/auth/refresh`, DB §6 `AuthSession`.

The whole method runs in one transaction with `noRollbackFor = ApiException.class`, so revoking a session on token reuse is committed even though the call then fails with `INVALID_REFRESH_TOKEN`.

```mermaid
flowchart TD
    start(["refresh(refreshToken)"]) --> hash["now = clock.instant()<br/>hash = SHA-256(refreshToken)"]
    hash --> findCurrent["auth_sessions: find by refresh_token_hash = hash<br/>(PESSIMISTIC_WRITE lock)"]
    findCurrent --> isCurrent{"Is it a session's<br/>current token?"}

    %% Not current: unknown token or an already rotated one
    isCurrent -- No --> findRotated["rotated_refresh_tokens: find by token_hash = hash"]
    findRotated --> wasRotated{"Rotated away<br/>before?"}
    wasRotated -- "No: unknown token" --> invalid
    wasRotated -- "Yes: token reuse" --> lockSession["auth_sessions: find by session_id<br/>(PESSIMISTIC_WRITE lock)"]
    lockSession --> alreadyRevoked{"Session already<br/>revoked?"}
    alreadyRevoked -- Yes --> invalid
    alreadyRevoked -- No --> revoke["Log a warning<br/>session.revoke(now)<br/>(the whole token chain dies)"]
    revoke --> invalid

    %% Current token: check session and user, then rotate
    isCurrent -- Yes --> canRefresh{"session.canRefresh(now)?<br/>not revoked and now before expires_at"}
    canRefresh -- No --> invalid
    canRefresh -- Yes --> loadUser["users: find by session.user_id"]
    loadUser --> active{"User ACTIVE?"}
    active -- "No: DISABLED" --> invalid
    active -- Yes --> keepOld["rotated_refresh_tokens: insert (hash, session_id, now)"]
    keepOld --> rotate["next = 256 random bits, base64url<br/>session.rotate(SHA-256(next), now + refresh-ttl)"]
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
| Current token, session active, user ACTIVE | New `IssuedTokens` | Old hash saved in `rotated_refresh_tokens`; session gets the new hash and a new `expires_at` (sliding) |
| Unknown token | `INVALID_REFRESH_TOKEN` | None |
| Rotated token presented again (reuse) | `INVALID_REFRESH_TOKEN` | Session revoked, so the newest refresh token and every access token of that session stop working |
| Current token, but session revoked or expired | `INVALID_REFRESH_TOKEN` | None |
| Current token, but user DISABLED | `INVALID_REFRESH_TOKEN` | None (disabling the user revokes its sessions separately) |

## Notes

- Only hashes are stored or looked up; the raw refresh token never touches the database.
- The lock on the session row serializes two refreshes with the same token. The second one no longer finds the token as current, finds it in `rotated_refresh_tokens`, and revokes the session: a client racing itself must log in again (strict on purpose, see `identity-service.md`).
- Roles and permissions are not in the token. The new access token carries the user's `authzVersion` read from the database at refresh time, and the step 3 refresh endpoint builds the profile from the current roles.
- Tests: `session/SessionServiceTests.java` (rotation, reuse, expired, revoked, disabled, unknown, current `authzVersion`).
