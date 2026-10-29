package com.avtech.pams.report.coa;

import com.avtech.pams.report.coa.Xlsx.Edge;
import com.avtech.pams.report.coa.Xlsx.Line;
import com.avtech.pams.report.coa.Xlsx.V;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Petty Cash Voucher (PCV) — COA Appendix 50, and the Report of Petty Cash replenishment that
 * lists the approved vouchers a replenishment restores, with the fund's cash position.
 */
@Component
public class PettyCashVoucherForm {

    private static final String FONT = "Arial";

    private final Signatories signatories;

    public PettyCashVoucherForm(Signatories signatories) {
        this.signatories = signatories;
    }

    public record Voucher(String entityName, String fundCluster, String fundName, String pcvNumber, LocalDate date, String payee, String particulars,
                          String responsibilityCenter, BigDecimal amount, String approvedBy, String custodian, String status) {
    }

    public record Entry(LocalDate date, String pcvNumber, String payee, String particulars, String responsibilityCenter, BigDecimal amount) {
    }

    public record Replenishment(String entityName, String fundCluster, String fundName, String custodian, String reportNumber, LocalDate reportDate,
                                BigDecimal fundAmount, BigDecimal cashOnHand, List<Entry> lines, BigDecimal total, String status, String reference,
                                LocalDate replenishedDate) {
    }

    public byte[] voucher(Voucher v) {
        Xlsx x = new Xlsx("PCV", FONT, 10);
        double[] widths = {18, 14, 14, 14, 14, 18};
        for (int i = 0; i < widths.length; i++) x.width(Xlsx.col(i + 1), widths[i]);
        x.set("F1", "Appendix 50");
        x.style("F1").italic().right();
        x.merge("A3:F3");
        x.set("A3", "PETTY CASH VOUCHER");
        x.style("A3").bold().size(14).center();

        pair(x, 5, "Entity Name :", OrsForm.or(v.entityName(), "____________________"), "PCV No. :", v.pcvNumber());
        pair(x, 6, "Fund Cluster :", OrsForm.or(v.fundCluster(), "____________________"), "Date :", v.date() == null ? "" : v.date().format(OrsForm.DATE));
        x.style("A5:F6").border(Edge.OUTLINE, Line.MEDIUM);

        x.set("A7", "Payee/Office :");
        x.merge("B7:F7");
        x.set("B7", v.payee());
        x.set("A8", "Charge to :");
        x.merge("B8:F8");
        x.set("B8", OrsForm.or(v.responsibilityCenter(), "") + (v.fundName() == null ? "" : "  (" + v.fundName() + ")"));
        x.style("A7:A8").bold();
        x.style("A7:F8").border(Edge.BOTTOM, Xlsx.Line.THIN);
        x.style("A7:F8").border(Edge.OUTLINE, Line.MEDIUM);

        x.merge("A9:E9");
        x.set("A9", "Particulars");
        x.set("F9", "Amount");
        x.style("A9:F9").bold().center().border(Edge.ALL, Line.THIN);
        x.merge("A10:E10");
        x.set("A10", v.particulars());
        x.set("F10", v.amount());
        x.style("A10:F10").valign(V.TOP).wrap().border(Edge.ALL, Line.THIN);
        x.height(10, 70);
        x.merge("A11:E11");
        x.set("A11", "Total");
        x.style("A11").bold().right();
        x.set("F11", v.amount());
        x.style("F10:F11").numberFormat(OrsForm.MONEY).right();
        x.style("A11:F11").bold().border(Edge.ALL, Line.THIN);
        x.style("A9:F11").border(Edge.OUTLINE, Line.MEDIUM);

        Signatories.Signatory none = new Signatories.Signatory(null, null, null, null);
        Signatories.Signatory approver = new Signatories.Signatory(v.approvedBy(), null, null, null);
        Signatories.Signatory custodian = new Signatories.Signatory(v.custodian(), null, null, null);
        Signatories.Signatory payee = new Signatories.Signatory(v.payee(), null, null, null);
        box(x, 12, "A", "C", "A.  Requested by:", none, "Signature over Printed Name of Requesting Officer");
        box(x, 12, "D", "F", "B.  Approved by:", approver, "Signature over Printed Name of Supervisor/Authorized Official");
        box(x, 17, "A", "C", "C.  Paid by:", custodian, "Signature over Printed Name of Petty Cash Custodian");
        box(x, 17, "D", "F", "D.  Cash received by:", payee, "Signature over Printed Name of Payee");
        x.merge("A22:F22");
        x.set("A22", "Supporting documents attached:  " + DisbursementVoucherForm.box(false) + " Official Receipt   " + DisbursementVoucherForm.box(false)
                + " Sales Invoice   " + DisbursementVoucherForm.box(false) + " Others ____________");
        x.style("A22").size(9);
        x.style("A22:F22").border(Edge.OUTLINE, Line.MEDIUM);
        if ("cancelled".equals(v.status())) {
            x.merge("A24:F24");
            x.set("A24", "CANCELLED");
            x.style("A24").bold().size(16).center();
        }
        x.page().portrait().paper(Xlsx.A4).fitToWidth(1).fitToHeight(0).margins(0.5, 0.5, 0.5, 0.5);
        return x.bytes();
    }

