package com.avtech.pams.inventory;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface StockRepository extends JpaRepository<Stock, Long> {

    List<Stock> findByItemIdOrderByReceivedDateAscIdAsc(Long itemId);

    @Query("select s from Stock s where (:itemId is null or s.itemId = :itemId) order by s.receivedDate desc, s.id desc")
    Page<Stock> entries(@Param("itemId") Long itemId, Pageable pageable);

    @Query("select s from Stock s where s.itemId = :itemId and s.location = :location and s.unitCost = :unitCost")
    List<Stock> atLocationWithCost(@Param("itemId") Long itemId, @Param("location") String location,
                                   @Param("unitCost") java.math.BigDecimal unitCost);

    /** An identical lot (brand, cost, lot no., expiry) already at the destination, to merge a transfer into. */
    default List<Stock> sameLotAt(Stock src, String location) {
        return atLocationWithCost(src.getItemId(), location, src.getUnitCost()).stream()
                .filter(s -> !s.getId().equals(src.getId()))
                .filter(s -> java.util.Objects.equals(s.getBrandName(), src.getBrandName()))
                .filter(s -> java.util.Objects.equals(s.getLotNumber(), src.getLotNumber()))
                .filter(s -> java.util.Objects.equals(s.getExpirationDate(), src.getExpirationDate()))
                .toList();
    }

    @Query("select s from Stock s where s.quantity > 0 and s.expirationDate is not null and s.expirationDate < :today order by s.expirationDate")
    List<Stock> expired(@Param("today") LocalDate today);

    @Query("""
            select s from Stock s where s.quantity > 0 and s.expirationDate is not null
              and s.expirationDate >= :today and s.expirationDate <= :until order by s.expirationDate
            """)
    List<Stock> expiringBetween(@Param("today") LocalDate today, @Param("until") LocalDate until);

    @Query("select distinct s.location from Stock s where s.location is not null and s.location <> '' order by s.location")
    List<String> locations();

    default Optional<Stock> firstSameLotAt(Stock src, String location) {
        return sameLotAt(src, location).stream().findFirst();
    }
}
