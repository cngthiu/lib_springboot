package com.example.books;

import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

import static com.example.jooq.tables.Notification.NOTIFICATION;
import static com.example.jooq.tables.NotificationHistory.NOTIFICATION_HISTORY;
import static com.example.jooq.tables.Loan.LOAN;
import static com.example.jooq.tables.Member.MEMBER;
import static com.example.jooq.tables.Book.BOOK;
import static org.jooq.impl.DSL.*;

import org.jooq.DatePart;
import java.time.LocalDateTime;


@Service
@Transactional
public class NotificationService {

    private final DSLContext dsl;

    public NotificationService(DSLContext dsl) {
        this.dsl = dsl;
    }

    public int createOverdueNotifications() {
        String subject = "Loan overdue reminder";

        return dsl.insertInto(NOTIFICATION,
                        NOTIFICATION.LOAN_ID,
                        NOTIFICATION.MEMBER_ID,
                        NOTIFICATION.EMAIL,
                        NOTIFICATION.SUBJECT,
                        NOTIFICATION.CONTENT)
                .select(
                        dsl.select(
                                        LOAN.ID.cast(Long.class),
                                        MEMBER.ID.cast(Long.class),
                                        MEMBER.EMAIL,
                                        DSL.inline(subject),
                                        DSL.concat(
                                                DSL.inline("Dear "), MEMBER.NAME,
                                                DSL.inline(", your loan for '"), BOOK.TITLE,
                                                DSL.inline("' was due on "),
                                                DSL.cast(LOAN.DUE_AT, String.class),
                                                DSL.inline(". Please return it as soon as possible.")
                                        )
                                )
                                .from(LOAN)
                                .join(MEMBER).on(MEMBER.ID.eq(LOAN.MEMBER_ID))
                                .join(BOOK).on(BOOK.ID.eq(LOAN.BOOK_ID))
                                .where(
                                        LOAN.STATUS.eq("OVERDUE")
                                                .and(LOAN.RETURNED_AT.isNull())
                                                .and(DSL.notExists(
                                                        dsl.selectOne()
                                                                .from(NOTIFICATION)
                                                                .where(NOTIFICATION.LOAN_ID.eq(LOAN.ID.cast(Long.class)))
                                                ))
                                )
                )
                .execute();
    }



    public int claimBatch(String processId, int limitSeconds, int limitRows) {
        int stolen = dsl.update(NOTIFICATION)
                .set(NOTIFICATION.PROCESS_ID, processId)
                .set(NOTIFICATION.LOCKED_AT, DSL.currentLocalDateTime())
                .set(NOTIFICATION.RETRY_COUNT, NOTIFICATION.RETRY_COUNT.plus(1))
                .where(
                        NOTIFICATION.PROCESS_ID.isNotNull()
                                .and(NOTIFICATION.LOCKED_AT.isNotNull())
                                .and(NOTIFICATION.RETRY_COUNT.lt(3))
                                .and(
                                        field(
                                                "DATEADD(second, {0}, {1})",
                                                SQLDataType.LOCALDATETIME,
                                                val(limitSeconds),
                                                NOTIFICATION.LOCKED_AT
                                        ).lt(currentTimestamp().cast(SQLDataType.LOCALDATETIME))
                                )
                )
// <--- đóng ngoặc where ở đây
                .execute(); // <--- gọi execute() ở đây, trên câu lệnh update


        int claimed = dsl.update(NOTIFICATION)
                .set(NOTIFICATION.PROCESS_ID, processId)
                .set(NOTIFICATION.LOCKED_AT, DSL.currentLocalDateTime())
                .set(NOTIFICATION.RETRY_COUNT, NOTIFICATION.RETRY_COUNT.plus(1))
                .where(NOTIFICATION.PROCESS_ID.isNull()
                        .and(NOTIFICATION.RETRY_COUNT.lt(3)))
                .limit(limitRows)
                .execute();

        return stolen + claimed;
    }

    public List<Record> fetchMine(String processId, int limit) {
        return new ArrayList<>(dsl.selectFrom(NOTIFICATION)
                .where(NOTIFICATION.PROCESS_ID.eq(processId))
                .orderBy(NOTIFICATION.CREATED_AT.asc())
                .limit(limit)
                .fetch());
    }


    public void markFailure(long id, String error) {
        dsl.update(NOTIFICATION)
                .set(NOTIFICATION.PROCESS_ID, (String) null)
                .set(NOTIFICATION.LOCKED_AT, (java.time.OffsetDateTime) null)
                .set(NOTIFICATION.LAST_ERROR, error)
                .set(NOTIFICATION.LAST_ATTEMPT_AT, DSL.currentLocalDateTime())
                .where(NOTIFICATION.ID.eq(id))
                .execute();
    }

    public void archiveExhausted(long id) {
        var n = dsl.selectFrom(NOTIFICATION)
                .where(NOTIFICATION.ID.eq(id))
                .fetchOne();
        if (n == null) return;

        dsl.insertInto(NOTIFICATION_HISTORY,
                        NOTIFICATION_HISTORY.LOAN_ID,
                        NOTIFICATION_HISTORY.MEMBER_ID,
                        NOTIFICATION_HISTORY.EMAIL,
                        NOTIFICATION_HISTORY.SUBJECT,
                        NOTIFICATION_HISTORY.CONTENT,
                        NOTIFICATION_HISTORY.SUCCESS,
                        NOTIFICATION_HISTORY.ERROR_MSG)
                .values(n.getLoanId(), n.getMemberId(), n.getEmail(),
                        n.getSubject(), n.getContent(), false, n.getLastError())
                .execute();

        dsl.deleteFrom(NOTIFICATION)
                .where(NOTIFICATION.ID.eq(id))
                .execute();
    }

    public void archiveAndDelete(long id, boolean success, String error) {
        var n = dsl.selectFrom(NOTIFICATION)
                .where(NOTIFICATION.ID.eq(id))
                .fetchOne();
        if (n == null) return;

        dsl.insertInto(NOTIFICATION_HISTORY,
                        NOTIFICATION_HISTORY.LOAN_ID,
                        NOTIFICATION_HISTORY.MEMBER_ID,
                        NOTIFICATION_HISTORY.EMAIL,
                        NOTIFICATION_HISTORY.SUBJECT,
                        NOTIFICATION_HISTORY.CONTENT,
                        NOTIFICATION_HISTORY.SUCCESS,
                        NOTIFICATION_HISTORY.ERROR_MSG)
                .values(n.getLoanId(), n.getMemberId(), n.getEmail(),
                        n.getSubject(), n.getContent(), success, error)
                .execute();

        dsl.deleteFrom(NOTIFICATION)
                .where(NOTIFICATION.ID.eq(id))
                .execute();
    }
}