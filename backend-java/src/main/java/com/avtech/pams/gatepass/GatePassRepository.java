package com.avtech.pams.gatepass;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface GatePassRepository extends JpaRepository<GatePass, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from GatePass g where g.id = :id and g.deletedAt is null")
    Optional<GatePass> lockById(@Param("id") Long id);

    @Query("select count(g) > 0 from GatePass g where g.propertyId = :propertyId and g.status <> 'returned' and g.deletedAt is null")
    boolean outFor(@Param("propertyId") Long propertyId);

    List<GatePass> findByPropertyIdOrderByDateReleasedDescIdDesc(Long propertyId);

    /** status "overdue" = still out past its expected return date. */
    @Query("""
            select g from GatePass g
            where g.deletedAt is null
              and (:onlyUser is null or g.releasedBy = :onlyUser)
              and (:status is null
                   or (:status = 'returned' and g.status = 'returned')
                   or (:status = 'released' and g.status <> 'returned')
                   or (:status = 'overdue' and g.status <> 'returned' and g.expectedReturnDate < :today))
              and (:search is null
                   or lower(g.gatePassNumber) like lower(concat('%', cast(:search as string), '%'))
                   or lower(g.receivedBy) like lower(concat('%', cast(:search as string), '%'))
                   or lower(g.purpose) like lower(concat('%', cast(:search as string), '%'))
                   or g.propertyId in (select p.id from Property p where lower(p.tagNumber) like lower(concat('%', cast(:search as string), '%'))))
            order by g.dateReleased desc, g.id desc
            """)
    Page<GatePass> search(@Param("onlyUser") Long onlyUser, @Param("status") String status, @Param("today") LocalDate today,
                          @Param("search") String search, Pageable pageable);
}
