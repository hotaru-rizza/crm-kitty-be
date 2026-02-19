package com.inkflow.crm.module.monobank.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Webhook payload sent by Monobank Acquiring API.
 * Docs: https://api.monobank.ua/docs/acquiring.html#tag/Merchantapi/operation/invoiceWebhook
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class MonobankWebhookPayload {

    @JsonProperty("invoiceId")
    private String invoiceId;

    /** created | processing | hold | success | failure | reversed | expired */
    @JsonProperty("status")
    private String status;

    /** Amount in kopecks */
    @JsonProperty("amount")
    private Long amount;

    @JsonProperty("ccy")
    private Integer ccy;

    @JsonProperty("finalAmount")
    private Long finalAmount;

    @JsonProperty("createdDate")
    private String createdDate;

    @JsonProperty("modifiedDate")
    private String modifiedDate;

    /** Our own reference — appointmentId */
    @JsonProperty("reference")
    private String reference;

    @JsonProperty("paymentInfo")
    private PaymentInfo paymentInfo;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PaymentInfo {
        @JsonProperty("maskedPan")
        private String maskedPan;

        @JsonProperty("approvalCode")
        private String approvalCode;

        @JsonProperty("rrn")
        private String rrn;

        @JsonProperty("paymentSystem")
        private String paymentSystem;

        @JsonProperty("country")
        private String country;

        @JsonProperty("fee")
        private Long fee;

        public BigDecimal getFeeDecimal() {
            return fee != null ? BigDecimal.valueOf(fee).movePointLeft(2) : BigDecimal.ZERO;
        }
    }

    /** Converts Monobank kopecks to UAH */
    public BigDecimal getAmountDecimal() {
        return amount != null ? BigDecimal.valueOf(amount).movePointLeft(2) : BigDecimal.ZERO;
    }
}
