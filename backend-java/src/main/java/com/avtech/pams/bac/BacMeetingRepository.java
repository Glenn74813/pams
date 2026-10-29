package com.avtech.pams.bac;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface BacMeetingRepository extends JpaRepository<BacMeeting, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from BacMeeting m where m.id = :id and m.deletedAt is null")
    Optional<BacMeeting> lockById(@Param("id") Long id);

    @Query("select m from BacMeeting m where m.id = :id and m.deletedAt is null")
    Optional<BacMeeting> findLive(@Param("id") Long id);

    @Query("""
            select m from BacMeeting m
            where m.deletedAt is null
              and (:status is null or m.status = :status)
              and (:search is null or lower(m.meetingNumber) like lower(concat('%', cast(:search as string), '%'))
                   or lower(m.title) like lower(concat('%', cast(:search as string), '%')))
            order by m.meetingDate desc, m.id desc
            """)
    Page<BacMeeting> search(@Param("status") String status, @Param("search") String search, Pageable pageable);
}
