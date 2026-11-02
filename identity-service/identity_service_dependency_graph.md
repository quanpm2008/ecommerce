classDiagram
direction BT
class AccessMode {
<<enumeration>>
  + AccessMode() 
  + values() AccessMode[]
  + valueOf(String) AccessMode
}
class AccessTokenDecoderConfig {
  + AccessTokenDecoderConfig() 
  ~ jwtDecoder(RSAKey, TokenProperties, SessionTokenValidator, Clock) JwtDecoder
  + decoder(RSAKey, TokenProperties, Clock, OAuth2TokenValidator~Jwt~[]) JwtDecoder
}
class AccessTokenIssuer {
  + AccessTokenIssuer(JwtEncoder, TokenProperties, Clock) 
  ~ issue(User, UUID) String
}
class AccountController {
  + AccountController(PasswordService, AccountService, SellerAccountService) 
  ~ changePassword(Jwt, ChangePassword) void
  ~ me(Jwt) ApiResponse~UserSelfProfile~
  ~ createCustomerProfile(Jwt) ResponseEntity~ApiResponse~UserSelfProfile~~
  ~ submitSellerApplication(Jwt, Map~String, Object~) ResponseEntity~ApiResponse~SellerSelfProfile~~
  ~ sellerApplication(Jwt) ApiResponse~SellerSelfProfile~
  ~ update(Jwt, Map~String, Object~) ApiResponse~UserSelfProfile~
}
class AccountService {
  + AccountService(UserRepository, CustomerRepository, UserRoleRepository, RoleAssignments, SelfProfileAssembler) 
  + update(Caller, Map~String, Object~) UserSelfProfile
  + createCustomerProfile(Caller) Result
  + me(Caller) UserSelfProfile
}
class Address {
  + Address(UUID, String, String, String, String, String, String, String, Instant, boolean, Instant) 
  - boolean isDefault
  + wardName() String
  + addressDetail() String
  + provinceName() String
  + wardCode() String
  + lastUsedAt() Instant
  + addressId() UUID
  + createdAt() Instant
  + recipientPhone() String
  + recipientName() String
  + provinceCode() String
   boolean isDefault
}
class AddressController {
  + AddressController(AddressService) 
  ~ update(Jwt, UUID, AddressRequest) ApiResponse~Address~
  ~ list(Jwt) ApiResponse~List~Address~~
  ~ create(Jwt, AddressRequest) ApiResponse~Address~
  ~ delete(Jwt, UUID) void
}
class AddressProperties {
  + AddressProperties(int) 
  + maxPerCustomer() int
}
class AddressRequest {
  + AddressRequest(String, String, String, String, String) 
  + provinceCode() String
  + recipientName() String
  + addressDetail() String
  + recipientPhone() String
  + wardCode() String
}
class AddressService {
  + AddressService(CustomerRepository, CustomerAddressRepository, RegionCatalog, AddressProperties, Clock) 
  + maxAddresses() int
  - noCustomer() ApiException
  - defaultIdOf(UUID) UUID?
  + list(Caller) List~Address~
  - recipientPhone(AddressRequest) String
  + create(Caller, AddressRequest) Address
  + delete(Caller, UUID) void
  - view(CustomerAddress, UUID) Address
  - requireActive(Customer) void
  - requireCustomer(Caller) void
  + update(Caller, UUID, AddressRequest) Address
  - owned(Caller, UUID) CustomerAddress
}
class AddressSnapshot {
  + AddressSnapshot(UUID, UUID, String, String, String, String, String, String, String) 
  + addressDetail() String
  + wardCode() String
  + recipientPhone() String
  + provinceName() String
  + addressId() UUID
  + recipientName() String
  + provinceCode() String
  + customerId() UUID
  + wardName() String
}
class AddressTouch {
  + AddressTouch(UUID, Instant, boolean) 
  + lastUsedAt() Instant
  + addressId() UUID
  + applied() boolean
}
class AdminAuditLog {
  # AdminAuditLog() 
  + AdminAuditLog(UUID, AuditAction, AuditTargetType, UUID, String, Map~String, Object~, String) 
  - UUID actorUserId
  - AuditAction action
  - UUID targetId
  - String reason
  - Map~String, Object~ beforeAfter
  - String requestId
  - Instant happenedAt
  - UUID auditId
  - AuditTargetType targetType
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
  + email() String
  + fullName() String
  + password() String
}
class AdminController {
  + AdminController(AdminDirectory, SellerReviewService, ModerationService) 
  ~ unban(Jwt, UUID, UnbanRequest) ApiResponse~UnbanResult~
  ~ reject(Jwt, UUID, RejectRequest) ApiResponse~SellerAdminView~
  ~ customer(UUID) ApiResponse~CustomerAdminView~
  ~ approve(Jwt, UUID) ApiResponse~SellerAdminView~
  ~ seller(UUID) ApiResponse~SellerAdminView~
  ~ ban(Jwt, UUID, BanRequest) ApiResponse~BanResult~
  ~ sellers(String, SellerApprovalStatus, SellerStatus, Pageable) ApiResponse~List~SellerAdminView~~
  ~ customers(String, CustomerStatus, Pageable) ApiResponse~List~CustomerAdminView~~
}
class AdminDirectory {
  + AdminDirectory(CustomerRepository, SellerRepository, AdminViews) 
  + customer(UUID) CustomerAdminView
  - page(Pageable, Map~String, String~, Sort, String) Pageable
  + customers(String, CustomerStatus, Pageable) Page~CustomerAdminView~
  + sellers(String, SellerApprovalStatus, SellerStatus, Pageable) Page~SellerAdminView~
  + seller(UUID) SellerAdminView
}
class AdminViews {
  + AdminViews(SelfProfileAssembler) 
  + customer(Customer) CustomerAdminView
  + seller(Seller) SellerAdminView
}
class ApiException {
  + ApiException(ErrorCode, String) 
  + ApiException(ErrorCode, String, List~Map~String, Object~~) 
  + ApiException(ErrorCode) 
  + code() ErrorCode
  + currentStatus(ErrorCode, Enum~?~) ApiException
  + forField(ErrorCode, String, String) ApiException
  + details() List~Map~String, Object~~
}
class ApiResponse~T~ {
  + ApiResponse(T, Object) 
  + of(T, Object) ApiResponse~T~
  + data() T
  + of(T) ApiResponse~T~
  + meta() Object
  + page(Page~T~) ApiResponse~List~T~~
}
class AuditAction {
<<enumeration>>
  + AuditAction() 
  + valueOf(String) AuditAction
  + values() AuditAction[]
}
class AuditState {
  + AuditState(String, String, String, Boolean, String, String, List~String~, long) 
  - putIfPresent(Map~String, Object~, String, Object) void
  + banMode() String
  + hasPaidOrderBeforeBan() Boolean
  + roles() List~String~
  ~ asMap() Map~String, Object~
  + authzVersion() long
  + approvalStatus() String
  + userStatus() String
  + sellerStatus() String
  + customerStatus() String
  + roles(User, Collection~RoleCode~) AuditState
  + capture(User, Customer, Seller) AuditState
}
class AuditTargetType {
<<enumeration>>
  + AuditTargetType() 
  + values() AuditTargetType[]
  + valueOf(String) AuditTargetType
}
class AuditTrail {
  + AuditTrail(AdminAuditLogRepository) 
  + record(UUID, AuditAction, AuditTargetType, UUID, String, AuditState, AuditState) void
}
class AuthController {
  + AuthController(AuthService, PasswordService, RateLimiter, RateLimitProperties) 
  ~ registerSeller(RegisterSeller, HttpServletRequest) ApiResponse~UserSelfProfile~
  ~ login(Login) ApiResponse~AuthTokens~
  ~ forgotPassword(ForgotPassword, HttpServletRequest) ApiResponse~Map~String, String~~
  ~ register(Register, HttpServletRequest) ApiResponse~UserSelfProfile~
  ~ refresh(RefreshToken) ApiResponse~AuthTokens~
  ~ logout(Jwt, RefreshToken) void
  ~ resetPassword(ResetPassword) void
}
class AuthMethod {
<<enumeration>>
  + AuthMethod() 
  + values() AuthMethod[]
  + valueOf(String) AuthMethod
}
class AuthRequests {
  - AuthRequests() 
}
class AuthService {
  + AuthService(UserRepository, CustomerRepository, SellerRepository, RoleAssignments, SessionService, SelfProfileAssembler, OutboxWriter, PasswordEncoder) 
  - withProfile(IssuedTokens) AuthTokens
  + registerSeller(RegisterSeller) UserSelfProfile
  + logout(Caller, String) void
  + login(Login) AuthTokens
  + register(Register) UserSelfProfile
  + refresh(String) AuthTokens
  - saveNewUser(User) User
}
class AuthSession {
  # AuthSession() 
  ~ AuthSession(UUID, String, Instant) 
  - Instant expiresAt
  - Instant createdAt
  - String refreshTokenHash
  - UUID userId
  - UUID sessionId
  - Instant revokedAt
  ~ canRefresh(Instant) boolean
  ~ rotate(String, Instant) void
  ~ revoke(Instant) void
   String refreshTokenHash
   UUID userId
   boolean revoked
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
class AuthTokens {
  + AuthTokens(String, long, String, String, UserSelfProfile) 
  + accessTokenExpiresIn() long
  + refreshToken() String
  + profile() UserSelfProfile
  + accessToken() String
  ~ of(IssuedTokens, UserSelfProfile) AuthTokens
  + tokenType() String
}
class AuthorizationContext {
  + AuthorizationContext(UUID, UserStatus, long, List~String~, List~String~, CustomerAccess, SellerAccess, boolean) 
  + sessionActive() boolean
  + userId() UUID
  + customerProfile() CustomerAccess
  + authzVersion() long
  + permissions() List~String~
  + sellerProfile() SellerAccess
  + userStatus() UserStatus
  + roles() List~String~
}
class AuthorizationContextService {
  ~ AuthorizationContextService(RSAKey, TokenProperties, Clock, UserRepository, AuthSessionRepository, CustomerRepository, SellerRepository, PermissionResolver) 
  - uuid(String) UUID
  + of(String) AuthorizationContext
  - decode(String) Jwt
}
class BanRequest {
  ~ BanRequest(ModerationScope, String) 
  + scope() ModerationScope
  + reason() String
}
class BanResult {
  + BanResult(UUID, ModerationScope, UserStatus, CustomerStatus, SellerStatus, CustomerBanMode, long) 
  + authzVersion() long
  + banMode() CustomerBanMode
  + scope() ModerationScope
  ~ of(Target, ModerationScope) BanResult
  + userStatus() UserStatus
  + userId() UUID
  + customerStatus() CustomerStatus
  + sellerStatus() SellerStatus
}
class Body {
  + Body(String, String, List~Map~String, Object~~, String) 
  + code() String
  + message() String
  + requestId() String
  + details() List~Map~String, Object~~
}
class Caller {
  + Caller(UUID, UUID) 
  + userId() UUID
  + sessionId() UUID
  + of(Jwt) Caller
}
class ChangePassword {
  + ChangePassword(String, String, String) 
  + confirmPassword() String
  + currentPassword() String
  + newPassword() String
}
class CheckoutEligibility {
  + CheckoutEligibility(UUID, UserStatus, CustomerStatus, boolean, boolean, long, boolean, long) 
  + customerStatus() CustomerStatus
  + paymentBlocked() boolean
  + authzVersion() long
  + canCheckout() boolean
  + userStatus() UserStatus
  + userId() UUID
  + hasPhone() boolean
  + addressCount() long
}
class ClockConfig {
  + ClockConfig() 
  ~ clock() Clock
}
class Conflict {
  - Conflict(ErrorCode, String) 
  + code() ErrorCode
  + field() String
}
class Contact {
  + Contact(UUID, String, String) 
  + userId() UUID
  + email() String
  + fullName() String
}
class Context {
<<enumeration>>
  + Context() 
  + valueOf(String) Context
  + values() Context[]
}
class Customer {
  # Customer() 
  + Customer(User) 
  - Boolean hasPaidOrderBeforeBan
  - Instant updatedAt
  - User user
  - CustomerStatus status
  - CustomerBanMode banMode
  - Instant createdAt
  - String banReason
  - UUID customerId
  + unban() void
  + ban(String, boolean) void
   CustomerBanMode banMode
   String banReason
   Boolean hasPaidOrderBeforeBan
   UUID customerId
   Instant updatedAt
   CustomerStatus status
   User user
   Instant createdAt
   boolean banned
}
class CustomerAccess {
  + CustomerAccess(CustomerStatus, AccessMode, boolean) 
  + status() CustomerStatus
  + accessMode() AccessMode
  + paymentBlocked() boolean
}
class CustomerAddress {
  # CustomerAddress() 
  + CustomerAddress(UUID, String, String, String, String, String) 
  - UUID addressId
  - String addressDetail
  - String provinceCode
  - Instant lastUsedAt
  - UUID customerId
  - String recipientPhone
  - String wardCode
  - Instant updatedAt
  - Instant createdAt
  - Instant deletedAt
  - String recipientName
  + mustBeKept() boolean
  + markUsed(Instant) void
  + markDeleted(Instant) void
  + update(String, String, String, String, String) void
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
  + countByCustomerIdAndDeletedAtIsNull(UUID) long
  + findBook(UUID) List~CustomerAddress~
  + findByAddressIdAndCustomerIdAndDeletedAtIsNull(UUID, UUID) Optional~CustomerAddress~
  + recordTouch(UUID, String) int
}
class CustomerAdminView {
  + CustomerAdminView(UserSummary, CustomerProfile, Boolean, String) 
  + user() UserSummary
  + customerProfile() CustomerProfile
  + hasPaidOrder() Boolean
  + banReason() String
}
class CustomerBanMode {
<<enumeration>>
  + CustomerBanMode() 
  + valueOf(String) CustomerBanMode
  + values() CustomerBanMode[]
}
class CustomerProfile {
  + CustomerProfile(UUID, CustomerStatus, String, boolean, AccessMode, boolean, Instant) 
  + paymentBlocked() boolean
  ~ of(Customer, boolean) CustomerProfile
  + createdAt() Instant
  + customerId() UUID
  + banReason() String
  + status() CustomerStatus
  + accessModeOf(Customer) AccessMode
  + accessMode() AccessMode
  + profileComplete() boolean
}
class CustomerRepository {
<<Interface>>
  + findForAdmin(CustomerStatus, String, Pageable) Page~Customer~
  + findLockedById(UUID) Optional~Customer~
}
class CustomerStatus {
<<enumeration>>
  + CustomerStatus() 
  + valueOf(String) CustomerStatus
  + values() CustomerStatus[]
}
class Envelope {
  ~ Envelope(PaidOrder) 
  + data() PaidOrder
}
class ErrorCode {
<<enumeration>>
  - ErrorCode(HttpStatus, String) 
  + status() HttpStatus
  + values() ErrorCode[]
  + defaultMessage() String
  + valueOf(String) ErrorCode
}
class ErrorCodes {
  + value() ErrorCode[]
}
class ErrorResponse {
  + ErrorResponse(Body) 
  + of(ErrorCode, String, List~Map~String, Object~~, String) ErrorResponse
  + error() Body
}
class EventActor {
  + EventActor(UUID, Kind, Context) 
  + userId() UUID
  + context() Context
  + user(UUID, Context) EventActor
  + kind() Kind
}
class EventPublisher {
<<Interface>>
  + publish(String, Map~String, Object~) void
   boolean configured
}
class FileSpec {
  ~ FileSpec(String, String, long, String) 
  + sizeBytes() long
  + mimeType() String
  + purpose() String
  + fileName() String
}
class ForgotPassword {
  + ForgotPassword(String) 
  + email() String
}
class GlobalExceptionHandler {
  + GlobalExceptionHandler() 
  ~ handleApiException(ApiException, WebRequest) ResponseEntity~Object~
  # handleMethodArgumentNotValid(MethodArgumentNotValidException, HttpHeaders, HttpStatusCode, WebRequest) ResponseEntity~Object~?
  - respond(ErrorCode, String, List~Map~String, Object~~, HttpHeaders, HttpStatusCode, WebRequest) ResponseEntity~Object~
  ~ handleUnexpected(Exception, WebRequest) ResponseEntity~Object~
  - toUpperSnake(String) String
  ~ handleAccessDenied(AccessDeniedException, WebRequest) ResponseEntity~Object~
  - codeFor(HttpStatusCode) ErrorCode
  - fieldDetail(FieldError) Map~String, Object~
  # handleExceptionInternal(Exception, Object, HttpHeaders, HttpStatusCode, WebRequest) ResponseEntity~Object~?
}
class HelloController {
  + HelloController() 
  + hello() String
}
class HttpOrderClient {
  ~ HttpOrderClient(OrderProperties) 
  + hasPaidOrder(UUID) boolean
}
class IdentityServiceApplication {
  + IdentityServiceApplication() 
  + main(String[]) void
}
class InternalClientProperties {
  + InternalClientProperties(Map~String, String~) 
  + clients() Map~String, String~
}
class InternalController {
  + InternalController(AuthorizationContextService, InternalQueries) 
  ~ address(UUID, UUID) ApiResponse~AddressSnapshot~
  ~ contact(UUID) ApiResponse~Contact~
  ~ checkoutEligibility(UUID) ApiResponse~CheckoutEligibility~
  ~ authorizationContext(String) ApiResponse~AuthorizationContext~
  ~ sellerStatuses(List~UUID~) ApiResponse~List~SellerStatusView~~
  ~ touch(UUID, UUID, TouchRequest) ApiResponse~AddressTouch~
}
class InternalQueries {
  + InternalQueries(UserRepository, CustomerRepository, CustomerAddressRepository, SellerRepository, UserRoleRepository, PermissionResolver, RegionCatalog, Clock) 
  + sellerStatuses(List~UUID~) List~SellerStatusView~
  + touch(UUID, UUID, String) AddressTouch
  + contact(UUID) Contact
  - ownedAddress(UUID, UUID) CustomerAddress
  + checkoutEligibility(UUID) CheckoutEligibility
  + address(UUID, UUID) AddressSnapshot
}
class InternalSecurityConfig {
  + InternalSecurityConfig() 
  ~ internalSecurity(HttpSecurity, ServiceCredentials, SecurityErrorHandler) SecurityFilterChain
}
class IssuedTokens {
  + IssuedTokens(UUID, UUID, String, long, String) 
  + accessToken() String
  + refreshToken() String
  + sessionId() UUID
  + accessTokenExpiresIn() long
  + userId() UUID
}
class JwksController {
  + JwksController(RSAKey) 
  ~ jwks() Map~String, Object~
}
class Kind {
<<enumeration>>
  + Kind() 
  + values() Kind[]
  + valueOf(String) Kind
}
class LikePattern {
  - LikePattern() 
  + contains(String) String
}
class Limit {
  + Limit(int, Duration) 
  + limit() int
  + window() Duration
}
class Login {
  + Login(String, String) 
  + email() String
  + password() String
}
class MediaConfig {
  + MediaConfig() 
  - credentials(MediaProperties) StaticCredentialsProvider
  ~ s3Presigner(MediaProperties) S3Presigner
  ~ s3Client(MediaProperties) S3Client
}
class MediaController {
  + MediaController(SellerAccountService, SellerLogos, MediaStorage) 
  ~ uploadUrls(Jwt, UploadRequest) ApiResponse~List~PresignedUpload~~
}
class MediaProperties {
  + MediaProperties(String, String, String, String, String, String, Duration, Duration, long) 
  + endpoint() String
  + bucket() String
  + accessKey() String
  + publicEndpoint() String
  + region() String
  + readUrlTtl() Duration
  + uploadUrlTtl() Duration
  + secretKey() String
  + sellerLogoMaxBytes() long
}
class MediaStorage {
  + MediaStorage(S3Client, S3Presigner, MediaProperties, Clock) 
  + readUrl(String) String
  - createBucket() void
  + inspect(String, int) Optional~StoredObject~
  + presignUpload(String, String, long) PresignedUpload
  - ensureBucket() void
  - unavailable(SdkException) ApiException
}
class Meta {
  + Meta(String, Instant) 
  + fetchedAt() Instant
  + sourceVersion() String
}
class ModerationScope {
<<enumeration>>
  + ModerationScope() 
  + valueOf(String) ModerationScope
  + values() ModerationScope[]
}
class ModerationService {
  + ModerationService(UserRepository, CustomerRepository, SellerRepository, PermissionResolver, SessionService, OrderClient, AuditTrail, OutboxWriter, TransactionTemplate) 
  - unchangedBan(Target, ModerationScope, String, String, Enum~?~) BanResult
  - customerEvent(Target) Map~String, Object~
  - record(UUID, AuditAction, AuditTargetType, Target, String, AuditState) void
  - applyUnban(UUID, UUID, ModerationScope, String) UnbanResult
  - sellerEvent(Target, String) Map~String, Object~
  + ban(UUID, UUID, ModerationScope, String) BanResult
  - applyBan(UUID, UUID, ModerationScope, String, Boolean) BanResult?
  - lock(UUID) Target
  - actor(UUID) EventActor
  - userEvent(Target, String) Map~String, Object~
  + unban(UUID, UUID, ModerationScope, String) UnbanResult
}
class Names {
  + Names(String, String) 
  + provinceName() String
  + wardName() String
}
class NewPasswords {
  - NewPasswords() 
  ~ check(String, String, String) void
}
class OpaqueTokens {
  - OpaqueTokens() 
  + hash(String) String
  + generate() String
}
class OpenApiConfig {
  + OpenApiConfig() 
  - preAuthorize(HandlerMethod) PreAuthorize
  ~ permissionsFromPreAuthorize() OperationCustomizer
  - appendDescription(Operation, String) void
  - quoted(PreAuthorize) List~String~
  - error(String) ApiResponse
  ~ identityOpenApi() OpenAPI
  - takesAuthorizationHeader(HandlerMethod) boolean
  - addErrors(Operation, HandlerMethod, boolean) void
}
class OpenApiVnRegionProvider {
  ~ OpenApiVnRegionProvider(RegionProperties) 
  - unknownProvince() ApiException
  + provinces() List~Province~
  - provinceCode(int) String
  - fetch(Supplier~T~) T
  - baseUrl() String
  + wards(String) List~Ward~
  - wardCode(int) String
}
class OrderClient {
<<Interface>>
  + hasPaidOrder(UUID) boolean
}
class OrderProperties {
  + OrderProperties(String, Duration) 
  + baseUrl() String
  + timeout() Duration
}
class OutboxEvent {
  # OutboxEvent() 
  ~ OutboxEvent(UUID, String, UUID, long, Map~String, Object~, Instant) 
  ~ String PENDING
  - long version
  - Map~String, Object~ payload
  - UUID eventId
  - String eventType
  - Instant publishedAt
  - UUID aggregateId
  - String status
  - Instant createdAt
  ~ markPublished(Instant, Map~String, Object~) void
   UUID aggregateId
   String eventType
   boolean PENDING
   String status
   long version
   UUID eventId
   Instant publishedAt
   Instant createdAt
   Map~String, Object~ payload
}
class OutboxEventRepository {
<<Interface>>
  + nextVersion(UUID) long
  + lockPending(int) List~OutboxEvent~
  + findByAggregateIdAndEventType(UUID, String) List~OutboxEvent~
}
class OutboxRelay {
  + OutboxRelay(OutboxEventRepository, EventPublisher, Clock) 
  + publishPending() int
  - withoutSecrets(OutboxEvent) Map~String, Object~
}
class OutboxRelayScheduling {
  + OutboxRelayScheduling(OutboxRelay) 
  ~ publishPending() void
}
class OutboxWriter {
  + OutboxWriter(OutboxEventRepository, Clock) 
  + write(String, UUID, EventActor, Map~String, Object~) UUID
}
class PageMeta {
  + PageMeta(int, int, long, int) 
  + size() int
  + totalItems() long
  + totalPages() int
  + page() int
  + from(Page~?~) PageMeta
}
class PaidOrder {
  ~ PaidOrder(Boolean) 
  + hasPaidOrder() Boolean
}
class PasswordConfig {
  + PasswordConfig() 
  ~ passwordEncoder() PasswordEncoder
}
class PasswordPolicy {
  - PasswordPolicy() 
  + isSatisfiedBy(String) boolean
}
class PasswordResetProperties {
  + PasswordResetProperties(String, Duration) 
  + ttl() Duration
  + linkBase() String
}
class PasswordResetToken {
  # PasswordResetToken() 
  ~ PasswordResetToken(UUID, String, Instant) 
  - UUID tokenId
  - UUID userId
  - String tokenHash
  - Instant expiresAt
  - Instant usedAt
  - Instant createdAt
  ~ isUsable(Instant) boolean
  ~ markUsed(Instant) void
   String tokenHash
   Instant expiresAt
   UUID tokenId
   Instant usedAt
   Instant createdAt
   UUID userId
}
class PasswordResetTokenRepository {
<<Interface>>
  + findByTokenHash(String) Optional~PasswordResetToken~
}
class PasswordService {
  + PasswordService(UserRepository, PasswordResetTokenRepository, SessionService, OutboxWriter, PasswordEncoder, PasswordResetProperties, Clock) 
  - sendResetLink(User) void
  + reset(ResetPassword) void
  + forgot(String) void
  + change(Caller, ChangePassword) void
}
class Permission {
  # Permission() 
  - Instant createdAt
  - String description
  - UUID permissionId
  - String code
   String description
   String code
   UUID permissionId
   Instant createdAt
}
class PermissionAuthentication {
  + PermissionAuthentication(UserRepository, PermissionResolver) 
  + authenticate(Jwt) JwtAuthenticationToken
}
class PermissionResolver {
  + PermissionResolver(UserRoleRepository) 
  + permissionsOf(User) SortedSet~String~
  + activeRolesOf(User) Set~RoleCode~
}
class Permissions {
  - Permissions() 
}
class PhoneNumbers {
  - PhoneNumbers() 
  + normalize(String) Optional~String~
}
class PresignedUpload {
  + PresignedUpload(String, String, Instant) 
  + objectKey() String
  + uploadUrl() String
  + expiresAt() Instant
}
class Province {
  + Province(String, String) 
  + code() String
  + name() String
}
class PublicSellerController {
  + PublicSellerController(SellerDirectory) 
  ~ get(UUID) ApiResponse~SellerPublic~
  ~ search(String, Pageable) ApiResponse~List~SellerPublic~~
}
class RateLimitProperties {
  + RateLimitProperties(Limit, Limit) 
  + register() Limit
  + forgotPassword() Limit
}
class RateLimiter {
  + RateLimiter(Clock) 
  + check(String, String, Limit) void
}
class RefreshToken {
  + RefreshToken(String) 
  + refreshToken() String
}
class RegionCatalog {
  + RegionCatalog(RegionProvider, RegionProperties, Clock) 
  + names(String, String) Optional~Names~
  - cached(String, Supplier~T~) Snapshot~T~
  - lookUpNames(String, String) Names
  + provinces() Snapshot~List~Province~~
  + requireNames(String, String) Names
  + wards(String) Snapshot~List~Ward~~
  + requireWardInProvince(String, String) void
}
class RegionController {
  + RegionController(RegionCatalog) 
  ~ wards(String) ApiResponse~List~Ward~~
  ~ provinces() ApiResponse~List~Province~~
}
class RegionProperties {
  + RegionProperties(String, String, Duration, Duration) 
  + timeout() Duration
  + configured() boolean
  + baseUrl() String
  + cacheTtl() Duration
  + datasetVersion() String
}
class RegionProvider {
<<Interface>>
  + provinces() List~Province~
  + wards(String) List~Ward~
}
class Register {
  + Register(String, String, String, String, String) 
  + password() String
  + phoneNumber() String
  + fullName() String
  + confirmPassword() String
  + email() String
}
class RegisterSeller {
  + RegisterSeller(String, String, String, String, String, String) 
  + sellerName() String
  + confirmPassword() String
  + contactPhone() String
  + email() String
  + password() String
  + fullName() String
}
class RejectRequest {
  ~ RejectRequest(String) 
  + reason() String
}
class RequestIdFilter {
  + RequestIdFilter() 
  # doFilterInternal(HttpServletRequest, HttpServletResponse, FilterChain) void
}
class ResetPassword {
  + ResetPassword(String, String, String) 
  + confirmPassword() String
  + token() String
  + newPassword() String
}
class Result {
  + Result(UserSelfProfile, boolean) 
  + profile() UserSelfProfile
  + created() boolean
}
class Result {
  + Result(SellerSelfProfile, boolean) 
  + profile() SellerSelfProfile
  + created() boolean
}
class Review {
  - Review(User, Seller, AuditState) 
  + user() User
  + before() AuditState
  + seller() Seller
}
class Role {
  # Role() 
  - String name
  - Instant createdAt
  - RoleCode code
  - UUID roleId
  - String description
  - Set~Permission~ permissions
   String name
   String description
   Set~Permission~ permissions
   RoleCode code
   UUID roleId
   Instant createdAt
}
class RoleAssignments {
  + RoleAssignments(RoleRepository, UserRoleRepository, UserRepository, OutboxWriter, AuditTrail) 
  + revoke(User, RoleCode) boolean
  - activeRoles(User) Set~RoleCode~
  + revoke(User, RoleCode, UUID) boolean
  + grant(User, RoleCode, UUID) boolean
  - withRole(Set~RoleCode~, RoleCode) Set~RoleCode~
  - changed(User, AuditAction, UUID, Set~RoleCode~, Set~RoleCode~) void
}
class RoleCode {
<<enumeration>>
  + RoleCode() 
  + valueOf(String) RoleCode
  + values() RoleCode[]
}
class RoleRepository {
<<Interface>>
  + findByCode(RoleCode) Optional~Role~
}
class RotatedRefreshToken {
  ~ RotatedRefreshToken(String, UUID, Instant) 
  # RotatedRefreshToken() 
  - UUID sessionId
  - String tokenHash
  - Instant rotatedAt
   String tokenHash
   Instant rotatedAt
   UUID sessionId
}
class RotatedRefreshTokenRepository {
<<Interface>>

}
class SecurityConfig {
  + SecurityConfig() 
  ~ apiSecurity(HttpSecurity, SecurityErrorHandler, PermissionAuthentication) SecurityFilterChain
}
class SecurityErrorHandler {
  + SecurityErrorHandler(JsonMapper) 
  + commence(HttpServletRequest, HttpServletResponse, AuthenticationException) void
  + handle(HttpServletRequest, HttpServletResponse, AccessDeniedException) void
  - codeFor(AuthenticationException) ErrorCode
  - write(HttpServletRequest, HttpServletResponse, ErrorCode) void
}
class SelfProfileAssembler {
  + SelfProfileAssembler(UserRepository, CustomerRepository, CustomerAddressRepository, SellerRepository, PermissionResolver, MediaStorage) 
  + customer(Customer) CustomerProfile
  + seller(Seller) SellerSelfProfile
  + of(UUID) UserSelfProfile
}
class Seller {
  # Seller() 
  + Seller(User, String, String, String) 
  - String description
  - String banReason
  - Instant updatedAt
  - String contactPhone
  - User user
  - SellerApprovalStatus approvalStatus
  - Instant reviewedAt
  - String logoObjectKey
  - Instant createdAt
  - Instant registeredAt
  - String sellerName
  - UUID sellerId
  - SellerStatus status
  - String rejectionReason
  - UUID reviewedByUserId
  + resubmit(String, String, String) void
  + unban() void
  + changeLogo(String) void
  + approve(UUID, Instant) void
  + canOperate() boolean
  + reject(UUID, String, Instant) void
  - requirePending() void
  + ban(String) void
  + changeContactPhone(String) void
  + changeDescription(String) void
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
   boolean pending
   String contactPhone
   String logoObjectKey
}
class SellerAccess {
  + SellerAccess(SellerApprovalStatus, SellerStatus, boolean) 
  + approvalStatus() SellerApprovalStatus
  + status() SellerStatus
  + canOperate() boolean
}
class SellerAccountService {
  + SellerAccountService(UserRepository, SellerRepository, SellerLogos, OutboxWriter, SelfProfileAssembler) 
  + updateProfile(Caller, Map~String, Object~) SellerSelfProfile
  + operatingSeller(Caller) Seller
  + application(Caller) Optional~SellerSelfProfile~
  - phone(StrictBody) String
  + submit(Caller, Map~String, Object~) Result
}
class SellerAdminView {
  + SellerAdminView(UserSummary, SellerSelfProfile, UUID) 
  + reviewedByUserId() UUID
  + user() UserSummary
  + sellerProfile() SellerSelfProfile
}
class SellerApplicationRequest {
  ~ SellerApplicationRequest(String, String, String) 
  + contactPhone() String
  + description() String
  + sellerName() String
}
class SellerApprovalStatus {
<<enumeration>>
  + SellerApprovalStatus() 
  + values() SellerApprovalStatus[]
  + valueOf(String) SellerApprovalStatus
}
class SellerDirectory {
  + SellerDirectory(SellerRepository, MediaStorage) 
  + search(String, Pageable) Page~SellerPublic~
  - view(Seller) SellerPublic
  + get(UUID) SellerPublic
}
class SellerLogos {
  + SellerLogos(MediaStorage, MediaProperties) 
  - prefix(UUID) String
  - startsWith(byte[], int, int[]) boolean
  + newObjectKey(UUID, String, String, long) String
  + requireUploadedLogo(UUID, String) void
  ~ isAllowedImage(byte[]) boolean
}
class SellerProfileController {
  + SellerProfileController(SellerAccountService) 
  ~ update(Jwt, Map~String, Object~) ApiResponse~SellerSelfProfile~
}
class SellerPublic {
  + SellerPublic(UUID, String, String, String) 
  + sellerId() UUID
  + logoUrl() String
  + description() String
  ~ of(Seller, String) SellerPublic
  + sellerName() String
}
class SellerRepository {
<<Interface>>
  + findPublicById(UUID) Optional~Seller~
  + findForAdmin(SellerApprovalStatus, SellerStatus, String, Pageable) Page~Seller~
  + findVisible(UUID, String, UserStatus, SellerApprovalStatus, SellerStatus, RoleCode, Pageable) Page~Seller~
  + findWithUserByIdIn(Collection~UUID~) List~Seller~
  + existsActiveContactPhone(String) boolean
  + existsActiveContactPhoneOfAnotherSeller(String, UUID) boolean
  + findPublic(String, Pageable) Page~Seller~
  + existsActiveSellerName(String) boolean
}
class SellerReviewService {
  + SellerReviewService(UserRepository, SellerRepository, CustomerRepository, RoleAssignments, AuditTrail, OutboxWriter, AdminViews, Clock) 
  - finish(UUID, Review, AuditAction, String, String) SellerAdminView
  - notFound() ApiException
  - capture(User, Seller) AuditState
  + approve(UUID, UUID) SellerAdminView
  + reject(UUID, UUID, String) SellerAdminView
  - start(UUID, UUID) Review
}
class SellerSelfProfile {
  + SellerSelfProfile(UUID, String, String, String, String, SellerApprovalStatus, SellerStatus, String, String, Instant, Instant) 
  + rejectionReason() String
  + reviewedAt() Instant
  + sellerId() UUID
  + status() SellerStatus
  ~ of(Seller, String) SellerSelfProfile
  + registeredAt() Instant
  + sellerName() String
  + contactPhone() String
  + approvalStatus() SellerApprovalStatus
  + logoUrl() String
  + description() String
  + banReason() String
}
class SellerStatus {
<<enumeration>>
  + SellerStatus() 
  + values() SellerStatus[]
  + valueOf(String) SellerStatus
}
class SellerStatusView {
  + SellerStatusView(UUID, UUID, UserStatus, SellerApprovalStatus, SellerStatus, boolean, boolean) 
  + sellerId() UUID
  + sellerRoleActive() boolean
  + status() SellerStatus
  + userId() UUID
  + approvalStatus() SellerApprovalStatus
  + userStatus() UserStatus
  + sellable() boolean
}
class ServiceCredentials {
  + ServiceCredentials(InternalClientProperties) 
  + serviceFor(String) Optional~String~
  - sha256(String) byte[]
}
class ServiceTokenFilter {
  ~ ServiceTokenFilter(ServiceCredentials) 
  # doFilterInternal(HttpServletRequest, HttpServletResponse, FilterChain) void
}
class SessionService {
  + SessionService(AuthSessionRepository, RotatedRefreshTokenRepository, UserRepository, AccessTokenIssuer, TokenProperties, Clock) 
  - tokens(User, AuthSession, String) IssuedTokens
  + start(User) IssuedTokens
  - revokeReused(AuthSession, Instant) void
  + refresh(String) IssuedTokens
  + revoke(UUID) void
  + revokeAllOf(UUID) void
  + logout(UUID, UUID, String) void
}
class SessionTokenValidator {
  + SessionTokenValidator(AuthSessionRepository, UserRepository) 
  - invalid(String) OAuth2TokenValidatorResult
  + validate(Jwt) OAuth2TokenValidatorResult
  - uuid(String) Optional~UUID~
}
class SigningKeyConfig {
  + SigningKeyConfig() 
  ~ jwtEncoder(RSAKey) JwtEncoder
  - parsePkcs8(String) RSAPrivateCrtKey
  ~ signingKey(TokenProperties) RSAKey
}
class Snapshot~T~ {
  + Snapshot(T, String, Instant) 
  + meta() Meta
  + sourceVersion() String
  + fetchedAt() Instant
  + items() T
}
class StoredObject {
  + StoredObject(long, byte[]) 
  + sizeBytes() long
  + header() byte[]
}
class StrictBody {
  - StrictBody(Map~String, Object~) 
  + has(String) boolean
  + nullableText(String, int) String?
  - invalid(String, String, String) ApiException
  + of(Map~String, Object~, Set~String~) StrictBody
  + text(String, int) String
  - detail(String, String, String) Map~String, Object~
}
class Target {
  - Target(User, Customer, Seller) 
  + user() User
  ~ state() AuditState
  + customer() Customer
  + seller() Seller
  ~ requireCustomer() Customer
  ~ requireSeller() Seller
}
class TokenClaims {
  - TokenClaims() 
}
class TokenProperties {
  + TokenProperties(String, String, Duration, Duration, String) 
  + issuer() String
  + accessTtl() Duration
  + refreshTtl() Duration
  + audience() String
  + signingKey() String
}
class TouchRequest {
  ~ TouchRequest(String) 
  + checkoutSessionId() String
}
class UnbanRequest {
  ~ UnbanRequest(ModerationScope, String) 
  + reason() String
  + scope() ModerationScope
}
class UnbanResult {
  + UnbanResult(UUID, ModerationScope, UserStatus, CustomerStatus, SellerStatus, long) 
  + sellerStatus() SellerStatus
  ~ of(Target, ModerationScope) UnbanResult
  + userId() UUID
  + authzVersion() long
  + userStatus() UserStatus
  + customerStatus() CustomerStatus
  + scope() ModerationScope
}
class UnconfiguredEventPublisher {
  ~ UnconfiguredEventPublisher() 
  + publish(String, Map~String, Object~) void
   boolean configured
}
class UniqueConflicts {
  - UniqueConflicts() 
  ~ translate(DataIntegrityViolationException) RuntimeException
}
class UpdateMeRequest {
  ~ UpdateMeRequest(String, String) 
  + phoneNumber() String
  + fullName() String
}
class UpdateShopRequest {
  ~ UpdateShopRequest(String, String, String) 
  + logoObjectKey() String
  + description() String
  + contactPhone() String
}
class UploadRequest {
  ~ UploadRequest(List~FileSpec~) 
  + files() List~FileSpec~
}
class UpstreamProvince {
  ~ UpstreamProvince(int, String, List~UpstreamWard~) 
  + code() int
  + wards() List~UpstreamWard~
  + name() String
}
class UpstreamWard {
  ~ UpstreamWard(int, String) 
  + name() String
  + code() int
}
class User {
  # User() 
  - String disabledReason
  - String googleSubject
  - String fullName
  - UUID userId
  - UserStatus status
  - long authzVersion
  - String phoneNumber
  - Instant createdAt
  - String email
  - String passwordHash
  - Instant updatedAt
  - AuthMethod authMethod
  + enable() void
  + normalizeEmail(String) String
  + local(String, String, String, String) User
  + changePhoneNumber(String) void
  + rename(String) void
  + bumpAuthzVersion() void
  + disable(String) void
  + changePassword(String) void
   String disabledReason
   long authzVersion
   AuthMethod authMethod
   boolean active
   Instant createdAt
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
  + findLockedById(UUID) Optional~User~
  + existsByEmail(String) boolean
  + existsByPhoneNumber(String) boolean
  + existsByPhoneNumberAndUserIdNot(String, UUID) boolean
  + findByEmail(String) Optional~User~
}
class UserRole {
  # UserRole() 
  + UserRole(UUID, Role, UUID) 
  - UserRoleId id
  - Instant revokedAt
  - UUID grantedByUserId
  - Role role
  - Instant grantedAt
  ~ revoke() void
  ~ regrant(UUID) void
   UUID grantedByUserId
   Role role
   UserRoleId id
   boolean active
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
  + wasRevoked(UUID, RoleCode) boolean
  + findHoldersOfActiveRole(Collection~UUID~, RoleCode) Set~UUID~
}
class UserSelfProfile {
  + UserSelfProfile(UUID, String, AuthMethod, String, String, UserStatus, List~String~, List~String~, long, CustomerProfile, SellerSelfProfile, Instant) 
  + phoneNumber() String
  + authzVersion() long
  + userId() UUID
  + sellerProfile() SellerSelfProfile
  + createdAt() Instant
  + userStatus() UserStatus
  + fullName() String
  + authMethod() AuthMethod
  + email() String
  + roles() List~String~
  + permissions() List~String~
  + customerProfile() CustomerProfile
}
class UserStatus {
<<enumeration>>
  + UserStatus() 
  + valueOf(String) UserStatus
  + values() UserStatus[]
}
class UserSummary {
  + UserSummary(UUID, String, String, String, UserStatus) 
  + userId() UUID
  ~ of(User) UserSummary
  + fullName() String
  + phoneNumber() String
  + email() String
  + userStatus() UserStatus
}
class Ward {
  + Ward(String, String, String) 
  + provinceCode() String
  + name() String
  + code() String
}
class Window {
  - Window(Instant, int) 
  ~ isOver(Instant) boolean
  + endsAt() Instant
  + count() int
}

CustomerProfile  -->  AccessMode 
AccessTokenIssuer "1" *--> "properties 1" TokenProperties 
AccountController "1" *--> "accounts 1" AccountService 
AccountController "1" *--> "passwords 1" PasswordService 
AccountController "1" *--> "sellerAccounts 1" SellerAccountService 
AccountService  ..>  ApiException : «create»
AccountService  ..>  Customer : «create»
AccountService "1" *--> "customers 1" CustomerRepository 
AccountService  ..>  Result : «create»
AccountService "1" *--> "roleAssignments 1" RoleAssignments 
AccountService "1" *--> "profiles 1" SelfProfileAssembler 
AccountService "1" *--> "users 1" UserRepository 
AccountService "1" *--> "userRoles 1" UserRoleRepository 
AddressController "1" *--> "addresses 1" AddressService 
AddressService  ..>  Address : «create»
AddressService "1" *--> "properties 1" AddressProperties 
AddressService  ..>  ApiException : «create»
AddressService  ..>  CustomerAddress : «create»
AddressService "1" *--> "addresses 1" CustomerAddressRepository 
AddressService "1" *--> "customers 1" CustomerRepository 
AddressService  ..>  Names : «create»
AddressService "1" *--> "regions 1" RegionCatalog 
InternalQueries  -->  AddressSnapshot 
InternalQueries  -->  AddressTouch 
AdminAuditLog "1" *--> "action 1" AuditAction 
AdminAuditLog "1" *--> "targetType 1" AuditTargetType 
AdminBootstrap  ..>  AdminAuditLog : «create»
AdminBootstrap "1" *--> "auditLog 1" AdminAuditLogRepository 
AdminBootstrap "1" *--> "properties 1" AdminBootstrapProperties 
AdminBootstrap "1" *--> "roleAssignments 1" RoleAssignments 
AdminBootstrap "1" *--> "users 1" UserRepository 
AdminController "1" *--> "directory 1" AdminDirectory 
AdminController "1" *--> "moderation 1" ModerationService 
AdminController "1" *--> "reviews 1" SellerReviewService 
AdminDirectory "1" *--> "views 1" AdminViews 
AdminDirectory  ..>  ApiException : «create»
AdminDirectory "1" *--> "customers 1" CustomerRepository 
AdminDirectory "1" *--> "sellers 1" SellerRepository 
AdminViews  ..>  CustomerAdminView : «create»
AdminViews "1" *--> "profiles 1" SelfProfileAssembler 
AdminViews  ..>  SellerAdminView : «create»
ApiException "1" *--> "code 1" ErrorCode 
AuditTrail  ..>  AdminAuditLog : «create»
AuditTrail "1" *--> "log 1" AdminAuditLogRepository 
AuthController "1" *--> "auth 1" AuthService 
AuthController "1" *--> "passwords 1" PasswordService 
AuthController "1" *--> "limits 1" RateLimitProperties 
AuthController "1" *--> "rateLimiter 1" RateLimiter 
AuthService  ..>  ApiException : «create»
AuthService  ..>  Customer : «create»
AuthService "1" *--> "customers 1" CustomerRepository 
AuthService "1" *--> "outbox 1" OutboxWriter 
AuthService "1" *--> "roleAssignments 1" RoleAssignments 
AuthService "1" *--> "profiles 1" SelfProfileAssembler 
AuthService  ..>  Seller : «create»
AuthService "1" *--> "sellers 1" SellerRepository 
AuthService "1" *--> "sessions 1" SessionService 
AuthService "1" *--> "users 1" UserRepository 
AuthorizationContextService  -->  AuthorizationContext 
AuthorizationContextService  ..>  ApiException : «create»
AuthorizationContextService "1" *--> "sessions 1" AuthSessionRepository 
AuthorizationContextService  ..>  AuthorizationContext : «create»
AuthorizationContextService  ..>  CustomerAccess : «create»
AuthorizationContextService "1" *--> "customers 1" CustomerRepository 
AuthorizationContextService "1" *--> "permissions 1" PermissionResolver 
AuthorizationContextService  ..>  SellerAccess : «create»
AuthorizationContextService "1" *--> "sellers 1" SellerRepository 
AuthorizationContextService "1" *--> "users 1" UserRepository 
AdminController  -->  BanRequest 
BanRequest "1" *--> "scope 1" ModerationScope 
ModerationService  -->  BanResult 
ErrorResponse  -->  Body 
AuthRequests  -->  ChangePassword 
InternalQueries  -->  CheckoutEligibility 
Conflict "1" *--> "code 1" ErrorCode 
UniqueConflicts  -->  Conflict 
InternalQueries  -->  Contact 
EventActor  -->  Context 
Customer "1" *--> "banMode 1" CustomerBanMode 
Customer "1" *--> "status 1" CustomerStatus 
Customer "1" *--> "user 1" User 
AuthorizationContextService  -->  CustomerAccess 
CustomerAccess "1" *--> "status 1" CustomerStatus 
AdminViews  -->  CustomerAdminView 
CustomerAdminView "1" *--> "user 1" UserSummary 
HttpOrderClient  -->  Envelope 
Envelope "1" *--> "data 1" PaidOrder 
ErrorResponse  ..>  Body : «create»
ErrorResponse "1" *--> "error 1" Body 
MediaController  -->  FileSpec 
AuthRequests  -->  ForgotPassword 
HttpOrderClient  ..>  ApiException : «create»
HttpOrderClient  ..>  OrderClient 
HttpOrderClient "1" *--> "properties 1" OrderProperties 
InternalController "1" *--> "authorizationContexts 1" AuthorizationContextService 
InternalController "1" *--> "queries 1" InternalQueries 
InternalQueries  ..>  AddressSnapshot : «create»
InternalQueries  ..>  AddressTouch : «create»
InternalQueries  ..>  ApiException : «create»
InternalQueries  ..>  CheckoutEligibility : «create»
InternalQueries  ..>  Contact : «create»
InternalQueries "1" *--> "addresses 1" CustomerAddressRepository 
InternalQueries "1" *--> "customers 1" CustomerRepository 
InternalQueries "1" *--> "permissions 1" PermissionResolver 
InternalQueries "1" *--> "regions 1" RegionCatalog 
InternalQueries "1" *--> "sellers 1" SellerRepository 
InternalQueries  ..>  SellerStatusView : «create»
InternalQueries "1" *--> "users 1" UserRepository 
InternalQueries "1" *--> "userRoles 1" UserRoleRepository 
InternalSecurityConfig  ..>  ServiceTokenFilter : «create»
EventActor  -->  Kind 
RateLimitProperties  -->  Limit 
AuthRequests  -->  Login 
MediaController "1" *--> "storage 1" MediaStorage 
MediaController "1" *--> "sellerAccounts 1" SellerAccountService 
MediaController "1" *--> "logos 1" SellerLogos 
MediaStorage  ..>  ApiException : «create»
MediaStorage "1" *--> "properties 1" MediaProperties 
MediaStorage  ..>  PresignedUpload : «create»
MediaStorage  ..>  StoredObject : «create»
RegionCatalog  -->  Meta 
ModerationService  ..>  ApiException : «create»
ModerationService "1" *--> "audit 1" AuditTrail 
ModerationService "1" *--> "customers 1" CustomerRepository 
ModerationService "1" *--> "orders 1" OrderClient 
ModerationService "1" *--> "outbox 1" OutboxWriter 
ModerationService "1" *--> "permissions 1" PermissionResolver 
ModerationService "1" *--> "sellers 1" SellerRepository 
ModerationService "1" *--> "sessions 1" SessionService 
ModerationService  ..>  Target : «create»
ModerationService "1" *--> "users 1" UserRepository 
RegionCatalog  -->  Names 
OpenApiVnRegionProvider  ..>  ApiException : «create»
OpenApiVnRegionProvider  ..>  Province : «create»
OpenApiVnRegionProvider "1" *--> "properties 1" RegionProperties 
OpenApiVnRegionProvider  ..>  RegionProvider 
OpenApiVnRegionProvider  ..>  Ward : «create»
OutboxRelay "1" *--> "publisher 1" EventPublisher 
OutboxRelay "1" *--> "events 1" OutboxEventRepository 
OutboxRelayScheduling "1" *--> "relay 1" OutboxRelay 
OutboxWriter  ..>  OutboxEvent : «create»
OutboxWriter "1" *--> "events 1" OutboxEventRepository 
HttpOrderClient  -->  PaidOrder 
PasswordService  ..>  ApiException : «create»
PasswordService  ..>  EventActor : «create»
PasswordService "1" *--> "outbox 1" OutboxWriter 
PasswordService "1" *--> "properties 1" PasswordResetProperties 
PasswordService  ..>  PasswordResetToken : «create»
PasswordService "1" *--> "resetTokens 1" PasswordResetTokenRepository 
PasswordService "1" *--> "sessions 1" SessionService 
PasswordService "1" *--> "users 1" UserRepository 
PermissionAuthentication "1" *--> "permissions 1" PermissionResolver 
PermissionAuthentication "1" *--> "users 1" UserRepository 
PermissionResolver "1" *--> "userRoles 1" UserRoleRepository 
MediaStorage  -->  PresignedUpload 
PublicSellerController "1" *--> "directory 1" SellerDirectory 
RateLimitProperties "1" *--> "register 1" Limit 
RateLimiter  ..>  ApiException : «create»
RateLimiter  ..>  Window : «create»
RateLimiter "1" *--> "windows *" Window 
AuthRequests  -->  RefreshToken 
RegionCatalog  ..>  Names : «create»
RegionCatalog "1" *--> "properties 1" RegionProperties 
RegionCatalog "1" *--> "provider 1" RegionProvider 
RegionCatalog  ..>  Snapshot~T~ : «create»
RegionCatalog "1" *--> "cache *" Snapshot~T~ 
RegionController "1" *--> "regions 1" RegionCatalog 
AuthRequests  -->  Register 
AuthRequests  -->  RegisterSeller 
AdminController  -->  RejectRequest 
AuthRequests  -->  ResetPassword 
AccountService  -->  Result 
SellerAccountService  -->  Result 
Result "1" *--> "profile 1" SellerSelfProfile 
Result "1" *--> "profile 1" UserSelfProfile 
SellerReviewService  -->  Review 
Review "1" *--> "user 1" User 
Role "1" *--> "permissions *" Permission 
Role "1" *--> "code 1" RoleCode 
RoleAssignments "1" *--> "audit 1" AuditTrail 
RoleAssignments "1" *--> "outbox 1" OutboxWriter 
RoleAssignments "1" *--> "roles 1" RoleRepository 
RoleAssignments "1" *--> "users 1" UserRepository 
RoleAssignments  ..>  UserRole : «create»
RoleAssignments  ..>  UserRoleId : «create»
RoleAssignments "1" *--> "userRoles 1" UserRoleRepository 
SelfProfileAssembler "1" *--> "addresses 1" CustomerAddressRepository 
SelfProfileAssembler "1" *--> "customers 1" CustomerRepository 
SelfProfileAssembler "1" *--> "media 1" MediaStorage 
SelfProfileAssembler "1" *--> "permissions 1" PermissionResolver 
SelfProfileAssembler "1" *--> "sellers 1" SellerRepository 
SelfProfileAssembler "1" *--> "users 1" UserRepository 
SelfProfileAssembler  ..>  UserSelfProfile : «create»
Seller "1" *--> "approvalStatus 1" SellerApprovalStatus 
Seller "1" *--> "status 1" SellerStatus 
Seller "1" *--> "user 1" User 
AuthorizationContextService  -->  SellerAccess 
SellerAccess "1" *--> "approvalStatus 1" SellerApprovalStatus 
SellerAccountService  ..>  ApiException : «create»
SellerAccountService "1" *--> "outbox 1" OutboxWriter 
SellerAccountService  ..>  Result : «create»
SellerAccountService "1" *--> "profiles 1" SelfProfileAssembler 
SellerAccountService  ..>  Seller : «create»
SellerAccountService "1" *--> "logos 1" SellerLogos 
SellerAccountService "1" *--> "sellers 1" SellerRepository 
SellerAccountService "1" *--> "users 1" UserRepository 
AdminViews  -->  SellerAdminView 
SellerAdminView "1" *--> "user 1" UserSummary 
AccountController  -->  SellerApplicationRequest 
SellerDirectory  ..>  ApiException : «create»
SellerDirectory "1" *--> "media 1" MediaStorage 
SellerDirectory "1" *--> "sellers 1" SellerRepository 
SellerLogos "1" *--> "properties 1" MediaProperties 
SellerLogos "1" *--> "storage 1" MediaStorage 
SellerProfileController "1" *--> "sellerAccounts 1" SellerAccountService 
SellerReviewService "1" *--> "views 1" AdminViews 
SellerReviewService  ..>  ApiException : «create»
SellerReviewService "1" *--> "audit 1" AuditTrail 
SellerReviewService "1" *--> "customers 1" CustomerRepository 
SellerReviewService "1" *--> "outbox 1" OutboxWriter 
SellerReviewService  ..>  Review : «create»
SellerReviewService "1" *--> "roleAssignments 1" RoleAssignments 
SellerReviewService "1" *--> "sellers 1" SellerRepository 
SellerReviewService "1" *--> "users 1" UserRepository 
InternalQueries  -->  SellerStatusView 
ServiceTokenFilter "1" *--> "credentials 1" ServiceCredentials 
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
Snapshot~T~  ..>  Meta : «create»
RegionCatalog  -->  Snapshot~T~ 
MediaStorage  -->  StoredObject 
StrictBody  ..>  ApiException : «create»
Target  ..>  ApiException : «create»
ModerationService  -->  Target 
Target "1" *--> "user 1" User 
InternalController  -->  TouchRequest 
AdminController  -->  UnbanRequest 
UnbanRequest "1" *--> "scope 1" ModerationScope 
ModerationService  -->  UnbanResult 
UnconfiguredEventPublisher  ..>  EventPublisher 
UniqueConflicts "1" *--> "BY_CONSTRAINT *" Conflict 
UniqueConflicts  ..>  Conflict : «create»
AccountController  -->  UpdateMeRequest 
SellerProfileController  -->  UpdateShopRequest 
UploadRequest "1" *--> "files *" FileSpec 
MediaController  -->  UploadRequest 
OpenApiVnRegionProvider  -->  UpstreamProvince 
OpenApiVnRegionProvider  -->  UpstreamWard 
User "1" *--> "authMethod 1" AuthMethod 
User "1" *--> "status 1" UserStatus 
UserRole "1" *--> "role 1" Role 
UserRole "1" *--> "id 1" UserRoleId 
UserRole  ..>  UserRoleId : «create»
AdminViews  -->  UserSummary 
RateLimiter  -->  Window 
