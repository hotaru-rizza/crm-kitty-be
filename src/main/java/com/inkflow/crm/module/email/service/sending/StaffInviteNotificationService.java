package com.inkflow.crm.module.email.service.sending;

import com.inkflow.crm.config.InkflowProperties;
import com.inkflow.crm.domain.entity.Staff;
import com.inkflow.crm.domain.entity.StaffInvite;
import com.inkflow.crm.domain.enums.UserRole;
import com.inkflow.crm.domain.repository.StaffRepository;
import com.inkflow.crm.module.email.dto.EmailRecipient;
import com.inkflow.crm.module.email.dto.EmailTenantContext;
import com.inkflow.crm.module.email.dto.NotificationCommand;
import com.inkflow.crm.module.email.enums.TemplateKey;
import com.inkflow.crm.module.email.enums.TemplateVar;
import com.inkflow.crm.module.email.service.EmailTenantContextLoader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class StaffInviteNotificationService {

    private static final Map<UserRole, String> ROLE_LABELS_UK = Map.of(
            UserRole.ADMIN, "Адміністратор",
            UserRole.ARTIST, "Майстер"
    );

    private final NotificationSender notificationSender;
    private final EmailTenantContextLoader tenantContextLoader;
    private final StaffRepository staffRepository;
    private final InkflowProperties inkflowProperties;

    public boolean sendTeamInvite(StaffInvite invite) {
        try {
            EmailTenantContext context = tenantContextLoader.loadContext(invite.getTenantId());
            notificationSender.send(NotificationCommand.forTenant(
                    invite.getTenantId(),
                    EmailRecipient.of(invite.getEmail(), null),
                    TemplateKey.TEAM_INVITE,
                    buildVariables(invite),
                    invite.getId(),
                    context
            ));
            return true;
        } catch (Exception exception) {
            log.warn("Failed to send team invite email: tenantId={} inviteId={} email={} error={}",
                    invite.getTenantId(), invite.getId(), invite.getEmail(), exception.getMessage());
            return false;
        }
    }

    private Map<String, String> buildVariables(StaffInvite invite) {
        return Map.of(
                TemplateVar.INVITER_NAME.getPlaceholder(), resolveInviterName(invite.getInvitedBy()),
                TemplateVar.ROLE.getPlaceholder(), roleLabel(invite.getRole()),
                TemplateVar.ACTION_URL.getPlaceholder(), buildInviteUrl(invite.getToken())
        );
    }

    private String buildInviteUrl(String token) {
        String baseUrl = inkflowProperties.getFrontendUrl().replaceAll("/+$", "");
        return baseUrl + "/invite/" + token;
    }

    private String resolveInviterName(UUID inviterId) {
        return staffRepository.findByIdAndDeletedAtIsNull(inviterId)
                .map(Staff::getFullName)
                .filter(name -> !name.isBlank())
                .orElse("Адміністратор");
    }

    private String roleLabel(UserRole role) {
        return ROLE_LABELS_UK.getOrDefault(role, role.getDescription());
    }
}
