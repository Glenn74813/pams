package com.avtech.pams.report.coa;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One payment tranche as the Obligation Request and Status and the Disbursement Voucher print it.
 * {@code amount} is the gross claim; the deductions and {@code net} are known once it is paid (or
 * are the figures the accountant proposes).
 */
public record PaymentClaim(String entityName, String fundCluster, String orsNumber, String dvNumber, LocalDate date, String payee, String tin,
                           String address, String particulars, String responsibilityCenter, String mfoPap, String uacsCode, BigDecimal amount,
                           BigDecimal retention, BigDecimal liquidatedDamages, BigDecimal withholdingTax, BigDecimal net, String paymentMethod,
                           String paymentReference, LocalDate paidDate, String status) {
}
