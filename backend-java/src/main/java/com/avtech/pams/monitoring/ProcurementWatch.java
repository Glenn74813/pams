package com.avtech.pams.monitoring;

import com.avtech.pams.bidding.Bidding;
import com.avtech.pams.bidding.BiddingController;
import com.avtech.pams.bidding.BiddingRepository;
import com.avtech.pams.common.Times;
import com.avtech.pams.contract.Contract;
import com.avtech.pams.contract.ContractController;
import com.avtech.pams.contract.ContractRepository;
import com.avtech.pams.procurement.ProcurementRules;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Deadlines across procurement that need someone's attention: bidding stages running past their
 * legal maximum, quotation deadlines, PO deliveries, contract deliveries accruing liquidated damages,
 * contracts and securities about to lapse, supplier documents expiring, and APP lines due this quarter
 * that nobody has requested yet (TOR 4.1, 7.4). Feeds the Monitoring page and the daily alerts.
 */
@Component
public class ProcurementWatch {

    public static final String OVERDUE = "overdue";
    public static final String DUE_SOON = "due_soon";

    /** Days before a deadline that it counts as due soon, per kind of deadline. */
    static final int STAGE_WARN = 5, RFQ_WARN = 3, DELIVERY_WARN = 7, EXPIRY_WARN = 30;

    private final JdbcTemplate jdbc;
    private final BiddingRepository biddings;
    private final ContractRepository contracts;
    private final ContractController contractRules;

    public ProcurementWatch(JdbcTemplate jdbc, BiddingRepository biddings, ContractRepository contracts, ContractController contractRules) {
        this.jdbc = jdbc;
        this.biddings = biddings;
        this.contracts = contracts;
        this.contractRules = contractRules;
    }

    /**
     * One thing to watch. {@code days} is the days left (negative once overdue); {@code relatedType}
     * is the Laravel model name so notifications and links agree with the rest of the system.
     */
    public record Entry(String kind, String severity, String reference, String title, String detail, LocalDate date, Integer days,
                        String relatedType, Long relatedId) {
    }

    @Transactional(readOnly = true)
    public List<Entry> scan() {
        LocalDate today = LocalDate.now(Times.ZONE);
        List<Entry> out = new ArrayList<>();
        biddingStages(today, out);
        quotationDeadlines(today, out);
        poDeliveries(today, out);
        contractDeliveries(today, out);
        expiries(today, out);
        supplierDocuments(today, out);
        appSchedule(today, out);
        out.sort(Comparator.comparing((Entry e) -> OVERDUE.equals(e.severity()) ? 0 : 1)
                .thenComparing(e -> e.days() == null ? Integer.MAX_VALUE : e.days()));
        return out;
    }

    private void biddingStages(LocalDate today, List<Entry> out) {
        for (Bidding b : biddings.findInProgress()) {
            for (BiddingController.Interval t : BiddingController.timeline(b)) {
                if (!t.pending() || t.elapsedDays() == null) continue;
                int left = t.maxDays() - t.elapsedDays();
                if (left > STAGE_WARN) continue;
                out.add(new Entry("bidding_stage", left < 0 ? OVERDUE : DUE_SOON, b.getBiddingNumber(), b.getTitle(),
                        t.label() + ": day " + t.elapsedDays() + " of " + t.maxDays()
                                + (left < 0 ? " — " + (-left) + " day(s) over the maximum" : " — " + left + " day(s) left"),
                        t.from().plusDays(t.maxDays()), left, "App\\Models\\Bidding", b.getId()));
            }
        }
    }

    private void quotationDeadlines(LocalDate today, List<Entry> out) {
        jdbc.query("""
                select id, rfq_number, title, deadline::date from rfqs
                where deleted_at is null and status = 'issued' and deadline is not null and deadline::date <= ?""", rs -> {
            LocalDate d = rs.getDate(4).toLocalDate();
            int left = (int) ChronoUnit.DAYS.between(today, d);
            out.add(new Entry("rfq_deadline", left < 0 ? OVERDUE : DUE_SOON, rs.getString(2), rs.getString(3),
                    left < 0 ? "Quotation deadline passed " + (-left) + " day(s) ago — close the RFQ and prepare the abstract"
                            : "Quotations are due " + (left == 0 ? "today" : "in " + left + " day(s)"),
                    d, left, "App\\Models\\Rfq", rs.getLong(1)));
        }, java.sql.Date.valueOf(today.plusDays(RFQ_WARN)));
    }

    private void poDeliveries(LocalDate today, List<Entry> out) {
        jdbc.query("""
                select po.id, po.po_number, s.name, po.delivery_date, po.status from purchase_orders po
                left join suppliers s on s.id = po.supplier_id
                where po.deleted_at is null and po.status in ('sent', 'confirmed', 'partially_received')
                  and po.delivery_date is not null and po.delivery_date <= ?""", rs -> {
            LocalDate d = rs.getDate(4).toLocalDate();
            int left = (int) ChronoUnit.DAYS.between(today, d);
            String what = "partially_received".equals(rs.getString(5)) ? "Balance of the delivery" : "Delivery";
            out.add(new Entry("po_delivery", left < 0 ? OVERDUE : DUE_SOON, rs.getString(2), rs.getString(3),
                    left < 0 ? what + " is " + (-left) + " day(s) late" : what + " is due " + (left == 0 ? "today" : "in " + left + " day(s)"),
                    d, left, "App\\Models\\PurchaseOrder", rs.getLong(1)));
        }, java.sql.Date.valueOf(today.plusDays(DELIVERY_WARN)));
    }

