package com.avtech.pams.procurement;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AppPlanRepository extends JpaRepository<AppPlan, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from AppPlan a where a.id = :id and a.deletedAt is null")
    Optional<AppPlan> lockById(@Param("id") Long id);

    @Query("select a from AppPlan a where a.id = :id and a.deletedAt is null")
    Optional<AppPlan> findLive(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from AppPlan a where a.fiscalYear = :year and a.deletedAt is null")
    Optional<AppPlan> lockForYear(@Param("year") Integer year);

    @Query("""
            select a from AppPlan a
            where a.deletedAt is null
              and (:year is null or a.fiscalYear = :year)
              and (:status is null or a.status = :status)
            order by a.fiscalYear desc, a.id desc
            """)
    Page<AppPlan> search(@Param("year") Integer year, @Param("status") String status, Pageable pageable);
}
