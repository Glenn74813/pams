package com.avtech.pams.bac;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BacResolutionRepository extends JpaRepository<BacResolution, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from BacResolution r where r.id = :id and r.deletedAt is null")
    Optional<BacResolution> lockById(@Param("id") Long id);

    @Query("select r from BacResolution r where r.id = :id and r.deletedAt is null")
    Optional<BacResolution> findLive(@Param("id") Long id);

    @Query("select r from BacResolution r where r.meetingId = :meetingId and r.deletedAt is null order by r.id")
    List<BacResolution> forMeeting(@Param("meetingId") Long meetingId);

    @Query("""
            select r from BacResolution r
            where r.deletedAt is null
              and (:status is null or r.status = :status)
              and (:type is null or r.type = :type)
              and (:pr is null or r.purchaseRequestId = :pr)
              and (:search is null or lower(r.resolutionNumber) like lower(concat('%', cast(:search as string), '%'))
                   or lower(r.title) like lower(concat('%', cast(:search as string), '%')))
            order by r.createdAt desc, r.id desc
            """)
    Page<BacResolution> search(@Param("status") String status, @Param("type") String type, @Param("pr") Long pr,
                               @Param("search") String search, Pageable pageable);
}
