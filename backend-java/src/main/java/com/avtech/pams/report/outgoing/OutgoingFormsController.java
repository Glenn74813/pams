package com.avtech.pams.report.outgoing;

import com.avtech.pams.common.ApiException;
import com.avtech.pams.report.CoaExportController;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Downloads of the documents PAMS sends out, as Excel; with {@code format=pdf} the same layout as PDF (PdfFormAdvice).
 * Each download is audited. Read access follows the record's own page.
 */
@RestController
@RequestMapping("/api")
public class OutgoingFormsController {

    private static final String PO_READ = "@perm.any('view_procurement', 'approve_procurement', 'edit_procurement', 'create_procurement', 'view_receiving', 'create_receiving', 'accept_delivery')";
    private static final String RFQ_READ = "@perm.any('view_rfq', 'create_rfq', 'generate_abstract', 'process_award')";
    private static final String BID_READ = "@perm.any('view_bidding', 'manage_bidding', 'process_award', 'conduct_bid_evaluation')";
    private static final String BAC_READ = "@perm.any('view_bac', 'manage_bac_meetings', 'manage_bac_resolutions', 'conduct_bid_evaluation', 'process_award', 'create_rfq', 'view_rfq')";
    private static final String CONTRACT_READ = "@perm.any('view_contracts', 'create_contracts', 'edit_contracts')";

    private final JdbcTemplate jdbc;
    private final CoaExportController files;
    private final PurchaseOrderForm po;
    private final RfqForms rfq;
    private final NoticeForms notices;
    private final ContractForms contracts;
    private final BacResolutionForm resolution;

    public OutgoingFormsController(JdbcTemplate jdbc, CoaExportController files, PurchaseOrderForm po, RfqForms rfq, NoticeForms notices,
                                   ContractForms contracts, BacResolutionForm resolution) {
        this.jdbc = jdbc;
        this.files = files;
        this.po = po;
        this.rfq = rfq;
        this.notices = notices;
        this.contracts = contracts;
        this.resolution = resolution;
    }

    // ───────────────────────── purchase orders ─────────────────────────

    @GetMapping("/procurement/purchase-orders/{id}/export")
    @PreAuthorize(PO_READ)
    public ResponseEntity<byte[]> purchaseOrder(@PathVariable Long id) {
        String no = number("select po_number from purchase_orders where id = ? and deleted_at is null", "Purchase order", id);
        return files.file(po.generate(id), "PO_" + no + ".xlsx", "Purchase Order " + no, "App\\Models\\PurchaseOrder", id);
    }

    // ───────────────────────── RFQs ─────────────────────────

    @GetMapping("/rfq/{id}/form/export")
    @PreAuthorize(RFQ_READ)
    public ResponseEntity<byte[]> requestForQuotation(@PathVariable Long id) {
        String no = rfqNumber(id);
        return files.file(rfq.request(id), "RFQ_" + no + ".xlsx", "Request for Quotation " + no, RFQ, id);
    }

    @GetMapping("/rfq/{id}/abstract/export")
    @PreAuthorize(RFQ_READ)
    public ResponseEntity<byte[]> abstractOfQuotations(@PathVariable Long id) {
        String no = rfqNumber(id);
        return files.file(rfq.abstractOfQuotations(id), "AbstractOfQuotations_" + no + ".xlsx", "Abstract of Quotations " + no, RFQ, id);
    }

    @GetMapping("/rfq/{id}/noa/export")
    @PreAuthorize(RFQ_READ)
    public ResponseEntity<byte[]> rfqNoa(@PathVariable Long id) {
        String no = rfqNumber(id);
        return files.file(notices.noticeOfAward("rfq", id), "NOA_" + no + ".xlsx", "Notice of Award for " + no, RFQ, id);
    }

    @GetMapping("/rfq/{id}/ntp/export")
    @PreAuthorize(RFQ_READ)
    public ResponseEntity<byte[]> rfqNtp(@PathVariable Long id) {
        String no = rfqNumber(id);
        return files.file(notices.noticeToProceed("rfq", id), "NTP_" + no + ".xlsx", "Notice to Proceed for " + no, RFQ, id);
    }

    // ───────────────────────── biddings ─────────────────────────

