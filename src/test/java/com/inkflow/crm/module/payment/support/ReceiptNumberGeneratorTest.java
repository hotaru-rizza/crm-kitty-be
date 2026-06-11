package com.inkflow.crm.module.payment.support;

import com.inkflow.crm.config.InkflowProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReceiptNumberGeneratorTest {

    private ReceiptNumberGenerator generator;

    @BeforeEach
    void setUp() {
        InkflowProperties properties = mock(InkflowProperties.class);
        when(properties.defaultZoneId()).thenReturn(ZoneId.of("Europe/Kyiv"));
        generator = new ReceiptNumberGenerator(properties);
    }

    @Test
    void generate_returnsDatePrefixedReceiptNumber() {
        String receipt = generator.generate();

        assertNotNull(receipt);
        assertTrue(receipt.matches("\\d{8}-\\d{5}"));
    }

    @Test
    void generate_producesUniqueNumbers() {
        String first = generator.generate();
        String second = generator.generate();

        assertTrue(!first.equals(second));
    }

    @Test
    void shouldUseConfiguredTimezoneForDatePrefix() {
        String expectedPrefix = LocalDateTime.now(ZoneId.of("Europe/Kyiv"))
                .format(DateTimeFormatter.ofPattern("yyyyMMdd"));

        String receipt = generator.generate();

        assertTrue(receipt.startsWith(expectedPrefix + "-"));
    }

    @Test
    void shouldFormatSequenceWithFiveDigits() {
        String receipt = generator.generate();

        String sequence = receipt.substring(receipt.indexOf('-') + 1);
        assertEquals(5, sequence.length());
        assertTrue(sequence.chars().allMatch(Character::isDigit));
    }
}
