classDiagram
direction BT
class AccessTokenDecoderConfig {
  + AccessTokenDecoderConfig() 
  ~ jwtDecoder(RSAKey, TokenProperties, SessionTokenValidator, Clock) JwtDecoder
}
class AccessTokenIssuer {
  + AccessTokenIssuer(JwtEncoder, TokenProperties, Clock) 
  ~ issue(User, UUID) String
}
class AdminAuditLog {
  # AdminAuditLog() 
  + AdminAuditLog(UUID, AuditAction, AuditTargetType, UUID, String, Map~String, Object~, String) 
  - UUID actorUserId
  - AuditTargetType targetType
  - String requestId
  - UUID auditId
  - UUID targetId
  - Map~String, Object~ beforeAfter
  - String reason
  - AuditAction action
  - Instant happenedAt
   UUID auditId
   AuditTargetType targetType
   Instant happenedAt
   UUID actorUserId
   UUID targetId
   String requestId
   Map~String, Object~ beforeAfter
   String reason
   AuditAction action
}
class AdminAuditLogRepository {
<<Interface>>
  + findByTargetTypeAndTargetIdOrderByHappenedAt(AuditTargetType, UUID) List~AdminAuditLog~
}
class AdminBootstrap {
  + AdminBootstrap(AdminBootstrapProperties, UserRepository, RoleAssignments, AdminAuditLogRepository, PasswordEncoder) 
  + run(ApplicationArguments) void
}
class AdminBootstrapProperties {
  + AdminBootstrapProperties(String, String, String) 
  + fullName() String
  + email() String
  + password() String
}
class ApiException {
  + ApiException(ErrorCode, String, List~Map~String, Object~~) 
  + ApiException(ErrorCode, String) 
  + ApiException(ErrorCode) 
  + details() List~Map~String, Object~~
  + code() ErrorCode
}
class ApiResponse~T~ {
  + ApiResponse(T, PageMeta) 
  + page(Page~T~) ApiResponse~List~T~~
  + meta() PageMeta
  + of(T) ApiResponse~T~
  + data() T
}
class AuditAction {
<<enumeration>>
  + AuditAction() 
  + valueOf(String) AuditAction
  + values() AuditAction[]
}
class AuditTargetType {
<<enumeration>>
  + AuditTargetType() 
  + valueOf(String) AuditTargetType
  + values() AuditTargetType[]
}
class AuthMethod {
<<enumeration>>
  + AuthMethod() 
  + values() AuthMethod[]
  + valueOf(String) AuthMethod
}
class AuthSession {
  # AuthSession() 
  ~ AuthSession(UUID, String, Instant) 
  - UUID userId
  - Instant expiresAt
  - Instant revokedAt
  - Instant createdAt
  - String refreshTokenHash
  - UUID sessionId
  ~ rotate(String, Instant) void
  ~ revoke(Instant) void
  ~ canRefresh(Instant) boolean
   String refreshTokenHash
   boolean revoked
   UUID userId
   Instant expiresAt
   Instant revokedAt
   Instant createdAt
   UUID sessionId
}
class AuthSessionRepository {
<<Interface>>
  + findByRefreshTokenHash(String) Optional~AuthSession~
  + findLockedById(UUID) Optional~AuthSession~
  + revokeAllOfUser(UUID, Instant) int
}
class Body {
  + Body(String, String, List~Map~String, Object~~, String) 
  + details() List~Map~String, Object~~
  + requestId() String
  + message() String
  + code() String
}
class ClockConfig {
  + ClockConfig() 
  ~ clock() Clock
}
class Customer {
  # Customer() 
  + Customer(User) 
  - User user
  - CustomerBanMode banMode
  - Instant createdAt
  - Instant updatedAt
  - CustomerStatus status
  - UUID customerId
  - Boolean hasPaidOrderBeforeBan
  - String banReason
   CustomerBanMode banMode
   String banReason
   Boolean hasPaidOrderBeforeBan
   UUID customerId
   Instant updatedAt
   CustomerStatus status
   User user
   Instant createdAt
}
class CustomerAddress {
  # CustomerAddress() 
  + CustomerAddress(UUID, String, String, String, String, String) 
  - String recipientPhone
  - String wardCode
  - Instant updatedAt
  - UUID addressId
  - UUID customerId
  - String provinceCode
  - Instant lastUsedAt
  - String recipientName
  - Instant createdAt
  - String addressDetail
  - Instant deletedAt
   String provinceCode
   String addressDetail
   Instant lastUsedAt
   String wardCode
   String recipientPhone
   UUID customerId
   UUID addressId
   Instant updatedAt
   Instant createdAt
   Instant deletedAt
   String recipientName
}
class CustomerAddressRepository {
<<Interface>>

}
class CustomerBanMode {
<<enumeration>>
  + CustomerBanMode() 
  + values() CustomerBanMode[]
  + valueOf(String) CustomerBanMode
}
class CustomerRepository {
<<Interface>>

}
class CustomerStatus {
<<enumeration>>
  + CustomerStatus() 
  + values() CustomerStatus[]
  + valueOf(String) CustomerStatus
}
class ErrorCode {
<<enumeration>>
  - ErrorCode(HttpStatus, String) 
  + valueOf(String) ErrorCode
  + status() HttpStatus
  + values() ErrorCode[]
  + defaultMessage() String
}
class ErrorResponse {
  + ErrorResponse(Body) 
  + of(ErrorCode, String, List~Map~String, Object~~, String) ErrorResponse
  + error() Body
}
class GlobalExceptionHandler {
  + GlobalExceptionHandler() 
  - toUpperSnake(String) String
  ~ handleApiException(ApiException, WebRequest) ResponseEntity~Object~
  # handleExceptionInternal(Exception, Object, HttpHeaders, HttpStatusCode, WebRequest) ResponseEntity~Object~?
  - codeFor(HttpStatusCode) ErrorCode
  - fieldDetail(FieldError) Map~String, Object~
  # handleMethodArgumentNotValid(MethodArgumentNotValidException, HttpHeaders, HttpStatusCode, WebRequest) ResponseEntity~Object~?
  - respond(ErrorCode, String, List~Map~String, Object~~, HttpHeaders, HttpStatusCode, WebRequest) ResponseEntity~Object~
  ~ handleUnexpected(Exception, WebRequest) ResponseEntity~Object~
}
class HelloController {
  + HelloController() 
  + hello() String
}
class IdentityServiceApplication {
  + IdentityServiceApplication() 
  + main(String[]) void
}
class IssuedTokens {
  + IssuedTokens(UUID, UUID, String, long, String) 
  + accessToken() String
  + userId() UUID
  + refreshToken() String
  + sessionId() UUID
  + accessTokenExpiresIn() long
}
class JwksController {
  + JwksController(RSAKey) 
  ~ jwks() Map~String, Object~
}
class PageMeta {
  + PageMeta(int, int, long, int) 
  + size() int
  + totalPages() int
  + from(Page~?~) PageMeta
  + page() int
  + totalItems() long
}
class PasswordConfig {
  + PasswordConfig() 
  ~ passwordEncoder() PasswordEncoder
}
class PasswordPolicy {
  - PasswordPolicy() 
  + isSatisfiedBy(String) boolean
}
class Permission {
  # Permission() 
  - UUID permissionId
  - String code
  - Instant createdAt
  - String description
   String description
   String code
   UUID permissionId
   Instant createdAt
}
class PermissionResolver {
  + PermissionResolver(UserRoleRepository) 
  + activeRolesOf(User) Set~RoleCode~
  + permissionsOf(User) SortedSet~String~
}
class Permissions {
  - Permissions() 
}
class RefreshTokens {
  - RefreshTokens() 
  ~ generate() String
  ~ hash(String) String
}
class RequestIdFilter {
  + RequestIdFilter() 
  # doFilterInternal(HttpServletRequest, HttpServletResponse, FilterChain) void
}
class Role {
  # Role() 
  - String name
  - Set~Permission~ permissions
  - UUID roleId
  - Instant createdAt
  - RoleCode code
  - String description
   String name
   String description
   Set~Permission~ permissions
   RoleCode code
   UUID roleId
   Instant createdAt
}
class RoleAssignments {
  + RoleAssignments(RoleRepository, UserRoleRepository, UserRepository) 
  + revoke(User, RoleCode) boolean
  - bump(User) void
  + grant(User, RoleCode, UUID) boolean
}
class RoleCode {
<<enumeration>>
  + RoleCode() 
  + values() RoleCode[]
  + valueOf(String) RoleCode
}
class RoleRepository {
<<Interface>>
  + findByCode(RoleCode) Optional~Role~
}
class RotatedRefreshToken {
  ~ RotatedRefreshToken(String, UUID, Instant) 
  # RotatedRefreshToken() 
  - String tokenHash
  - Instant rotatedAt
  - UUID sessionId
   String tokenHash
   Instant rotatedAt
   UUID sessionId
}
class RotatedRefreshTokenRepository {
<<Interface>>

}
class SecurityConfig {
  + SecurityConfig() 
  ~ apiSecurity(HttpSecurity, SecurityErrorHandler) SecurityFilterChain
}
class SecurityErrorHandler {
  + SecurityErrorHandler(JsonMapper) 
  + commence(HttpServletRequest, HttpServletResponse, AuthenticationException) void
  + handle(HttpServletRequest, HttpServletResponse, AccessDeniedException) void
  - codeFor(AuthenticationException) ErrorCode
  - write(HttpServletRequest, HttpServletResponse, ErrorCode) void
}
class Seller {
  # Seller() 
  + Seller(User, String, String, String) 
  - String rejectionReason
  - String contactPhone
  - String description
  - UUID reviewedByUserId
  - String sellerName
  - Instant updatedAt
  - User user
  - Instant reviewedAt
  - SellerStatus status
  - SellerApprovalStatus approvalStatus
  - String banReason
  - String logoObjectKey
  - Instant createdAt
  - UUID sellerId
  - Instant registeredAt
   String description
   String sellerName
   String rejectionReason
   SellerApprovalStatus approvalStatus
   UUID sellerId
   UUID reviewedByUserId
   User user
   Instant createdAt
   String banReason
   Instant reviewedAt
   Instant registeredAt
   SellerStatus status
   Instant updatedAt
   String contactPhone
   String logoObjectKey
}
class SellerApprovalStatus {
<<enumeration>>
  + SellerApprovalStatus() 
  + values() SellerApprovalStatus[]
  + valueOf(String) SellerApprovalStatus
}
class SellerRepository {
<<Interface>>

}
class SellerStatus {
<<enumeration>>
  + SellerStatus() 
  + values() SellerStatus[]
  + valueOf(String) SellerStatus
}
class SessionService {
  + SessionService(AuthSessionRepository, RotatedRefreshTokenRepository, UserRepository, AccessTokenIssuer, TokenProperties, Clock) 
  + start(User) IssuedTokens
  - revokeReused(AuthSession, Instant) void
  + revoke(UUID) void
  + revokeAllOf(UUID) void
  + refresh(String) IssuedTokens
  - tokens(User, AuthSession, String) IssuedTokens
}
class SessionTokenValidator {
  + SessionTokenValidator(AuthSessionRepository, UserRepository) 
  - invalid(String) OAuth2TokenValidatorResult
  + validate(Jwt) OAuth2TokenValidatorResult
  - uuid(String) Optional~UUID~
}
class SigningKeyConfig {
  + SigningKeyConfig() 
  ~ signingKey(TokenProperties) RSAKey
  ~ jwtEncoder(RSAKey) JwtEncoder
  - parsePkcs8(String) RSAPrivateCrtKey
}
class TokenClaims {
  - TokenClaims() 
}
class TokenProperties {
  + TokenProperties(String, String, Duration, Duration, String) 
  + issuer() String
  + audience() String
  + accessTtl() Duration
  + refreshTtl() Duration
  + signingKey() String
}
class User {
  # User() 
  - long authzVersion
  - String email
  - String fullName
  - String disabledReason
  - UUID userId
  - String phoneNumber
  - Instant createdAt
  - Instant updatedAt
  - String googleSubject
  - UserStatus status
  - AuthMethod authMethod
  - String passwordHash
  + local(String, String, String, String) User
  + enable() void
  + disable(String) void
  + normalizeEmail(String) String
  + bumpAuthzVersion() void
   long authzVersion
   String disabledReason
   AuthMethod authMethod
   Instant createdAt
   boolean active
   String passwordHash
   String email
   String phoneNumber
   UUID userId
   UserStatus status
   String googleSubject
   String fullName
   Instant updatedAt
}
class UserRepository {
<<Interface>>
  + findByEmail(String) Optional~User~
}
class UserRole {
  + UserRole(UUID, Role, UUID) 
  # UserRole() 
  - Instant revokedAt
  - Role role
  - UUID grantedByUserId
  - Instant grantedAt
  - UserRoleId id
  ~ regrant(UUID) void
  ~ revoke() void
   UUID grantedByUserId
   boolean active
   Role role
   UserRoleId id
   Instant revokedAt
   Instant grantedAt
}
class UserRoleId {
  + UserRoleId(UUID, UUID) 
  + userId() UUID
  + roleId() UUID
}
class UserRoleRepository {
<<Interface>>
  + findActiveRoleCodes(UUID) Set~RoleCode~
  + findActivePermissionCodes(UUID) Set~String~
}
class UserStatus {
<<enumeration>>
  + UserStatus() 
  + values() UserStatus[]
  + valueOf(String) UserStatus
}

