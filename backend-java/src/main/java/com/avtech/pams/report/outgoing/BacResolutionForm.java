package com.avtech.pams.report.outgoing;

import com.avtech.pams.common.ApiException;
import com.avtech.pams.report.coa.Xlsx;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import static com.avtech.pams.report.outgoing.OutgoingDocs.*;

/**
 * A BAC Resolution. For a resolution to award, the WHEREAS clauses are drawn from the bidding or RFQ it decides. Signed by
 * the BAC members and approved by the Head of the Procuring Entity; the signatures go on once the resolution is approved.
 */
@Component
public class BacResolutionForm {

    private static final String LAST = "F";
    private static final int CHARS = 94;

    private final JdbcTemplate jdbc;
    private final OutgoingDocs docs;

    public BacResolutionForm(JdbcTemplate jdbc, OutgoingDocs docs) {
        this.jdbc = jdbc;
        this.docs = docs;
    }

    public byte[] generate(Long id) {
        List<Map<String, Object>> found = jdbc.queryForList("""
                select r.*, m.meeting_date, m.venue, pr.pr_number from bac_resolutions r left join bac_meetings m on m.id = r.bac_meeting_id
                left join purchase_requests pr on pr.id = r.purchase_request_id where r.id = ? and r.deleted_at is null""", id);
        if (found.isEmpty()) throw ApiException.notFound("BAC resolution");
        Map<String, Object> r = found.get(0);
        String agency = or(docs.setting("agency_name"), "the Procuring Entity");
        boolean approved = r.get("approved_at") != null || "approved".equals(r.get("status"));

        List<Map<String, Object>> bidding = jdbc.queryForList("""
                select b.bidding_number ref, b.title, b.abc, b.awarded_amount amount, s.name winner, b.evaluation_method method, b.itb_posted_at,
                       (select count(*) from bidding_bidders bb where bb.bidding_id = b.id) offers
                from biddings b left join suppliers s on s.id = b.awarded_supplier_id where b.bac_resolution_id = ? and b.deleted_at is null""", id);
        List<Map<String, Object>> rfq = bidding.isEmpty() ? jdbc.queryForList("""
                select r.rfq_number ref, r.title, r.abc, q.total_amount amount, s.name winner, r.procurement_method method, r.negotiated_ground,
                       (select count(*) from rfq_quotations x where x.rfq_id = r.id) offers
                from rfqs r left join suppliers s on s.id = r.awarded_supplier_id
                left join rfq_quotations q on q.rfq_id = r.id and q.supplier_id = r.awarded_supplier_id and q.is_winner = ?
                where r.bac_resolution_id = ? and r.deleted_at is null""", true, id) : List.of();

        Xlsx x = docs.sheet("BAC Resolution", 16, 16, 16, 16, 16, 16);
        int row = docs.letterhead(x, LAST);
        x.merge("A" + row + ":F" + row);
        x.set("A" + row, "BIDS AND AWARDS COMMITTEE");
        x.style("A" + row).bold().center();
        row++;
        row = docs.title(x, row, LAST, null, "BAC RESOLUTION NO. " + str(r.get("resolution_number")));
        x.merge("A" + row + ":F" + row);
        x.set("A" + row, str(r.get("title")).toUpperCase(Locale.ROOT));
        x.style("A" + row).bold().center().wrap();
        x.height(row, 30);
        row += 2;

        Map<String, Object> p = !bidding.isEmpty() ? bidding.get(0) : !rfq.isEmpty() ? rfq.get(0) : null;
        if (p != null) {
            boolean bid = !bidding.isEmpty();
            String method = str(p.get("method"));
            String mode = bid ? "Competitive Bidding" + ("mearb".equals(method) ? " (MEARB)" : "marb".equals(method) ? " (MARB)" : "") : method;
            String basis = !bid ? "Lowest Calculated and Responsive Quotation"
                    : "mearb".equals(method) ? "Most Economically Advantageous Responsive Bid"
                    : "marb".equals(method) ? "Most Advantageous Responsive Bid" : "Lowest Calculated Responsive Bid";
            String[] whereas = {
                    "the " + agency + " requires the " + p.get("title") + (blank(str(r.get("pr_number"))) ? "" : " under Purchase Request No. " + r.get("pr_number"))
                            + ", with an Approved Budget for the Contract (ABC) of " + words(p.get("abc")).toUpperCase(Locale.ROOT) + " (" + peso(p.get("abc")) + ");",
                    "the procurement (" + p.get("ref") + ") was conducted through " + mode + " in accordance with Republic Act No. 12009 and its Implementing "
                            + "Rules and Regulations;",
                    "a total of " + p.get("offers") + " " + (bid ? "bid(s)" : "quotation(s)") + " were received and evaluated by the BAC;",
                    p.get("winner") == null ? "the evaluation has not yet determined a winning offer;"
                            : "after evaluation" + (bid ? " and post-qualification" : "") + ", " + p.get("winner") + " was found to have submitted the " + basis
                            + ", in the amount of " + words(p.get("amount")).toUpperCase(Locale.ROOT) + " (" + peso(p.get("amount")) + ");",
            };
            for (String w : whereas) {
                row = docs.para(x, row, "A", LAST, "WHEREAS, " + w, CHARS + 18);
                row++;
            }
            if (p.get("winner") != null) {
                row = docs.para(x, row, "A", LAST, "NOW, THEREFORE, we, the members of the Bids and Awards Committee, hereby RESOLVE, as it is hereby RESOLVED, "
                        + "to recommend to the Head of the Procuring Entity the award of the contract for the " + p.get("title") + " to " + p.get("winner")
                        + " in the amount of " + words(p.get("amount")).toUpperCase(Locale.ROOT) + " (" + peso(p.get("amount")) + ").", CHARS + 18);
                row++;
            }
        }
        if (!blank(str(r.get("content")))) {
            String content = str(r.get("content")).trim();
            row = docs.para(x, row, "A", LAST, (p == null || content.toUpperCase(Locale.ROOT).startsWith("RESOLVED") ? "" : "RESOLVED FURTHER, ") + content, CHARS + 18);
            row++;
        }
        row = docs.para(x, row, "A", LAST, "RESOLVED this " + (r.get("meeting_date") == null ? "____ day of ____________" : date(r.get("meeting_date")))
                + (blank(str(r.get("venue"))) ? "" : " at " + r.get("venue")) + ".", CHARS + 18);
        row += 2;
        row = RfqForms.bacSignatures(docs, x, row, LAST, approved);
        row++;
        row = docs.sign(x, row, "A", "C", "APPROVED / DISAPPROVED:", docs.hope(), r.get("approved_at") != null);
        x.page().printArea("A1:F" + row);
        return x.bytes();
    }
}