    @GetMapping("/bidding/{id}/itb/export")
    @PreAuthorize(BID_READ)
    public ResponseEntity<byte[]> invitationToBid(@PathVariable Long id) {
        String no = biddingNumber(id);
        return files.file(notices.invitationToBid(id), "ITB_" + no + ".xlsx", "Invitation to Bid " + no, BIDDING, id);
    }

    @GetMapping("/bidding/{id}/noa/export")
    @PreAuthorize(BID_READ)
    public ResponseEntity<byte[]> biddingNoa(@PathVariable Long id) {
        String no = biddingNumber(id);
        return files.file(notices.noticeOfAward("bidding", id), "NOA_" + no + ".xlsx", "Notice of Award for " + no, BIDDING, id);
    }

    @GetMapping("/bidding/{id}/ntp/export")
    @PreAuthorize(BID_READ)
    public ResponseEntity<byte[]> biddingNtp(@PathVariable Long id) {
        String no = biddingNumber(id);
        return files.file(notices.noticeToProceed("bidding", id), "NTP_" + no + ".xlsx", "Notice to Proceed for " + no, BIDDING, id);
    }

    @GetMapping("/bac/resolutions/{id}/export")
    @PreAuthorize(BAC_READ)
    public ResponseEntity<byte[]> bacResolution(@PathVariable Long id) {
        String no = number("select resolution_number from bac_resolutions where id = ? and deleted_at is null", "BAC resolution", id);
        return files.file(resolution.generate(id), "BAC_Resolution_" + no + ".xlsx", "BAC Resolution " + no, "App\\Models\\BacResolution", id);
    }

    // ───────────────────────── contracts ─────────────────────────

    @GetMapping("/contracts/{id}/export")
    @PreAuthorize(CONTRACT_READ)
    public ResponseEntity<byte[]> contract(@PathVariable Long id) {
        String no = contractNumber(id);
        return files.file(contracts.agreement(id), "Contract_" + no + ".xlsx", "Contract Agreement " + no, CONTRACT, id);
    }

    @GetMapping("/contracts/{id}/ld-notice/export")
    @PreAuthorize(CONTRACT_READ)
    public ResponseEntity<byte[]> liquidatedDamages(@PathVariable Long id) {
        String no = contractNumber(id);
        return files.file(notices.liquidatedDamages(id), "LD_Notice_" + no + ".xlsx", "Notice of Liquidated Damages for " + no, CONTRACT, id);
    }

    @GetMapping("/contracts/amendments/{id}/export")
    @PreAuthorize(CONTRACT_READ)
    public ResponseEntity<byte[]> amendment(@PathVariable Long id) {
        String no = number("""
                select c.contract_number from contract_amendments a join contracts c on c.id = a.contract_id
                where a.id = ? and c.deleted_at is null""", "Contract amendment", id);
        return files.file(contracts.amendment(id), "Amendment_" + no + "_" + id + ".xlsx", "Contract Amendment to " + no, "App\\Models\\ContractAmendment", id);
    }

    @GetMapping("/contracts/extensions/{id}/export")
    @PreAuthorize(CONTRACT_READ)
    public ResponseEntity<byte[]> extension(@PathVariable Long id) {
        String no = number("""
                select c.contract_number from delivery_extensions e join contracts c on c.id = e.contract_id
                where e.id = ? and c.deleted_at is null""", "Delivery extension", id);
        return files.file(contracts.extension(id), "Extension_" + no + "_" + id + ".xlsx", "Contract time extension for " + no, "App\\Models\\DeliveryExtension", id);
    }

    // ───────────────────────── helpers ─────────────────────────

    private static final String RFQ = "App\\Models\\Rfq", BIDDING = "App\\Models\\Bidding", CONTRACT = "App\\Models\\Contract";

    private String rfqNumber(Long id) {
        return number("select rfq_number from rfqs where id = ? and deleted_at is null", "RFQ", id);
    }

    private String biddingNumber(Long id) {
        return number("select bidding_number from biddings where id = ? and deleted_at is null", "Bidding", id);
    }

    private String contractNumber(Long id) {
        return number("select contract_number from contracts where id = ? and deleted_at is null", "Contract", id);
    }

    private String number(String sql, String what, Long id) {
        List<String> v = jdbc.queryForList(sql, String.class, id);
        if (v.isEmpty()) throw ApiException.notFound(what);
        return v.get(0) == null ? String.valueOf(id) : v.get(0);
    }
}
