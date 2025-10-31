package com.example.books;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
public class OverdueScheduler {

    private static final Logger log = LoggerFactory.getLogger(OverdueScheduler.class);

    private final LoanService loanService;

    private final NotificationService notificationService;

    public OverdueScheduler(LoanService loanService, NotificationService notificationService) {
        this.loanService = loanService;
        this.notificationService = notificationService;
    }

    // Run at 08:00 every day (Asia/Ho_Chi_Minh)
    @Scheduled(cron = "0 0 8 * * *", zone = "Asia/Ho_Chi_Minh")
    public void markOverdueDaily() {
        int updated = loanService.markOverdueAndCount();
        if (updated > 0) {
            log.warn("Marked {} loan(s) as OVERDUE", updated);
        } else {
            log.info("No overdue loans to mark today");
        }
            try {
            int notif = notificationService.createOverdueNotifications();
            if (notif > 0) {
                log.warn("Created {} overdue notification(s)", notif);
            }
        } catch (Exception ex) {
            log.error("Failed to create overdue notifications", ex);
        }
    }
}
