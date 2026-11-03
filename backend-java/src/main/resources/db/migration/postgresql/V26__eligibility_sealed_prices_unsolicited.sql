-- GPPB Circular 06-2026: eligibility documents by mode. A supplier's kind decides the exceptions in 4.5, 4.6 and the
-- lease rows of the matrix; an RFQ states when documents are submitted (4.2) and whether the BAC revised the
-- eligibility requirements after two failed biddings (4.3.1 c).
ALTER TABLE suppliers ADD COLUMN supplier_kind varchar(20) NOT NULL DEFAULT 'business';
ALTER TABLE rfqs ADD COLUMN eligibility_revised boolean NOT NULL DEFAULT false;
ALTER TABLE rfqs ADD COLUMN documents_stage varchar(20);

-- IRR 64.1: a Limited Source Bidding or Competitive Dialogue can fail too, and counts towards two failed biddings.
ALTER TABLE rfqs ADD COLUMN failure_reason text;

-- IRR 58 (b): MEARB and MARB financial proposals stay sealed until the technical evaluation is done. Biddings that
-- already hold prices were opened under the earlier procedure.
ALTER TABLE biddings ADD COLUMN financial_opened_at timestamp(0) without time zone;
UPDATE biddings SET financial_opened_at = updated_at
WHERE evaluation_method IN ('mearb', 'marb')
  AND EXISTS (SELECT 1 FROM bidding_bidders x WHERE x.bidding_id = biddings.id AND x.bid_amount IS NOT NULL);

-- IRR Section 30: Unsolicited Offer with Bid Matching.
CREATE TABLE unsolicited_offers (
    id bigserial PRIMARY KEY,
    offer_number varchar(40) NOT NULL UNIQUE,
    supplier_id bigint NOT NULL REFERENCES suppliers (id),
    title varchar(255) NOT NULL,
    concept text NOT NULL,
    category varchar(30) NOT NULL,
    proposed_amount numeric(15, 2) NOT NULL,
    received_date date NOT NULL,
    status varchar(30) NOT NULL DEFAULT 'received',
    notice_date date,
    documents_date date,
    new_concept_basis varchar(30),
    evaluation_extension_days integer,
    evaluation_extension_reason text,
    evaluation_date date,
    negotiation_start date,
    negotiation_end date,
    negotiated_amount numeric(15, 2),
    negotiated_terms text,
    certification_number varchar(60),
    certification_date date,
    bid_security_form varchar(40),
    bid_security_amount numeric(15, 2),
    bid_security_date date,
    bidding_id bigint REFERENCES biddings (id),
    lccrb_date date,
    lccrb_supplier_id bigint REFERENCES suppliers (id),
    lccrb_amount numeric(15, 2),
    match_result varchar(20),
    match_amount numeric(15, 2),
    match_date date,
    closing_reason text,
    closed_date date,
    late_withdrawal boolean NOT NULL DEFAULT false,
    created_by bigint,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone,
    deleted_at timestamp(0) without time zone,
    CONSTRAINT unsolicited_offers_category_check CHECK (category IN ('Goods', 'Consulting Services'))
);
CREATE INDEX unsolicited_offers_status_index ON unsolicited_offers (status);
CREATE INDEX unsolicited_offers_supplier_index ON unsolicited_offers (supplier_id);
