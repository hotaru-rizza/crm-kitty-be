package com.inkflow.crm.module.consumer.service;

import com.inkflow.crm.common.exception.ApiException;
import com.inkflow.crm.common.exception.ErrorCode;
import com.inkflow.crm.module.consumer.entity.ConsumerUser;
import com.inkflow.crm.module.consumer.repository.ConsumerUserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConsumerTokenServiceTest {

    @Mock
    private ConsumerUserRepository consumerUserRepository;

    @InjectMocks
    private ConsumerTokenService consumerTokenService;

    @Test
    void assertCanAfford_whenBalanceTooLow_throwsInsufficientTokens() {
        ConsumerUser user = new ConsumerUser(UUID.randomUUID(), "user@test.com", "User");
        user.setAiTokens(0);

        ApiException ex = assertThrows(ApiException.class, () -> consumerTokenService.assertCanAfford(user, 1));

        assertEquals(ErrorCode.INSUFFICIENT_TOKENS, ex.getErrorCode());
    }

    @Test
    void chargeAndGetRemaining_whenSpendSucceeds_returnsUpdatedBalance() {
        UUID userId = UUID.randomUUID();
        when(consumerUserRepository.spendTokens(userId, 1)).thenReturn(1);
        when(consumerUserRepository.findAiTokensById(userId)).thenReturn(Optional.of(4));

        int remaining = consumerTokenService.chargeAndGetRemaining(userId, 1);

        assertEquals(4, remaining);
        verify(consumerUserRepository).spendTokens(userId, 1);
    }

    @Test
    void chargeAndGetRemaining_whenSpendFails_throwsInsufficientTokens() {
        UUID userId = UUID.randomUUID();
        when(consumerUserRepository.spendTokens(userId, 1)).thenReturn(0);

        ApiException ex = assertThrows(ApiException.class, () -> consumerTokenService.chargeAndGetRemaining(userId, 1));

        assertEquals(ErrorCode.INSUFFICIENT_TOKENS, ex.getErrorCode());
    }
}
