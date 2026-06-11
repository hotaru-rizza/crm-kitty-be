package com.inkflow.crm.module.payment.support;

import com.inkflow.crm.config.InkflowProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicLong;

@Component
@RequiredArgsConstructor
public class ReceiptNumberGenerator {

    private static final AtomicLong RECEIPT_COUNTER = new AtomicLong(System.currentTimeMillis() % 100_000);
    private static final DateTimeFormatter DATE_PREFIX_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final InkflowProperties inkflowProperties;

    public String generate() {
        String datePrefix = LocalDateTime.now(inkflowProperties.defaultZoneId()).format(DATE_PREFIX_FORMAT);
        long sequenceNum = RECEIPT_COUNTER.incrementAndGet() % 100_000;
        return String.format("%s-%05d", datePrefix, sequenceNum);
    }
}