    private void contractDeliveries(LocalDate today, List<Entry> out) {
        for (Contract c : contracts.findActive()) {
            ContractController.Delivery d = contractRules.delivery(c);
            if (d != null && d.complete()) continue;
            ContractController.LdEstimate ld = contractRules.ld(c, today);
            if (ld == null || ld.daysDelayed() <= 0) continue;
            out.add(new Entry("contract_delivery", OVERDUE, c.getContractNumber(), c.getTitle(),
                    ld.daysDelayed() + " day(s) past the " + ld.deadline() + " delivery deadline; liquidated damages so far ₱"
                            + ProcurementRules.peso(ld.amount()) + (ld.capped() ? " (10% ceiling reached — the contract may be rescinded)" : ""),
                    ld.deadline(), -ld.daysDelayed(), "App\\Models\\Contract", c.getId()));
        }
    }

    private void expiries(LocalDate today, List<Entry> out) {
        java.sql.Date horizon = java.sql.Date.valueOf(today.plusDays(EXPIRY_WARN));
        jdbc.query("""
                select id, contract_number, title, end_date from contracts
                where deleted_at is null and status = 'active' and end_date is not null and end_date <= ?""", rs -> {
            LocalDate d = rs.getDate(4).toLocalDate();
            int left = (int) ChronoUnit.DAYS.between(today, d);
            out.add(new Entry("contract_expiry", left < 0 ? OVERDUE : DUE_SOON, rs.getString(2), rs.getString(3),
                    left < 0 ? "Ended " + (-left) + " day(s) ago but is still active — complete or extend it" : "Expires in " + left + " day(s)",
                    d, left, "App\\Models\\Contract", rs.getLong(1)));
        }, horizon);
        jdbc.query("""
                select c.id, c.contract_number, cs.security_type, cs.validity_date from contract_securities cs
                join contracts c on c.id = cs.contract_id
                where c.deleted_at is null and c.status = 'active' and cs.status = 'posted'
                  and cs.validity_date is not null and cs.validity_date <= ?""", rs -> {
            LocalDate d = rs.getDate(4).toLocalDate();
            int left = (int) ChronoUnit.DAYS.between(today, d);
            out.add(new Entry("security_expiry", left < 0 ? OVERDUE : DUE_SOON, rs.getString(2), rs.getString(3),
                    left < 0 ? "Lapsed " + (-left) + " day(s) ago — renew it or call on it" : "Valid only " + left + " more day(s)",
                    d, left, "App\\Models\\Contract", rs.getLong(1)));
        }, horizon);
    }

    private void supplierDocuments(LocalDate today, List<Entry> out) {
        jdbc.query("""
                select s.id, s.name, d.document_type, d.expiry_date from supplier_compliance_documents d
                join suppliers s on s.id = d.supplier_id
                where s.is_active and d.expiry_date is not null and d.expiry_date <= ?
                  and not exists (select 1 from supplier_compliance_documents n where n.supplier_id = d.supplier_id
                                  and n.document_type = d.document_type and n.expiry_date > d.expiry_date)""", rs -> {
            LocalDate d = rs.getDate(4).toLocalDate();
            int left = (int) ChronoUnit.DAYS.between(today, d);
            out.add(new Entry("supplier_document", left < 0 ? OVERDUE : DUE_SOON, rs.getString(2), rs.getString(3),
                    left < 0 ? "Expired " + (-left) + " day(s) ago — the supplier is not eligible until it is renewed" : "Expires in " + left + " day(s)",
                    d, left, "App\\Models\\Supplier", rs.getLong(1)));
        }, java.sql.Date.valueOf(today.plusDays(EXPIRY_WARN)));
    }

    /** Approved APP lines scheduled for this quarter (or an earlier one this year) not yet fully requested. */
    private void appSchedule(LocalDate today, List<Entry> out) {
        int quarter = (today.getMonthValue() - 1) / 3 + 1;
        jdbc.query("""
                select a.id, a.app_number, ai.general_description, ai.schedule, ai.quantity, ai.estimated_budget,
                       coalesce((select sum(pri.quantity) from purchase_request_items pri join purchase_requests pr on pr.id = pri.purchase_request_id
                                 where pri.app_item_id = ai.id and pr.deleted_at is null and pr.status not in ('cancelled', 'rejected')), 0)
                from app_items ai join apps a on a.id = ai.app_id
                where a.deleted_at is null and a.status = 'approved' and a.fiscal_year = ? and ai.schedule in ('Q1', 'Q2', 'Q3', 'Q4')
                  and cast(substring(ai.schedule from 2) as int) <= ?""", rs -> {
            int requested = rs.getInt(7), qty = rs.getInt(5);
            if (requested >= qty) return;
            int q = Integer.parseInt(rs.getString(4).substring(1));
            LocalDate end = LocalDate.of(today.getYear(), q * 3, 1).plusMonths(1).minusDays(1);
            int left = (int) ChronoUnit.DAYS.between(today, end);
            BigDecimal budget = rs.getBigDecimal(6);
            out.add(new Entry("app_schedule", q < quarter ? OVERDUE : DUE_SOON, rs.getString(2), rs.getString(3),
                    (q < quarter ? "Scheduled for " + rs.getString(4) + " and still not requested" : "Scheduled for this quarter")
                            + " — " + (qty - requested) + " of " + qty + " unit(s) without a purchase request (est. ₱" + ProcurementRules.peso(budget) + ")",
                    end, left, "App\\Models\\App", rs.getLong(1)));
        }, today.getYear(), quarter);
    }
}
