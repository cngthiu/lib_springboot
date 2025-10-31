package com.example.ms.notificationservice;

import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/notification-service")
public class NotificationServiceController {
    // NOTE: This is a minimal skeleton for demo. Replace with real DB/jOOQ in production.
    private final Map<Long, Map<String,Object>> store = new HashMap<>();

    @GetMapping
    public List<Map<String,Object>> list() { return new ArrayList<>(store.values()); }

    @PostMapping
    public Map<String,Object> create(@RequestBody Map<String,Object> body) {
        long id = store.size()+1;
        body.put("id", id);
        store.put(id, body);
        return body;
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() { return ResponseEntity.ok("ok"); }

    // Example cron in this service every minute (UTC): just heartbeat log
    @Scheduled(cron = "0 * * * * *")
    public void tick() {
        System.out.println("[notification-service] heartbeat " + new Date());
    }
}
