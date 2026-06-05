package com.solarisbank.auth_service.repository;

import com.solarisbank.auth_service.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);
    Optional<User> findByEmailVerificationToken(String token);
    Optional<User> findByPasswordResetToken(String token);
    boolean existsByEmail(String email);

    /**
     * Paginated user list with optional server-side filtering.
     * All three filter params are optional: passing {@code null} disables that filter.
     */
    @Query("""
            SELECT u FROM User u
            WHERE (:search   IS NULL
                   OR LOWER(CONCAT(u.firstname, ' ', u.lastname, ' ', u.email))
                      LIKE LOWER(CONCAT('%', :search, '%')))
              AND (:role     IS NULL OR u.role     = :role)
              AND (:isActive IS NULL OR u.isActive = :isActive)
            """)
    Page<User> findWithFilters(
            @Param("search")   String    search,
            @Param("role")     User.Role role,
            @Param("isActive") Boolean   isActive,
            Pageable pageable);
}
