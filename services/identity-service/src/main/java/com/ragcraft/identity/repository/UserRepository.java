package com.ragcraft.identity.repository;

import com.ragcraft.identity.domain.Organization;
import com.ragcraft.identity.domain.OrganizationMember;
import com.ragcraft.identity.domain.PasswordReset;
import com.ragcraft.identity.domain.User;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);
    long countByActiveTrue();

    @Query("select u from User u where lower(u.email) like lower(concat('%', :search, '%')) " +
           "or lower(u.fullName) like lower(concat('%', :search, '%')) order by u.createdAt desc")
    List<User> search(String search, Pageable pageable);
}
