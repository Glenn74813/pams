-- Lowest-price (LCRB) biddings now keep the second bid envelope sealed until the eligibility and technical
-- requirements are rated, as MEARB and MARB already did (IRR 58, 59.2). Biddings recorded before this change had
-- their prices read at the opening: mark their second envelopes as opened where the financial stage has begun or
-- the bidding is over, so they carry on as recorded. The others open them in the new order.
UPDATE biddings SET financial_opened_at = COALESCE(bid_opening_date, updated_at)
WHERE evaluation_method = 'price' AND financial_opened_at IS NULL
  AND (status IN ('awarded', 'failed', 'cancelled')
       OR EXISTS (SELECT 1 FROM bidding_bidders bb WHERE bb.bidding_id = biddings.id AND (bb.financial_result <> 'pending' OR bb.post_qual_result <> 'pending')));
