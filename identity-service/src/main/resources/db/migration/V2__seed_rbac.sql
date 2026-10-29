-- RBAC seed: the roles of DB §3.1.5 and the permission matrix of API §3.2.1 / DB §3.1.9.
-- account:read:own, account:update:own and seller:application:own are the baseline policy for every ACTIVE user;
-- they are catalogued here but deliberately not linked to any role. Changing role_permissions later must bump
-- authz_version for every affected user (API §3.2.1).

INSERT INTO roles (code, name, description) VALUES
    ('CUSTOMER', 'Customer', 'Buys products; granted when the Customer profile is created'),
    ('SELLER', 'Seller', 'Runs a shop; granted when an admin approves the Seller profile'),
    ('ADMIN', 'Administrator', 'Internal operator; granted only by bootstrap or a controlled internal process');

INSERT INTO permissions (code, description) VALUES
    ('account:read:own', 'Read own account (baseline policy)'),
    ('account:update:own', 'Update own account name and phone, not role, status or email (baseline policy)'),
    ('seller:application:own', 'Submit or view own seller application without the SELLER role (baseline policy)'),
    ('customer:profile:own', 'Manage own Customer profile and address book'),
    ('cart:manage:own', 'Manage own cart; Customer must be ACTIVE'),
    ('checkout:create:own', 'Create own checkout; Customer must be ACTIVE'),
    ('payment:create:own', 'Pay for own checkout; Customer must be ACTIVE'),
    ('order:read:own', 'Read own orders, checkouts and payments'),
    ('order:cancel:own', 'Cancel own order or checkout when the state machine and G-14 allow it'),
    ('order:confirm:own', 'Confirm receipt of own order'),
    ('review:write:own', 'Review a product from own CONFIRMED order'),
    ('seller:profile:own', 'Manage own shop profile; Seller must be APPROVED and ACTIVE'),
    ('product:manage:own', 'Manage own products; Seller must be APPROVED and ACTIVE'),
    ('inventory:manage:own', 'Manage stock of own products; Seller must be APPROVED and ACTIVE'),
    ('order:fulfill:own', 'Fulfill orders of own shop in a valid order state'),
    ('wallet:read:own', 'Read own wallet; Seller must be valid'),
    ('withdrawal:create:own', 'Request a withdrawal from own wallet (WD-01…WD-11)'),
    ('seller:approve', 'Approve or reject seller applications, never one''s own'),
    ('account:moderate', 'Ban or unban users, customers and sellers with an audited reason'),
    ('product:moderate', 'Moderate products'),
    ('category:manage', 'Manage the category tree'),
    ('operations:read:any', 'Read system-wide data with PII filtered; never change balances or confirm payments'),
    ('refund:retry', 'Retry a failed refund'),
    ('notification:read:own', 'Read, mark and delete own notifications');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM (VALUES
    ('CUSTOMER', 'customer:profile:own'),
    ('CUSTOMER', 'cart:manage:own'),
    ('CUSTOMER', 'checkout:create:own'),
    ('CUSTOMER', 'payment:create:own'),
    ('CUSTOMER', 'order:read:own'),
    ('CUSTOMER', 'order:cancel:own'),
    ('CUSTOMER', 'order:confirm:own'),
    ('CUSTOMER', 'review:write:own'),
    ('CUSTOMER', 'notification:read:own'),
    ('SELLER', 'seller:profile:own'),
    ('SELLER', 'product:manage:own'),
    ('SELLER', 'inventory:manage:own'),
    ('SELLER', 'order:fulfill:own'),
    ('SELLER', 'wallet:read:own'),
    ('SELLER', 'withdrawal:create:own'),
    ('SELLER', 'notification:read:own'),
    ('ADMIN', 'seller:approve'),
    ('ADMIN', 'account:moderate'),
    ('ADMIN', 'product:moderate'),
    ('ADMIN', 'category:manage'),
    ('ADMIN', 'operations:read:any'),
    ('ADMIN', 'refund:retry'),
    ('ADMIN', 'notification:read:own')
) AS m (role_code, permission_code)
JOIN roles r ON r.code = m.role_code
JOIN permissions p ON p.code = m.permission_code;
