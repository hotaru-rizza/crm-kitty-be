package com.inkflow.crm.module.client.service;

import com.inkflow.crm.domain.repository.ClientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class ClientDormancyService {

    private final ClientRepository clientRepository;

    @Transactional
    public DormancyResult processDormancy(Instant cutoff) {
        int markedDormant = clientRepository.markDormantClients(cutoff);
        int reactivated = clientRepository.reactivateDormantClients(cutoff);
        return new DormancyResult(markedDormant, reactivated);
    }

    public record DormancyResult(int markedDormant, int reactivated) {}
}
