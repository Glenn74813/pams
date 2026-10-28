package com.avtech.pams.property;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PropertyTransferRepository extends JpaRepository<PropertyTransfer, Long> {

    List<PropertyTransfer> findByPropertyIdOrderByTransferDateDescIdDesc(Long propertyId);

    boolean existsByPropertyId(Long propertyId);

    @Query("""
            select t from PropertyTransfer t
            where (:type is null or t.transferType = :type)
              and (:search is null
                   or lower(coalesce(t.referenceNumber, '')) like lower(concat('%', cast(:search as string), '%'))
                   or lower(t.transferReason) like lower(concat('%', cast(:search as string), '%'))
                   or t.propertyId in (select p.id from Property p where lower(p.tagNumber) like lower(concat('%', cast(:search as string), '%'))))
            order by t.transferDate desc, t.id desc
            """)
    Page<PropertyTransfer> search(@Param("type") String type, @Param("search") String search, Pageable pageable);
}
