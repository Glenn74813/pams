package com.avtech.pams.inventory;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StockTransferRepository extends JpaRepository<StockTransfer, Long> {

    @Query("""
            select t from StockTransfer t
            where (:itemId is null or t.itemId = :itemId)
              and (:location is null
                   or lower(t.fromLocation) like lower(concat('%', cast(:location as string), '%'))
                   or lower(t.toLocation) like lower(concat('%', cast(:location as string), '%')))
            order by t.transferDate desc, t.id desc
            """)
    Page<StockTransfer> history(@Param("itemId") Long itemId, @Param("location") String location, Pageable pageable);

    @Query("select count(t) from StockTransfer t where t.transferNumber like concat(:prefix, '%')")
    long countWithPrefix(@Param("prefix") String prefix);
}
