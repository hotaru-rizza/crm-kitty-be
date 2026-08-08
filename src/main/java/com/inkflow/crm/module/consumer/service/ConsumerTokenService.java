package com.inkflow.crm.module.consumer.service;

import com.inkflow.crm.common.exception.ApiException;
import com.inkflow.crm.common.exception.ErrorCode;
import com.inkflow.crm.module.consumer.entity.ConsumerUser;
import com.inkflow.crm.module.consumer.repository.ConsumerUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ConsumerTokenService {

    private final ConsumerUserRepository consumerUserRepository;

    public void assertCanAfford(ConsumerUser user, int cost) {
        if (user.getAiTokens() < cost) {
            throw new ApiException(ErrorCode.INSUFFICIENT_TOKENS);
        }
    }

    @Transactional
    public int chargeAndGetRemaining(UUID userId, int cost) {
        if (consumerUserRepository.spendTokens(userId, cost) == 0) {
            throw new ApiException(ErrorCode.INSUFFICIENT_TOKENS);
        }

        return consumerUserRepository.findAiTokensById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.INTERNAL_ERROR));
    }
}