AccessTokenIssuer "1" *--> "properties 1" TokenProperties 
AdminAuditLog "1" *--> "action 1" AuditAction 
AdminAuditLog "1" *--> "targetType 1" AuditTargetType 
AdminBootstrap  ..>  AdminAuditLog : «create»
AdminBootstrap "1" *--> "auditLog 1" AdminAuditLogRepository 
AdminBootstrap "1" *--> "properties 1" AdminBootstrapProperties 
AdminBootstrap "1" *--> "roleAssignments 1" RoleAssignments 
AdminBootstrap "1" *--> "users 1" UserRepository 
ApiException "1" *--> "code 1" ErrorCode 
ErrorResponse  -->  Body 
Customer "1" *--> "banMode 1" CustomerBanMode 
Customer "1" *--> "status 1" CustomerStatus 
Customer "1" *--> "user 1" User 
ErrorResponse  ..>  Body : «create»
ErrorResponse "1" *--> "error 1" Body 
PermissionResolver "1" *--> "userRoles 1" UserRoleRepository 
Role "1" *--> "permissions *" Permission 
Role "1" *--> "code 1" RoleCode 
RoleAssignments "1" *--> "roles 1" RoleRepository 
RoleAssignments "1" *--> "users 1" UserRepository 
RoleAssignments  ..>  UserRole : «create»
RoleAssignments  ..>  UserRoleId : «create»
RoleAssignments "1" *--> "userRoles 1" UserRoleRepository 
Seller "1" *--> "approvalStatus 1" SellerApprovalStatus 
Seller "1" *--> "status 1" SellerStatus 
Seller "1" *--> "user 1" User 
SessionService "1" *--> "accessTokens 1" AccessTokenIssuer 
SessionService  ..>  ApiException : «create»
SessionService  ..>  AuthSession : «create»
SessionService "1" *--> "sessions 1" AuthSessionRepository 
SessionService  ..>  IssuedTokens : «create»
SessionService  ..>  RotatedRefreshToken : «create»
SessionService "1" *--> "rotatedTokens 1" RotatedRefreshTokenRepository 
SessionService "1" *--> "properties 1" TokenProperties 
SessionService "1" *--> "users 1" UserRepository 
SessionTokenValidator "1" *--> "sessions 1" AuthSessionRepository 
SessionTokenValidator "1" *--> "users 1" UserRepository 
User "1" *--> "authMethod 1" AuthMethod 
User "1" *--> "status 1" UserStatus 
UserRole "1" *--> "role 1" Role 
UserRole "1" *--> "id 1" UserRoleId 
UserRole  ..>  UserRoleId : «create»
