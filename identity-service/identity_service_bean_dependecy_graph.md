classDiagram

direction LR

class node77

class accessTokenDecoderConfig

class accessTokenIssuer

class accountController

class accountService

class addressController

class addressService

class adminAuditLogRepository

class adminBootstrap

class apiSecurity

class authController

class authService

class authSessionRepository

class clock

class clockConfig

class configurationPropertiesScanRegistrar

class customerAddressRepository

class customerRepository

class enableConfigurationPropertiesRegistrar

class enableDiscoveryClientImportSelector

class environment

class fakeRegionProvider

class node11

class globalExceptionHandler

class helloController

class node7

class node32

class node27

class node21

class node43

class node73

class identityServiceApplication

class importAutoConfigurationImportSelector

class jacksonJsonMapper

class jwksController

class jwtDecoder

class jwtEncoder

class mediaConfig

class mediaController

class mediaStorage

class methodSecuritySelector

class mutableClock

class openSecurity

class outboxEventRepository

class outboxWriter

class passwordConfig

class passwordEncoder

class passwordResetTokenRepository

class passwordService

class permissionAuthentication

class permissionResolver

class postgresContainer

class rateLimiter

class regionCatalog

class requestIdFilter

class roleAssignments

class roleRepository

class rotatedRefreshTokenRepository

class s3Client

class s3Presigner

class seaweedfs

class seaweedfsProperties

class seaweedfsTestConfiguration

class securityConfig

class securityErrorHandler

class selfProfileAssembler

class sellerAccountService

class sellerLogos

class sellerProfileController

class sellerRepository

class sessionService

class sessionTokenValidator

class signingKey

class signingKeyConfig

class testClockConfiguration

class testcontainersConfiguration

class unconfiguredRegionProvider

class userRepository

class userRoleRepository

class webApplicationContext

class node60

class node24

accessTokenDecoderConfig  ..&gt;  jwtDecoder : @Bean

accessTokenIssuer  --&gt;  clock : depends on

accessTokenIssuer  --&gt;  node73 : depends on

accessTokenIssuer  --&gt;  jwtEncoder : depends on

accountController  --&gt;  accountService : depends on

accountController  --&gt;  passwordService : depends on

accountController  --&gt;  sellerAccountService : depends on

accountService  --&gt;  customerRepository : depends on

accountService  --&gt;  roleAssignments : depends on

accountService  --&gt;  selfProfileAssembler : depends on

accountService  --&gt;  userRepository : depends on

accountService  --&gt;  userRoleRepository : depends on

addressController  --&gt;  addressService : depends on

addressService  --&gt;  clock : depends on

addressService  --&gt;  customerAddressRepository : depends on

addressService  --&gt;  customerRepository : depends on

addressService  --&gt;  node7 : depends on

addressService  --&gt;  regionCatalog : depends on

adminBootstrap  --&gt;  adminAuditLogRepository : depends on

adminBootstrap  --&gt;  node32 : depends on

adminBootstrap  --&gt;  passwordEncoder : depends on

adminBootstrap  --&gt;  roleAssignments : depends on

adminBootstrap  --&gt;  userRepository : depends on

authController  --&gt;  authService : depends on

authController  --&gt;  node43 : depends on

authController  --&gt;  passwordService : depends on

authController  --&gt;  rateLimiter : depends on

authService  --&gt;  customerRepository : depends on

authService  --&gt;  outboxWriter : depends on

authService  --&gt;  passwordEncoder : depends on

authService  --&gt;  roleAssignments : depends on

authService  --&gt;  selfProfileAssembler : depends on

authService  --&gt;  sellerRepository : depends on

authService  --&gt;  sessionService : depends on

authService  --&gt;  userRepository : depends on

clockConfig  ..&gt;  clock : @Bean

configurationPropertiesScanRegistrar  --&gt;  environment : depends on

configurationPropertiesScanRegistrar  --&gt;  webApplicationContext : depends on

