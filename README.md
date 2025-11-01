# Books CRUD - Spring Boot + jOOQ + MS SQL Server

## Prereqs
- Java 21+, Maven
- SQL Server running (`localhost:1433`, DB `booksdb`). Update credentials in `application.properties` if needed.

## Generate jOOQ code from DDL (no live DB needed)
```bash
mvn -DskipTests=true generate-sources
```
This parses `src/main/resources/db/mssql/schema.sql` and generates jOOQ classes under `target/generated-sources/jooq` with package `com.example.jooq`.

## Run
```bash
mvn spring-boot:run
# UI
http://localhost:8080/books
# REST
curl "http://localhost:8080/api/books?q=java&page=0&size=10&sort=price,desc"
```

## Notes
- CRUD + pagination + search (title/author/ISBN)
- Thymeleaf UI + Bootstrap
- jOOQ is used via `DSLContext` in `BookService` (no JPA).


## Borrow/Return (Library loans)
- Tables: `MEMBER`, `LOAN`
- REST:
  - `POST /api/loans/borrow?bookId=..&memberId=..&days=14`
  - `POST /api/loans/{loanId}/return`
  - `GET  /api/loans?q=&status=&page=&size=`
- UI: `/loans` (filter by status, quick borrow form)

## Cron jobs
- `OverdueScheduler` runs daily at 08:00 (Asia/Ho_Chi_Minh) to mark overdue loans.

## Microservices examples
Under `microservices/` there are 3 minimal Spring Boot services:
- `inventory-service`: manage books inventory (skeleton)
- `borrow-service`: manage loan commands (skeleton)
- `notification-service`: send reminders (skeleton)

Each exposes `/api/<service>/...` and has a sample scheduled heartbeat.


## Overdue email notifications
- New tables: `NOTIFICATION`, `NOTIFICATION_HISTORY`
- Daily job `OverdueScheduler.markOverdueDaily()` also **creates notifications** for overdue, unreturned loans (if not already queued).
- New cron `EmailNotificationScheduler.sendPendingNotifications()` runs **every 5 minutes** (Asia/Ho_Chi_Minh):
  - Loads pending notifications (batch 50)
  - Sends email via Spring Mail (`JavaMailSender`)
  - On success: deletes from `NOTIFICATION`, inserts into `NOTIFICATION_HISTORY`
  - On failure: still archives with `SUCCESS=false` and error message

> Configure SMTP in `application.properties` (`spring.mail.*`) to actually send emails.


## Concurrency-safe notification processing (no Kafka)
- Fields added to `NOTIFICATION`: `PROCESS_ID`, `RETRY_COUNT` (max 3), `LOCKED_AT`, `TIMEOUT_SEC` (default 300s), `LAST_ERROR`, `LAST_ATTEMPT_AT`.
- Flow:
  1. Mỗi tiến trình có `processId` (UUID). Cron `EmailNotificationScheduler` gọi `claimBatch(processId, 300, 100)`:
     - **Steal** các bản ghi bị khoá quá `TIMEOUT_SEC` và `RETRY_COUNT < 3` (tăng retry + gán `processId`).
     - **Claim** các bản ghi chưa ai xử lý với `RETRY_COUNT < 3` (tăng retry + gán `processId`).
  2. Đọc các bản ghi `PROCESS_ID = processId` và gửi email.
  3. **Thành công** ➜ xóa khỏi `NOTIFICATION`, ghi `NOTIFICATION_HISTORY` (`SUCCESS=true`).  
     **Thất bại** ➜ `markFailure`: xóa lock (để cron khác claim), cập nhật `LAST_ERROR`, `LAST_ATTEMPT_AT`.  
     **Hết retry (>=3)** ➜ `archiveExhausted` đưa vào lịch sử với `SUCCESS=false` và xóa khỏi hàng đợi.
- Có index hỗ trợ: `IX_NOTIFICATION_PROCESS`, `IX_NOTIFICATION_LOCK`, `IX_NOTIFICATION_RETRY`.
