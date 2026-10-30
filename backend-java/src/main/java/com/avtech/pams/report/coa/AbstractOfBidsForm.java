package com.avtech.pams.report.coa;

import com.avtech.pams.common.ApiException;
import com.avtech.pams.report.coa.Xlsx.Edge;
import com.avtech.pams.report.coa.Xlsx.H;
import com.avtech.pams.report.coa.Xlsx.Line;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * RA 12009 Abstract of Bids — the BAC's tabulation of bids with the eligibility/technical/financial/
 * post-qualification results and the recommended bidder.
 */
@Component
public class AbstractOfBidsForm {

    private static final String FONT = "Calibri";
    private static final List<String> STAGES = List.of("eligibility_result", "technical_result", "financial_result", "post_qual_result");

    private final JdbcTemplate jdbc;

    public AbstractOfBidsForm(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public byte[] generate(Long biddingId, String entityName) {
        List<Map<String, Object>> found = jdbc.queryForList(
                "select bidding_number, title, abc, evaluation_method from biddings where id = ? and deleted_at is null", biddingId);
        if (found.isEmpty()) throw ApiException.notFound("Bidding");
        Map<String, Object> bidding = found.get(0);
        List<Map<String, Object>> bidders = jdbc.queryForList("""
                select b.*, s.name as supplier_name
                from bidding_bidders b
                left join suppliers s on s.id = b.supplier_id
                where b.bidding_id = ?
                order by b.id""", biddingId);

        Xlsx x = new Xlsx("Abstract of Bids", FONT, 10);
        // PhpSpreadsheet's default page setup: Letter, default orientation.
        x.page().paper(Xlsx.LETTER);

        x.merge("A1:I1");
        x.set("A1", empty(entityName) ? "[Agency / Local Government Unit]" : entityName);
        x.style("A1").bold().size(12);
        x.style("A1").align(H.CENTER);

        x.merge("A2:I2");
        x.set("A2", "ABSTRACT OF BIDS");
        x.style("A2").bold().size(13);
        x.style("A2").align(H.CENTER);

        x.merge("A4:I4");
        x.set("A4", "Bidding No.: " + str(bidding.get("bidding_number")) + "   |   " + str(bidding.get("title")));
        x.style("A4").bold();
        x.merge("A5:I5");
        BigDecimal abc = bidding.get("abc") instanceof BigDecimal b ? b : BigDecimal.ZERO;
        x.set("A5", "Approved Budget for the Contract (ABC): ₱" + numberFormat(abc)
                + "   |   Method: " + ("mearb".equals(bidding.get("evaluation_method")) ? "MEARB (rated)" : "marb".equals(bidding.get("evaluation_method")) ? "MARB (rated)" : "Lowest Calculated Bid"));

        // Header row
        int headRow = 7;
        String[] headers = {"#", "Bidder", "Bid Amount", "Bid Security", "Eligibility", "Technical", "Financial", "Post-Qual", "Remarks"};
        String[] cols = {"A", "B", "C", "D", "E", "F", "G", "H", "I"};
        for (int i = 0; i < headers.length; i++) {
            x.set(cols[i] + headRow, headers[i]);
        }
        x.style("A" + headRow + ":I" + headRow).bold();
        x.style("A" + headRow + ":I" + headRow).align(H.CENTER);

        int row = headRow + 1;
        int n = 1;
        for (Map<String, Object> b : bidders) {
            BigDecimal bid = (BigDecimal) b.get("bid_amount");
            boolean overAbc = abc.signum() > 0 && bid != null && bid.compareTo(abc) > 0;
            String form = (String) b.get("bid_security_form");
            x.set("A" + row, n++);
            x.set("B" + row, b.get("supplier_name") != null ? b.get("supplier_name") : "Supplier #" + b.get("supplier_id"));
            x.set("C" + row, bid != null ? Xlsx.bind(numberFormat(bid)) : "");
            x.set("D" + row, Boolean.TRUE.equals(b.get("bid_security_posted")) ? "Posted" + (empty(form) ? "" : " (" + form + ")") : "Not posted");
            x.set("E" + row, ucfirst((String) b.get("eligibility_result")));
            x.set("F" + row, ucfirst((String) b.get("technical_result")));
            x.set("G" + row, ucfirst((String) b.get("financial_result")));
            x.set("H" + row, ucfirst((String) b.get("post_qual_result")));
            String remarks = (String) b.get("remarks");
            x.set("I" + row, overAbc ? "EXCEEDS ABC" : (empty(remarks) ? "" : remarks));
            x.style("A" + row + ":I" + row).border(Edge.ALL, Line.THIN);
            x.style("C" + row + ":H" + row).align(H.CENTER);
            row++;
        }
        x.style("A" + headRow + ":I" + (row - 1)).border(Edge.ALL, Line.THIN);

        // Recommendation
        row += 1;
        Map<String, Object> recommended = bidders.stream()
                .filter(b -> {
                    BigDecimal bid = (BigDecimal) b.get("bid_amount");
                    boolean within = abc.signum() <= 0 || bid == null || bid.compareTo(abc) <= 0;
                    boolean passed = STAGES.stream().allMatch(s -> "passed".equals(b.get(s)));
                    return within && passed && bid != null;
                })
                .min(Comparator.comparing(b -> (BigDecimal) b.get("bid_amount")))
                .orElse(null);
        String recName = recommended != null
                ? str(recommended.get("supplier_name")) + " — ₱" + numberFormat((BigDecimal) recommended.get("bid_amount"))
                : "None (no responsive bid within ABC)";
        x.merge("A" + row + ":I" + row);
        x.set("A" + row, "Recommended (Lowest Calculated Responsive Bid): " + recName);
        x.style("A" + row).bold();

        // Signatures
        row += 3;
        x.set("B" + row, "Prepared by: BAC Secretariat");
        x.set("F" + row, "Approved by: BAC Chairperson");

        // PhpSpreadsheet's auto-size leaves the merged title/recommendation rows out of the widths.
        for (String c : cols) {
            x.autoSizeSkippingMerges(c);
        }

        return x.bytes();
    }

    /** PHP number_format($n, 2). */
    private static String numberFormat(BigDecimal n) {
        DecimalFormat f = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.ROOT));
        f.setRoundingMode(RoundingMode.HALF_UP);
        return f.format(n);
    }

    private static String ucfirst(String s) {
        if (s == null || s.isEmpty()) return "";
        char c = s.charAt(0);
        return (c >= 'a' && c <= 'z' ? String.valueOf((char) (c - 32)) : String.valueOf(c)) + s.substring(1);
    }

    /** PHP's empty() for a string: null, "" or "0". */
    private static boolean empty(String s) {
        return s == null || s.isEmpty() || "0".equals(s);
    }

    private static String str(Object v) {
        return v == null ? "" : v.toString();
    }
}
