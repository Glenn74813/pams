package com.avtech.pams.procurement;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface AppItemRepository extends JpaRepository<AppItem, Long> {

    /** Locks APP lines so two purchase requests cannot both take the last of a line's quantity. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from AppItem i join fetch i.app where i.id in :ids order by i.id")
    List<AppItem> lockAll(@Param("ids") Collection<Long> ids);
}
