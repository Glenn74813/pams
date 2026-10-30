package com.avtech.pams.report;

import com.avtech.pams.report.coa.XlsxPdf;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * Any COA form download answers as a PDF when the request says {@code format=pdf} (TOR 1.6.2): the
 * Excel file the form builds is drawn on PDF pages, so forms keep one layout and one code path.
 */
@ControllerAdvice
public class PdfFormAdvice implements ResponseBodyAdvice<Object> {

    private static final MediaType XLSX = MediaType.parseMediaType(CoaExportController.XLSX);

    /** True when this request wants the PDF of a form. */
    private static boolean wantsPdf() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes a)) return false;
        HttpServletRequest req = a.getRequest();
        return "pdf".equalsIgnoreCase(req.getParameter("format"));
    }

    /** The file name to record in the audit trail for a form's download. */
    public static String fileName(String xlsxName) {
        return wantsPdf() ? xlsxName.replaceAll("(?i)\\.xlsx$", ".pdf") : xlsxName;
    }

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType, Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request, ServerHttpResponse response) {
        if (!(body instanceof byte[] file) || !XLSX.isCompatibleWith(selectedContentType) || !wantsPdf()) return body;
        byte[] pdf = XlsxPdf.convert(file);
        HttpHeaders h = response.getHeaders();
        h.setContentType(MediaType.APPLICATION_PDF);
        h.setContentLength(pdf.length);
        String disposition = h.getFirst(HttpHeaders.CONTENT_DISPOSITION);
        if (disposition != null) h.set(HttpHeaders.CONTENT_DISPOSITION, disposition.replace(".xlsx", ".pdf"));
        return pdf;
    }
}