node11  ..&gt;  fakeRegionProvider : @Bean

identityServiceApplication  ..&gt;  node77 

identityServiceApplication  ..&gt;  accessTokenDecoderConfig 

identityServiceApplication  ..&gt;  accessTokenIssuer 

identityServiceApplication  ..&gt;  accountController 

identityServiceApplication  ..&gt;  accountService 

identityServiceApplication  ..&gt;  addressController 

identityServiceApplication  ..&gt;  addressService 

identityServiceApplication  ..&gt;  adminAuditLogRepository 

identityServiceApplication  ..&gt;  adminBootstrap 

identityServiceApplication  ..&gt;  authController 

identityServiceApplication  ..&gt;  authService 

identityServiceApplication  ..&gt;  authSessionRepository 

identityServiceApplication  ..&gt;  clockConfig 

identityServiceApplication  --&gt;  configurationPropertiesScanRegistrar : @ConfigurationPropertiesScan

identityServiceApplication  ..&gt;  customerAddressRepository 

identityServiceApplication  ..&gt;  customerRepository 

identityServiceApplication  --&gt;  enableConfigurationPropertiesRegistrar : @ConfigurationPropertiesScan

identityServiceApplication  --&gt;  enableDiscoveryClientImportSelector : @EnableDiscoveryClient

identityServiceApplication  ..&gt;  node11 

identityServiceApplication  ..&gt;  globalExceptionHandler 

identityServiceApplication  ..&gt;  helloController 

identityServiceApplication  ..&gt;  node7 

identityServiceApplication  ..&gt;  node32 

identityServiceApplication  ..&gt;  node27 

identityServiceApplication  ..&gt;  node21 

identityServiceApplication  ..&gt;  node43 

identityServiceApplication  ..&gt;  node73 

identityServiceApplication  ..&gt;  identityServiceApplication 

identityServiceApplication  --&gt;  importAutoConfigurationImportSelector : @SpringBootApplication

identityServiceApplication  ..&gt;  jwksController 

identityServiceApplication  ..&gt;  mediaConfig 

identityServiceApplication  ..&gt;  mediaController 

identityServiceApplication  ..&gt;  mediaStorage 

identityServiceApplication  ..&gt;  outboxEventRepository 

identityServiceApplication  ..&gt;  outboxWriter 

identityServiceApplication  ..&gt;  passwordConfig 

identityServiceApplication  ..&gt;  passwordResetTokenRepository 

identityServiceApplication  ..&gt;  passwordService 

identityServiceApplication  ..&gt;  permissionAuthentication 

identityServiceApplication  ..&gt;  permissionResolver 

identityServiceApplication  ..&gt;  rateLimiter 

identityServiceApplication  ..&gt;  regionCatalog 

identityServiceApplication  ..&gt;  requestIdFilter 

identityServiceApplication  ..&gt;  roleAssignments 

identityServiceApplication  ..&gt;  roleRepository 

identityServiceApplication  ..&gt;  rotatedRefreshTokenRepository 

identityServiceApplication  ..&gt;  seaweedfsTestConfiguration 

identityServiceApplication  ..&gt;  securityConfig 

identityServiceApplication  ..&gt;  securityErrorHandler 

identityServiceApplication  ..&gt;  selfProfileAssembler 

identityServiceApplication  ..&gt;  sellerAccountService 

identityServiceApplication  ..&gt;  sellerLogos 

identityServiceApplication  ..&gt;  sellerProfileController 

identityServiceApplication  ..&gt;  sellerRepository 

identityServiceApplication  ..&gt;  sessionService 

identityServiceApplication  ..&gt;  sessionTokenValidator 

identityServiceApplication  ..&gt;  signingKeyConfig 

identityServiceApplication  ..&gt;  testClockConfiguration 

identityServiceApplication  ..&gt;  testcontainersConfiguration 

identityServiceApplication  ..&gt;  unconfiguredRegionProvider 