    public byte[] replenishment(Replenishment rp) {
        Xlsx x = new Xlsx("Replenishment", FONT, 10);
        double[] widths = {13, 17, 24, 36, 16, 16};
        for (int i = 0; i < widths.length; i++) x.width(Xlsx.col(i + 1), widths[i]);
        x.merge("A1:F1");
        x.set("A1", "REPORT OF PETTY CASH REPLENISHMENT");
        x.style("A1").bold().size(14).center();
        x.merge("A2:F2");
        x.set("A2", OrsForm.or(rp.entityName(), "____________________"));
        x.style("A2").bold().underline().center();
        x.merge("A3:F3");
        x.set("A3", "Entity Name");
        x.style("A3").italic().size(9).center();
        pair(x, 5, "Fund :", rp.fundName(), "Report No. :", rp.reportNumber());
        pair(x, 6, "Fund Cluster :", OrsForm.or(rp.fundCluster(), ""), "Date :", rp.reportDate() == null ? "" : rp.reportDate().format(OrsForm.DATE));
        pair(x, 7, "Custodian :", OrsForm.or(rp.custodian(), ""), "Status :", "replenished".equals(rp.status())
                ? "Replenished " + (rp.replenishedDate() == null ? "" : rp.replenishedDate()) + (rp.reference() == null ? "" : " (" + rp.reference() + ")") : "For replenishment");

        String[] heads = {"Date", "PCV No.", "Payee", "Particulars", "Responsibility Center", "Amount"};
        for (int i = 0; i < heads.length; i++) x.set(Xlsx.col(i + 1) + 9, heads[i]);
        x.style("A9:F9").bold().center().vcenter().wrap().border(Edge.ALL, Line.THIN);
        int r = 10;
        for (Entry l : rp.lines()) {
            x.set("A" + r, l.date() == null ? "" : l.date().toString());
            x.set("B" + r, l.pcvNumber());
            x.set("C" + r, l.payee());
            x.set("D" + r, l.particulars());
            x.set("E" + r, OrsForm.or(l.responsibilityCenter(), ""));
            x.set("F" + r, l.amount());
            x.style("A" + r + ":F" + r).valign(V.TOP).wrap().border(Edge.ALL, Line.THIN);
            r++;
        }
        x.merge("A" + r + ":E" + r);
        x.set("A" + r, "Total disbursements for replenishment");
        x.style("A" + r).right();
        x.set("F" + r, rp.total());
        x.style("A" + r + ":F" + r).bold().border(Edge.ALL, Line.THIN);
        x.style("F10:F" + r).numberFormat(OrsForm.MONEY).right();
        x.style("A9:F" + r).border(Edge.OUTLINE, Line.MEDIUM);

        r += 2;
        summary(x, r++, "Petty cash fund", rp.fundAmount());
        summary(x, r++, "Less: Disbursements per this report", rp.total());
        summary(x, r++, "Cash on hand", rp.cashOnHand());
        summary(x, r, "Amount requested for replenishment", rp.total());
        x.style("E" + r + ":F" + r).bold();

        r += 2;
        x.merge("A" + r + ":F" + r);
        x.set("A" + r, "I hereby certify that the foregoing is a true and correct report of the petty cash disbursements made by me, "
                + "supported by the petty cash vouchers and receipts listed above.");
        x.style("A" + r).wrap();
        x.height(r, 28);
        r += 2;
        Signatories.Signatory custodian = new Signatories.Signatory(rp.custodian(), null, null, null);
        Signatories.Signatory accountant = signatories.resolve("accountant");
        Signatories.Signatory head = signatories.resolve("head_of_agency");
        sign(x, r, "A", "B", "Certified correct:", custodian, "Petty Cash Custodian");
        sign(x, r, "C", "D", "Reviewed:", accountant, "Head, Accounting Unit/Authorized Representative");
        sign(x, r, "E", "F", "Approved for replenishment:", head, "Agency Head/Authorized Representative");

        x.page().landscape().paper(Xlsx.A4).fitToWidth(1).fitToHeight(0).margins(0.5, 0.4, 0.5, 0.4);
        return x.bytes();
    }

