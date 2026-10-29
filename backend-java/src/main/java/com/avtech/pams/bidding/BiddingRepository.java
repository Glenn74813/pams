package com.avtech.pams.bidding;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface BiddingRepository extends JpaRepository<Bidding, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Bidding b where b.id = :id and b.deletedAt is null")
    Optional<Bidding> lockById(@Param("id") Long id);

    @Query("select b from Bidding b where b.id = :id and b.deletedAt is null")
    Optional<Bidding> findLive(@Param("id") Long id);

    @Query("select x.bidding.id from BiddingBidder x where x.id = :id")
    Optional<Long> biddingOfBidder(@Param("id") Long bidderId);

    @Query("select x.bidding.id from BidProtest x where x.id = :id")
    Optional<Long> biddingOfProtest(@Param("id") Long protestId);

    @Query("select x.bidding.id from BidObserver x where x.id = :id")
    Optional<Long> biddingOfObserver(@Param("id") Long observerId);

    @Query("""
            select b from Bidding b
            where b.deletedAt is null
              and (:status is null or b.status = :status)
              and (:pr is null or b.purchaseRequestId = :pr)
              and (:search is null or lower(b.biddingNumber) like lower(concat('%', cast(:search as string), '%'))
                   or lower(b.title) like lower(concat('%', cast(:search as string), '%')))
            order by b.createdAt desc, b.id desc
            """)
    Page<Bidding> search(@Param("status") String status, @Param("pr") Long pr, @Param("search") String search, Pageable pageable);

    /** Biddings still under way (posted, not yet awarded, failed or cancelled). */
    @Query("select b from Bidding b where b.deletedAt is null and b.status in ('pre_procurement', 'invitation', 'bid_opening', 'evaluation', 'awarded') and b.ntpDate is null order by b.id")
    java.util.List<Bidding> findInProgress();
}
