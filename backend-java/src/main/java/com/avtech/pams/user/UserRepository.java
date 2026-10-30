package com.avtech.pams.user;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    @EntityGraph(attributePaths = {"role", "department"})
    Optional<User> findWithRoleById(Long id);

    @EntityGraph(attributePaths = {"role", "department"})
    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);

    /** Paged user list, optionally narrowed by a name/email search and a role. */
    @EntityGraph(attributePaths = {"role", "department"})
    @Query("""
            select u from User u
            where (:search is null
                   or lower(u.name) like lower(concat('%', cast(:search as string), '%'))
                   or lower(u.email) like lower(concat('%', cast(:search as string), '%')))
              and (:roleId is null or u.role.id = :roleId)
            """)
    Page<User> search(@Param("search") String search, @Param("roleId") Long roleId, Pageable pageable);

    /** Pickers offer active accounts only. */
    @Query("select u.id as id, u.name as name, u.email as email from User u where u.active = true order by u.name")
    List<UserOption> findAllOptions();

    interface UserOption {
        Long getId();

        String getName();

        String getEmail();
    }
}
