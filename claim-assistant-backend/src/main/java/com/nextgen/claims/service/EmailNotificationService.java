package com.nextgen.claims.service;

import com.nextgen.claims.docvalidation.model.ClaimEntity;
import com.nextgen.claims.repository.UserRepository;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.util.Properties;

@Slf4j
@Service
public class EmailNotificationService {

    private final UserRepository userRepository;

    @Value("${app.notification.primary.username}")
    private String primaryUsername;

    @Value("${app.notification.primary.password}")
    private String primaryPassword;

    @Value("${app.notification.fallback.username}")
    private String fallbackUsername;

    @Value("${app.notification.fallback.password}")
    private String fallbackPassword;

    public EmailNotificationService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public void sendClaimDecisionEmail(ClaimEntity claim) {
        userRepository.findByCustomerId(claim.getCustomerId()).ifPresent(user -> {
            boolean sent = trySend(buildMailSender(primaryUsername, primaryPassword),
                    primaryUsername, "sumathiatk@gmail.com", "Karna", claim);
            if (!sent) {
                log.warn("[EmailNotification] primary sender failed, trying fallback for claim={}", claim.getClaimId());
                trySend(buildMailSender(fallbackUsername, fallbackPassword),
                        fallbackUsername, "sumathiatk@gmail.com", "Karna", claim);
            }
        });
    }

    private boolean trySend(JavaMailSender sender, String fromEmail,
                             String toEmail, String userName, ClaimEntity claim) {
        try {
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject(buildSubject(claim));
            helper.setText(buildBody(userName, claim), false);
            sender.send(message);
            log.info("[EmailNotification] claim={} sent from={} to={}", claim.getClaimId(), fromEmail, toEmail);
            return true;
        } catch (Exception e) {
            log.error("[EmailNotification] claim={} send failed from={}: {}", claim.getClaimId(), fromEmail, e.getMessage());
            return false;
        }
    }

    private JavaMailSender buildMailSender(String username, String password) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost("smtp.gmail.com");
        sender.setPort(587);
        sender.setUsername(username);
        sender.setPassword(password);

        Properties props = sender.getJavaMailProperties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.starttls.required", "true");
        props.put("mail.smtp.connectiontimeout", "5000");
        props.put("mail.smtp.timeout", "5000");
        return sender;
    }

    private String buildSubject(ClaimEntity claim) {
        String claimId = claim.getClaimId();
        return switch (claim.getDecision().getDecision()) {
            case APPROVED      -> "Your Claim Has Been Approved – " + claimId;
            case REJECTED      -> "Your Claim Has Been Rejected – " + claimId;
            case MANUAL_REVIEW -> "Your Claim Is Under Manual Review – " + claimId;
        };
    }

    private String buildBody(String userName, ClaimEntity claim) {
        var decision = claim.getDecision();
        String extra = decision.getDecision().name().equals("MANUAL_REVIEW")
                ? "A human adjudicator will review your claim shortly and contact you with the outcome.\n"
                : "";

        return """
                Dear %s,

                Your %s insurance claim (ID: %s) has been processed by ClaimXpert.

                Decision   : %s
                Reason     : %s
                Confidence : %.0f%%

                %s
                If you have any questions, please contact our support team with your Claim ID.

                Regards,
                ClaimXpert Team
                """.formatted(
                userName,
                claim.getClaimType(),
                claim.getClaimId(),
                decision.getDecision(),
                decision.getReason(),
                decision.getConfidence() * 100,
                extra
        );
    }
}
