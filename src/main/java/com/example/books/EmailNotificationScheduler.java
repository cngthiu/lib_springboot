package com.example.books;

import org.jooq.Record;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

import static com.example.jooq.tables.Notification.NOTIFICATION;

@Component
@EnableScheduling
public class EmailNotificationScheduler {

    private static final Logger log = LoggerFactory.getLogger(EmailNotificationScheduler.class);

    private final String processId = UUID.randomUUID().toString();
    private final NotificationService notificationService;
    private final JavaMailSender mailSender;

    public EmailNotificationScheduler(NotificationService notificationService, JavaMailSender mailSender) {
        this.notificationService = notificationService;
        this.mailSender = mailSender;
    }

    @Scheduled(cron = "0 */5 * * * *", zone = "Asia/Ho_Chi_Minh")
    public void sendPendingNotifications() {
        int claimed = notificationService.claimBatch(processId, 300, 100);
        if (claimed > 0) {
            log.info("Process {} claimed {} notification(s)", processId, claimed);
        }

        List<Record> pending = notificationService.fetchMine(processId, 100);
        if (pending.isEmpty()) {
            log.info("{} has nothing to send", processId);
            return;
        }

        for (Record r : pending) {
            long id = (Long) r.get(NOTIFICATION.ID);
            String to = r.get(NOTIFICATION.EMAIL);
            String subject = r.get(NOTIFICATION.SUBJECT);
            String content = r.get(NOTIFICATION.CONTENT);
            Integer retryCount = r.get(NOTIFICATION.RETRY_COUNT);

            try {
                SimpleMailMessage msg = new SimpleMailMessage();
                msg.setTo(to);
                msg.setSubject(subject);
                msg.setText(content);
                mailSender.send(msg);

                notificationService.archiveAndDelete(id, true, null);
                log.info("[{}] Sent notification id={} to {}", processId, id, to);

            } catch (MailException ex) {
                handleFailure(id, to, retryCount, ex.getMessage());
            } catch (Exception ex) {
                handleFailure(id, to, retryCount, ex.toString());
            }
        }
    }

    private void handleFailure(long id, String to, Integer retryCount, String error) {
        if (retryCount != null && retryCount >= 3) {
            notificationService.archiveExhausted(id);
            log.error("[{}] Exhausted retries for id={} to {}: {}", processId, id, to, error);
        } else {
            notificationService.markFailure(id, error);
            log.error("[{}] Failed send id={} to {} (will retry): {}", processId, id, to, error);
        }
    }
}