identityServiceApplication  ..&gt;  userRepository 

identityServiceApplication  ..&gt;  userRoleRepository 

identityServiceApplication  ..&gt;  node60 

identityServiceApplication  ..&gt;  node24 

jwksController  --&gt;  signingKey : depends on

mediaConfig  ..&gt;  s3Client : @Bean

mediaConfig  ..&gt;  s3Presigner : @Bean

mediaController  --&gt;  mediaStorage : depends on

mediaController  --&gt;  sellerAccountService : depends on

mediaController  --&gt;  sellerLogos : depends on

mediaStorage  --&gt;  clock : depends on

mediaStorage  --&gt;  node27 : depends on

mediaStorage  --&gt;  s3Client : depends on

mediaStorage  --&gt;  s3Presigner : depends on

outboxWriter  --&gt;  clock : depends on

outboxWriter  --&gt;  outboxEventRepository : depends on

passwordConfig  ..&gt;  passwordEncoder : @Bean

passwordService  --&gt;  clock : depends on

passwordService  --&gt;  node21 : depends on

passwordService  --&gt;  outboxWriter : depends on

passwordService  --&gt;  passwordEncoder : depends on

passwordService  --&gt;  passwordResetTokenRepository : depends on

passwordService  --&gt;  sessionService : depends on

passwordService  --&gt;  userRepository : depends on

permissionAuthentication  --&gt;  permissionResolver : depends on

permissionAuthentication  --&gt;  userRepository : depends on

permissionResolver  --&gt;  userRoleRepository : depends on

rateLimiter  --&gt;  clock : depends on

regionCatalog  --&gt;  unconfiguredRegionProvider : depends on

roleAssignments  --&gt;  roleRepository : depends on

roleAssignments  --&gt;  userRepository : depends on

roleAssignments  --&gt;  userRoleRepository : depends on

seaweedfsTestConfiguration  ..&gt;  seaweedfs : @Bean

seaweedfsTestConfiguration  ..&gt;  seaweedfsProperties : @Bean

securityConfig  ..&gt;  apiSecurity : @Bean

securityConfig  --&gt;  methodSecuritySelector : @EnableMethodSecurity

securityErrorHandler  --&gt;  jacksonJsonMapper : depends on

selfProfileAssembler  --&gt;  customerAddressRepository : depends on

selfProfileAssembler  --&gt;  customerRepository : depends on

selfProfileAssembler  --&gt;  mediaStorage : depends on

selfProfileAssembler  --&gt;  permissionResolver : depends on

selfProfileAssembler  --&gt;  sellerRepository : depends on

selfProfileAssembler  --&gt;  userRepository : depends on

sellerAccountService  --&gt;  outboxWriter : depends on

sellerAccountService  --&gt;  selfProfileAssembler : depends on

sellerAccountService  --&gt;  sellerLogos : depends on

sellerAccountService  --&gt;  sellerRepository : depends on

sellerAccountService  --&gt;  userRepository : depends on

sellerLogos  --&gt;  node27 : depends on

sellerLogos  --&gt;  mediaStorage : depends on

sellerProfileController  --&gt;  sellerAccountService : depends on

sessionService  --&gt;  accessTokenIssuer : depends on

sessionService  --&gt;  authSessionRepository : depends on

sessionService  --&gt;  clock : depends on

sessionService  --&gt;  node73 : depends on

sessionService  --&gt;  rotatedRefreshTokenRepository : depends on

sessionService  --&gt;  userRepository : depends on

sessionTokenValidator  --&gt;  authSessionRepository : depends on

sessionTokenValidator  --&gt;  userRepository : depends on

signingKeyConfig  ..&gt;  jwtEncoder : @Bean

signingKeyConfig  ..&gt;  signingKey : @Bean

testClockConfiguration  ..&gt;  mutableClock : @Bean

testcontainersConfiguration  ..&gt;  postgresContainer : @Bean

node24  ..&gt;  openSecurity : @Bean

