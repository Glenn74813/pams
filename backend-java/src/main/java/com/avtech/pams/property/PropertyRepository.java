package com.avtech.pams.property;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PropertyRepository extends JpaRepository<Property, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Property p where p.id = :id")
    Optional<Property> lockById(@Param("id") Long id);

    boolean existsByTagNumberIgnoreCase(String tag);

    boolean existsBySerialNumberIgnoreCase(String serial);

    boolean existsBySerialNumberIgnoreCaseAndIdNot(String serial, Long id);

    boolean existsByTagNumberIgnoreCaseAndIdNot(String tag, Long id);

    List<Property> findByAssignedToOrderByAssignedDateDesc(Long userId);

    /** holder limits to one person's assets (for people who may only see their own). */
    @Query("""
            select p from Property p
            where (:holder is null or p.assignedTo = :holder)
              and (:status is null or p.status = :status)
              and (:condition is null or p.condition = :condition)
              and (:search is null
                   or lower(p.tagNumber) like lower(concat('%', cast(:search as string), '%'))
                   or lower(coalesce(p.serialNumber, '')) like lower(concat('%', cast(:search as string), '%'))
                   or lower(coalesce(p.accountabilityNumber, '')) like lower(concat('%', cast(:search as string), '%'))
                   or p.itemId in (select i.id from Item i where lower(i.name) like lower(concat('%', cast(:search as string), '%'))))
            order by p.tagNumber
            """)
    Page<Property> search(@Param("holder") Long holder, @Param("status") String status, @Param("condition") String condition,
                          @Param("search") String search, Pageable pageable);

    /** PPE that can still depreciate (not disposed). */
    @Query("""
            select p from Property p
            where p.depreciable = true and p.status <> 'disposed' and p.acquisitionCost is not null and p.acquisitionCost > 0
              and p.itemId in (select i.id from Item i where i.ppe = true)
            """)
    List<Property> depreciableAssets();
}
