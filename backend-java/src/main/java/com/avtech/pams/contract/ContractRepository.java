package com.avtech.pams.contract;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;

public interface ContractRepository extends JpaRepository<Contract, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Contract c where c.id = :id and c.deletedAt is null")
    Optional<Contract> lockById(@Param("id") Long id);

    @Query("select c from Contract c where c.id = :id and c.deletedAt is null")
    Optional<Contract> findLive(@Param("id") Long id);

    @Query("select x.contract.id from ContractAmendment x where x.id = :id")
    Optional<Long> contractOfAmendment(@Param("id") Long id);

    @Query("select x.contract.id from ContractSecurity x where x.id = :id")
    Optional<Long> contractOfSecurity(@Param("id") Long id);

    @Query("select x.contract.id from DeliveryExtension x where x.id = :id")
    Optional<Long> contractOfExtension(@Param("id") Long id);

    @Query("select x.contract.id from FrameworkCalloff x where x.id = :id")
    Optional<Long> contractOfCalloff(@Param("id") Long id);

    /** expiringBy limits the list to active contracts ending between today and that date. */
    @Query("""
            select c from Contract c
            where c.deletedAt is null
              and (:status is null or c.status = :status)
              and (:type is null or c.contractType = :type)
              and (:supplier is null or c.supplierId = :supplier)
              and (cast(:expiringBy as date) is null or (c.status = 'active' and c.endDate between :today and :expiringBy))
              and (:search is null or lower(c.contractNumber) like lower(concat('%', cast(:search as string), '%'))
                   or lower(c.title) like lower(concat('%', cast(:search as string), '%')))
            order by c.createdAt desc, c.id desc
            """)
    Page<Contract> search(@Param("status") String status, @Param("type") String type, @Param("supplier") Long supplier,
                          @Param("today") LocalDate today, @Param("expiringBy") LocalDate expiringBy, @Param("search") String search, Pageable pageable);
}
