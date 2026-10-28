package com.avtech.pams.role;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Long> {

    Optional<Role> findByName(String name);

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

    /** Each role with how many users hold it, as [Role, Long]. */
    @Query("select r, (select count(u) from User u where u.role = r) from Role r order by r.id")
    List<Object[]> findAllWithUserCounts();
}
