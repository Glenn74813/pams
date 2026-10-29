package com.avtech.pams.purchasing;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Optional;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PurchaseOrder p where p.id = :id and p.deletedAt is null")
    Optional<PurchaseOrder> lockById(@Param("id") Long id);

    @Query("select p from PurchaseOrder p where p.id = :id and p.deletedAt is null")
    Optional<PurchaseOrder> findLive(@Param("id") Long id);

    /** onlyRequester limits the list to POs on that user's purchase requests. */
    @Query("""
            select p from PurchaseOrder p
            where p.deletedAt is null
              and (:statusesEmpty = true or p.status in :statuses)
              and (:supplier is null or p.supplierId = :supplier)
              and (:onlyRequester is null or p.purchaseRequestId in (select r.id from PurchaseRequest r where r.requestedBy = :onlyRequester))
              and (:search is null or lower(p.poNumber) like lower(concat('%', cast(:search as string), '%')))
            order by p.createdAt desc, p.id desc
            """)
    Page<PurchaseOrder> search(@Param("statusesEmpty") boolean statusesEmpty, @Param("statuses") Collection<String> statuses,
                               @Param("supplier") Long supplier, @Param("onlyRequester") Long onlyRequester,
                               @Param("search") String search, Pageable pageable);
}
