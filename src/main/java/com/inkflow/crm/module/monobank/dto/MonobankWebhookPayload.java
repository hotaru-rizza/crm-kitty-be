package com.inkflow.crm.module.monobank.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;


@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class MonobankWebhookPayload {

    @JsonProperty("invoiceId")
    private String invoiceId;


    @JsonProperty("status")
    private String status;


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


    public BigDecimal getAmountDecimal() {
        return amount != null ? BigDecimal.valueOf(amount).movePointLeft(2) : BigDecimal.ZERO;
    }
}
