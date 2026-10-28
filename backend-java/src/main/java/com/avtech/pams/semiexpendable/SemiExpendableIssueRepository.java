package com.avtech.pams.semiexpendable;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface SemiExpendableIssueRepository extends JpaRepository<SemiExpendableIssue, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SemiExpendableIssue s where s.id = :id")
    Optional<SemiExpendableIssue> lockById(@Param("id") Long id);

    @Query("""
            select s from SemiExpendableIssue s
            where (:status is null or s.status = :status)
              and (:outstanding = false or s.quantityReturned < s.quantity)
              and (:itemId is null or s.itemId = :itemId)
              and (:holderId is null or s.issuedTo = :holderId)
              and (:search is null
                   or lower(coalesce(s.custodianName, '')) like lower(concat('%', cast(:search as string), '%'))
                   or lower(coalesce(s.icsNumber, '')) like lower(concat('%', cast(:search as string), '%')))
            order by s.issuedDate desc, s.id desc
            """)
    Page<SemiExpendableIssue> search(@Param("status") String status, @Param("outstanding") boolean outstanding,
                                     @Param("itemId") Long itemId, @Param("holderId") Long holderId,
                                     @Param("search") String search, Pageable pageable);
}