    private static void summary(Xlsx x, int r, String label, BigDecimal v) {
        x.set("E" + r, label);
        x.style("E" + r).right();
        x.set("F" + r, v);
        x.style("F" + r).numberFormat(OrsForm.MONEY).right().border(Edge.BOTTOM, Line.THIN);
    }

    private static void sign(Xlsx x, int r, String from, String to, String caption, Signatories.Signatory s, String position) {
        x.merge(from + r + ":" + to + r);
        x.set(from + r, caption);
        x.style(from + r).italic();
        x.merge(from + (r + 2) + ":" + to + (r + 2));
        Signatories.stamp(x, from + (r + 1), from + (r + 2), s, 30);
        x.style(from + (r + 2) + ":" + to + (r + 2)).center().border(Edge.BOTTOM, Line.THIN);
        x.merge(from + (r + 3) + ":" + to + (r + 3));
        x.set(from + (r + 3), position);
        x.style(from + (r + 3)).size(8).center();
        x.height(r + 1, 30);
    }

    private static void box(Xlsx x, int r, String from, String to, String caption, Signatories.Signatory s, String position) {
        x.merge(from + r + ":" + to + r);
        x.set(from + r, caption);
        x.style(from + r).bold();
        x.merge(from + (r + 2) + ":" + to + (r + 2));
        Signatories.stamp(x, from + (r + 1), from + (r + 2), s, 30);
        x.style(from + (r + 2) + ":" + to + (r + 2)).center().border(Edge.BOTTOM, Line.THIN);
        x.merge(from + (r + 3) + ":" + to + (r + 3));
        x.set(from + (r + 3), position);
        x.style(from + (r + 3)).size(8).center().wrap();
        x.set(from + (r + 4), "Date: ____________");
        x.style(from + (r + 4)).size(9);
        x.height(r + 1, 30);
        x.style(from + r + ":" + to + (r + 4)).border(Edge.OUTLINE, Line.MEDIUM);
    }

    private static void pair(Xlsx x, int r, String l1, String v1, String l2, String v2) {
        x.set("A" + r, l1);
        x.style("A" + r).bold();
        x.merge("B" + r + ":D" + r);
        x.set("B" + r, v1 == null ? "" : v1);
        x.set("E" + r, l2);
        x.style("E" + r).bold().right();
        x.set("F" + r, v2 == null ? "" : v2);
    }
}
