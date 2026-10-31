package com.example.identity_service.seller;

import com.example.identity_service.rbac.RoleCode;
import com.example.identity_service.user.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface SellerRepository extends JpaRepository<Seller, UUID> {

	/** Same normalization and scope as the {@code uq_sellers_seller_name_active} index. */
	@Query(value = """
			select exists (select 1 from sellers
			where lower(regexp_replace(btrim(seller_name), '\\s+', ' ', 'g'))
			      = lower(regexp_replace(btrim(:sellerName), '\\s+', ' ', 'g'))
			  and approval_status in ('PENDING', 'APPROVED'))""", nativeQuery = true)
	boolean existsActiveSellerName(String sellerName);

	/** {@code contactPhone} must already be normalized; same scope as {@code uq_sellers_contact_phone_active}. */
	@Query(value = """
			select exists (select 1 from sellers
			where contact_phone = :contactPhone and approval_status in ('PENDING', 'APPROVED'))""", nativeQuery = true)
	boolean existsActiveContactPhone(String contactPhone);

	@Query(value = """
			select exists (select 1 from sellers
			where contact_phone = :contactPhone and seller_id <> :sellerId
			  and approval_status in ('PENDING', 'APPROVED'))""", nativeQuery = true)
	boolean existsActiveContactPhoneOfAnotherSeller(String contactPhone, UUID sellerId);

	/**
	 * Sellers a buyer may see (G-06): User ACTIVE, Seller APPROVED + ACTIVE and an unrevoked SELLER role, whose name
	 * matches {@code namePattern} case-insensitively ({@code like} with {@code \} as escape character).
	 */
	default Page<Seller> findPublic(String namePattern, Pageable pageable) {
		return findVisible(null, namePattern, UserStatus.ACTIVE, SellerApprovalStatus.APPROVED, SellerStatus.ACTIVE,
				RoleCode.SELLER, pageable);
	}

	/** One seller if visible under the same G-06 rule as {@link #findPublic}. */
	default Optional<Seller> findPublicById(UUID sellerId) {
		return findVisible(sellerId, "%", UserStatus.ACTIVE, SellerApprovalStatus.APPROVED, SellerStatus.ACTIVE,
				RoleCode.SELLER, Pageable.unpaged()).stream().findFirst();
	}

	@Query("""
			select s from Seller s join s.user u
			where (:sellerId is null or s.sellerId = :sellerId)
			  and u.status = :userStatus and s.approvalStatus = :approvalStatus and s.status = :sellerStatus
			  and exists (select 1 from UserRole ur where ur.id.userId = s.sellerId and ur.role.code = :role
			              and ur.revokedAt is null)
			  and lower(s.sellerName) like lower(:namePattern) escape '\\'""")
	Page<Seller> findVisible(UUID sellerId, String namePattern, UserStatus userStatus,
			SellerApprovalStatus approvalStatus, SellerStatus sellerStatus, RoleCode role, Pageable pageable);

}
