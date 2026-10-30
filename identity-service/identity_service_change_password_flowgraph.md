# identity-service — change password flow

How `PUT /api/v1/identity/users/me/password` changes the caller's password: `AccountController.changePassword` → `PasswordService.change` (`src/main/java/com/example/identity_service/auth/PasswordService.java`). Spec: API §6.1 "Đổi mật khẩu", UC-A04.

Request body: `{ currentPassword, newPassword, confirmPassword }`. Success is `204 No Content`, and the client must log in again because every session of the user, including the current one, is revoked.

```mermaid
flowchart TD
    req(["PUT /api/v1/identity/users/me/password<br/>Authorization: Bearer accessToken"]) --> filter

    subgraph security["Spring Security filter chain"]
        filter{"Bearer token present<br/>and valid?<br/>RS256, exp, iss, aud"}
        filter -- Yes --> sessionCheck{"SessionTokenValidator:<br/>session active and owned by sub,<br/>user ACTIVE, authzVersion current?"}
        sessionCheck -- Yes --> authorities["PermissionAuthentication:<br/>load current permissions<br/>from the database as authorities"]
    end

    filter -- "No / expired" --> e401(["401 UNAUTHENTICATED<br/>or TOKEN_EXPIRED"])
    sessionCheck -- "No: stale authzVersion" --> e401stale(["401 TOKEN_STALE"])
    sessionCheck -- "No: revoked session or disabled user" --> e401

    authorities --> valid{"Body valid?<br/>3 fields not blank"}
    valid -- No --> e400v(["400 VALIDATION_ERROR<br/>details per field"])
    valid -- Yes --> perm{"@PreAuthorize<br/>hasAuthority account:update:own?"}
    perm -- No --> e403(["403 FORBIDDEN"])

    perm -- Yes --> tx

    subgraph tx["PasswordService.change(caller, request): one transaction"]
        load["users.findById(caller.userId)<br/>userId comes from the token sub"] --> local{"authMethod LOCAL?"}
        local -- Yes --> current{"currentPassword matches<br/>stored hash?"}
        current -- Yes --> confirm{"newPassword equals<br/>confirmPassword?"}
        confirm -- Yes --> policy{"G-07 policy:<br/>8+ chars, upper, lower, digit,<br/>at most 72 bytes"}
        policy -- Yes --> same{"newPassword matches<br/>the current hash?"}
        same -- No --> change["user.changePassword(encode(newPassword))<br/>BCrypt via delegating encoder"]
        change --> revoke["sessions.revokeAllOf(userId)<br/>revoked_at = now on every active session"]
    end

    local -- "No: GOOGLE" --> e403g(["403 FORBIDDEN<br/>Google accounts have no password"])
    current -- No --> e400a(["400 PASSWORD_INCORRECT<br/>field currentPassword"])
    confirm -- No --> e400b(["400 VALIDATION_ERROR<br/>field confirmPassword"])
    policy -- No --> e400c(["400 PASSWORD_POLICY_VIOLATION<br/>field newPassword"])
    same -- Yes --> e400d(["400 PASSWORD_SAME_AS_OLD<br/>field newPassword"])

    revoke --> commit(["Commit, then 204 No Content<br/>client must log in again"])

    classDef fail fill:#fde2e1,stroke:#c0392b,color:#7b241c
    classDef success fill:#dff5e3,stroke:#1e8449,color:#145a32
    classDef danger fill:#fff3cd,stroke:#b7950b,color:#7d6608
    class e401,e401stale,e400v,e403,e403g,e400a,e400b,e400c,e400d fail
    class commit success
    class revoke danger
```

## Outcomes

| Situation | Response | Side effect |
|---|---|---|
| Missing, invalid or expired access token | 401 `UNAUTHENTICATED` / `TOKEN_EXPIRED` | None |
| Token issued before a role or status change | 401 `TOKEN_STALE` | None; the client refreshes and retries |
| Blank field | 400 `VALIDATION_ERROR` | None |
| Google account | 403 `FORBIDDEN` | None |
| Wrong current password | 400 `PASSWORD_INCORRECT` | None; the session stays valid |
| `confirmPassword` differs | 400 `VALIDATION_ERROR` (`confirmPassword`) | None |
| New password fails G-07 | 400 `PASSWORD_POLICY_VIOLATION` | None |
| New password equals the current one | 400 `PASSWORD_SAME_AS_OLD` | None |
| Everything valid | 204 | Password hash replaced; every session of the user revoked, so all its access and refresh tokens stop working |

## Notes

- The user is always the token's `sub` (`Caller.of(jwt)`); the body has no user id, so a caller can only change their own password.
- Body validation runs before `@PreAuthorize`: Spring MVC resolves and validates the arguments, then calls the method-security proxy. Every ACTIVE user has `account:update:own` through the baseline policy, so the permission check only fails for unusual states.
- Checks inside the service run in the order drawn. The first failure throws `ApiException`, the transaction rolls back, and nothing changes.
- Changing the password does not bump `authzVersion`: revoking the sessions is what invalidates the old tokens, because `SessionTokenValidator` rejects any token whose session is revoked.
- Tests: `auth/PasswordEndpointsTests.java` (`changePasswordEndsEverySessionIncludingTheCurrentOne`, `changePasswordReportsEachProblemAndKeepsTheSession`, `googleAccountCannotChangePassword`, `changePasswordNeedsAnAccessToken`).
