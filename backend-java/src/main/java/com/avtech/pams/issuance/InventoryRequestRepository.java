package com.avtech.pams.issuance;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;

public interface InventoryRequestRepository extends JpaRepository<InventoryRequest, Long> {

    @EntityGraph(attributePaths = "items")
    Optional<InventoryRequest> findWithItemsById(Long id);

    /** Locks the request so two approvers can't process it at the same time. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from InventoryRequest r where r.id = :id")
    Optional<InventoryRequest> lockById(@Param("id") Long id);

    /** onlyUser limits to one requester (people who may only see their own requests). */
    @Query("""
            select r from InventoryRequest r
            where (:onlyUser is null or r.requestedBy = :onlyUser)
              and (:status is null or r.status = :status)
              and (:from is null or r.requestDate >= :from)
              and (:to is null or r.requestDate <= :to)
              and (:search is null
                   or lower(r.requestNumber) like lower(concat('%', cast(:search as string), '%'))
                   or lower(coalesce(r.risNumber, '')) like lower(concat('%', cast(:search as string), '%'))
                   or lower(coalesce(r.purpose, '')) like lower(concat('%', cast(:search as string), '%')))
            """)
    Page<InventoryRequest> search(@Param("onlyUser") Long onlyUser, @Param("status") String status,
                                  @Param("from") LocalDate from, @Param("to") LocalDate to,
                                  @Param("search") String search, Pageable pageable);
}
