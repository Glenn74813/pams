package com.avtech.pams.inventory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface InventoryLogRepository extends JpaRepository<InventoryLog, Long> {

    @Query("select l from InventoryLog l where l.itemId = :itemId and l.action in :actions order by l.createdAt asc, l.id asc")
    List<InventoryLog> movements(@Param("itemId") Long itemId, @Param("actions") Collection<String> actions);
}
