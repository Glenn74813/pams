package com.avtech.pams.physicalcount;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PhysicalCountRepository extends JpaRepository<PhysicalCount, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from PhysicalCount c where c.id = :id")
    Optional<PhysicalCount> lockById(@Param("id") Long id);

    @Query("""
            select c from PhysicalCount c
            where (:status is null or c.status = :status)
              and (:type is null or c.countType = :type)
              and (:search is null or lower(c.countNumber) like lower(concat('%', cast(:search as string), '%'))
                   or lower(coalesce(c.purpose, '')) like lower(concat('%', cast(:search as string), '%')))
            order by c.id desc
            """)
    Page<PhysicalCount> search(@Param("status") String status, @Param("type") String type, @Param("search") String search, Pageable page);
}
