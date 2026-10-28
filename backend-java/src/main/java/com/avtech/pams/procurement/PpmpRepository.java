package com.avtech.pams.procurement;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PpmpRepository extends JpaRepository<Ppmp, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Ppmp p where p.id = :id and p.deletedAt is null")
    Optional<Ppmp> lockById(@Param("id") Long id);

    @Query("select p from Ppmp p where p.id = :id and p.deletedAt is null")
    Optional<Ppmp> findLive(@Param("id") Long id);

    /** Approved PPMPs of the year, locked so they cannot change while they are consolidated. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Ppmp p where p.fiscalYear = :year and p.status = 'approved' and p.deletedAt is null order by p.id")
    List<Ppmp> lockApproved(@Param("year") Integer year);

    /** restrict = false shows everything; otherwise only the given department's and the user's own. */
    @Query("""
            select p from Ppmp p
            where p.deletedAt is null
              and (:restrict = false or p.preparedBy = :user or (:dept is not null and p.departmentId = :dept))
              and (:year is null or p.fiscalYear = :year)
              and (:status is null or p.status = :status)
              and (:department is null or p.departmentId = :department)
              and (:search is null or lower(p.ppmpNumber) like lower(concat('%', cast(:search as string), '%'))
                   or lower(p.title) like lower(concat('%', cast(:search as string), '%')))
            order by p.createdAt desc, p.id desc
            """)
    Page<Ppmp> search(@Param("restrict") boolean restrict, @Param("user") Long user, @Param("dept") Long dept,
                      @Param("year") Integer year, @Param("status") String status, @Param("department") Long department,
                      @Param("search") String search, Pageable pageable);
}
