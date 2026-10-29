package com.avtech.pams.supplier;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface SupplierRepository extends JpaRepository<Supplier, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Supplier s where s.id = :id")
    Optional<Supplier> lockById(@Param("id") Long id);

    @Query("select x.supplier.id from ComplianceDocument x where x.id = :id")
    Optional<Long> supplierOfDocument(@Param("id") Long id);

    @Query("select x.supplier.id from SupplierSanction x where x.id = :id")
    Optional<Long> supplierOfSanction(@Param("id") Long id);

    @Query("select x.supplier.id from SupplierEvaluation x where x.id = :id")
    Optional<Long> supplierOfEvaluation(@Param("id") Long id);

    @Query("select count(s) > 0 from Supplier s where lower(s.name) = lower(:name) and (:except is null or s.id <> :except)")
    boolean nameTaken(@Param("name") String name, @Param("except") Long except);

    @Query("select count(s) > 0 from Supplier s where lower(s.email) = lower(:email) and (:except is null or s.id <> :except)")
    boolean emailTaken(@Param("email") String email, @Param("except") Long except);

    @Query("select count(s) > 0 from Supplier s where s.vendorCode = :code and (:except is null or s.id <> :except)")
    boolean codeTaken(@Param("code") String code, @Param("except") Long except);

    @Query("""
            select s from Supplier s
            where (:active is null or s.active = :active)
              and (:eligible is null or s.eligible = :eligible)
              and (:search is null or lower(s.name) like lower(concat('%', cast(:search as string), '%'))
                   or lower(coalesce(s.vendorCode, '')) like lower(concat('%', cast(:search as string), '%'))
                   or lower(coalesce(s.tin, '')) like lower(concat('%', cast(:search as string), '%'))
                   or lower(coalesce(s.contactPerson, '')) like lower(concat('%', cast(:search as string), '%'))
                   or lower(coalesce(s.email, '')) like lower(concat('%', cast(:search as string), '%')))
            order by s.name, s.id
            """)
    Page<Supplier> search(@Param("active") Boolean active, @Param("eligible") Boolean eligible, @Param("search") String search, Pageable pageable);
}
