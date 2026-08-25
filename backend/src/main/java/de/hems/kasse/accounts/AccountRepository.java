package de.hems.kasse.accounts;

import de.hems.kasse.auth.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<Account, UUID> {

    @Query("select a from Account a where a.role = :role and lower(a.name) = lower(:name)")
    Optional<Account> findByRoleAndName(@Param("role") Role role, @Param("name") String name);

    List<Account> findAllByOrderByRoleAscNameAsc();

    List<Account> findAllByRoleAndActiveIsTrueOrderByNameAsc(Role role);

    long countByRole(Role role);
}
