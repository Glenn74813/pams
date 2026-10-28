package com.avtech.pams.procurement;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Optional;

public interface PurchaseRequestRepository extends JpaRepository<PurchaseRequest, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PurchaseRequest p where p.id = :id and p.deletedAt is null")
    Optional<PurchaseRequest> lockById(@Param("id") Long id);

    @Query("select p from PurchaseRequest p where p.id = :id and p.deletedAt is null")
    Optional<PurchaseRequest> findLive(@Param("id") Long id);

    /** awaiting = true keeps only requests whose current step is signed with one of stepPermissions. */
    @Query("""
            select p from PurchaseRequest p
            where p.deletedAt is null
              and (:onlyUser is null or p.requestedBy = :onlyUser)
              and (:status is null or p.status = :status)
              and (:priority is null or p.priority = :priority)
              and (:department is null or p.departmentId = :department)
              and (:year is null or p.fiscalYear = :year)
              and (:search is null or lower(p.prNumber) like lower(concat('%', cast(:search as string), '%'))
                   or lower(coalesce(p.purpose, '')) like lower(concat('%', cast(:search as string), '%')))
              and (:awaiting = false or (p.status in ('submitted', 'under_review') and exists (
                    select a from PurchaseRequestApproval a where a.request = p and a.step = p.currentStep
                      and a.status = 'pending' and a.requiredPermission in :stepPermissions)))
            order by p.createdAt desc, p.id desc
            """)
    Page<PurchaseRequest> search(@Param("onlyUser") Long onlyUser, @Param("status") String status, @Param("priority") String priority,
                                 @Param("department") Long department, @Param("year") Integer year, @Param("search") String search,
                                 @Param("awaiting") boolean awaiting, @Param("stepPermissions") Collection<String> stepPermissions,
                                 Pageable pageable);
}
