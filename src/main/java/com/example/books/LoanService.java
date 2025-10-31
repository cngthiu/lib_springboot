package com.example.books;

import org.jooq.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static com.example.jooq.tables.Book.BOOK;
import static com.example.jooq.tables.Loan.LOAN;
import static com.example.jooq.tables.Member.MEMBER;

@Service
@Transactional
public class LoanService {

    private final DSLContext dsl;

    public LoanService(DSLContext dsl) {
        this.dsl = dsl;
    }

    public Page<LoanView> list(String q, String status, Pageable pageable) {
        StringBuilder where = new StringBuilder("1=1");
        List<Object> countParams = new ArrayList<>();
        List<Object> queryParams = new ArrayList<>();

        if (status != null && !status.isBlank()) {
            where.append(" AND L.STATUS = ?");
            countParams.add(status);
            queryParams.add(status);
        }

        if (q != null && !q.isBlank()) {
            String like = "%" + q.trim() + "%";
            where.append(" AND (B.TITLE LIKE ? OR M.NAME LIKE ? OR M.CODE LIKE ?)");
            countParams.add(like);
            countParams.add(like);
            countParams.add(like);
            queryParams.add(like);
            queryParams.add(like);
            queryParams.add(like);
        }

        // COUNT tổng
        String countSql = "SELECT COUNT(*) FROM dbo.LOAN L " +
                "JOIN dbo.BOOK B ON B.ID = L.BOOK_ID " +
                "JOIN dbo.MEMBER M ON M.ID = L.MEMBER_ID " +
                "WHERE " + where;
        Integer total = dsl.fetchOne(countSql, countParams.toArray()).get(0, Integer.class);

        // Pagination
        int offset = (int) pageable.getOffset();
        int pageSize = pageable.getPageSize();

        String sql = "SELECT L.ID, B.TITLE, M.CODE, M.NAME, " +
                "L.BORROWED_AT, L.DUE_AT, L.RETURNED_AT, L.STATUS " +
                "FROM dbo.LOAN L " +
                "JOIN dbo.BOOK B ON B.ID = L.BOOK_ID " +
                "JOIN dbo.MEMBER M ON M.ID = L.MEMBER_ID " +
                "WHERE " + where + " " +
                "ORDER BY L.BORROWED_AT DESC " +
                "OFFSET ? ROWS FETCH NEXT ? ROWS ONLY";

        queryParams.add(offset);
        queryParams.add(pageSize);

        List<LoanView> content = dsl.fetch(sql, queryParams.toArray())
                .stream()
                .map(r -> new LoanView(
                        r.getValue("ID", Long.class),
                        r.getValue("TITLE", String.class),
                        r.getValue("CODE", String.class),
                        r.getValue("NAME", String.class),
                        r.getValue("BORROWED_AT", java.sql.Timestamp.class) != null
                                ? r.getValue("BORROWED_AT", java.sql.Timestamp.class).toLocalDateTime()
                                : null,
                        r.getValue("DUE_AT", java.sql.Timestamp.class) != null
                                ? r.getValue("DUE_AT", java.sql.Timestamp.class).toLocalDateTime()
                                : null,
                        r.getValue("RETURNED_AT", java.sql.Timestamp.class) != null
                                ? r.getValue("RETURNED_AT", java.sql.Timestamp.class).toLocalDateTime()
                                : null,
                        r.getValue("STATUS", String.class)
                ))
                .toList();

        return new PageImpl<>(content, pageable, total);
    }

    public LoanView borrow(long bookId, long memberId, int days) {
        OffsetDateTime now = OffsetDateTime.now(ZoneId.systemDefault());
        OffsetDateTime due = now.plusDays(Math.max(days, 1));
        LocalDateTime nowLdt = now.toLocalDateTime();
        LocalDateTime dueLdt = due.toLocalDateTime();

        String insertSql = "INSERT INTO dbo.LOAN (BOOK_ID, MEMBER_ID, BORROWED_AT, DUE_AT, STATUS) " +
                "VALUES (?, ?, ?, ?, 'BORROWED'); " +
                "SELECT CAST(SCOPE_IDENTITY() AS BIGINT) AS ID";

        Long newId = dsl.fetchOne(insertSql, bookId, memberId, nowLdt, dueLdt).get(0, Long.class);

        String title = dsl.fetchOne("SELECT TITLE FROM dbo.BOOK WHERE ID = ?", bookId)
                .getValue("TITLE", String.class);

        Record2<String, String> m = dsl.select(MEMBER.CODE, MEMBER.NAME)
                .from(MEMBER)
                .where(MEMBER.ID.eq(memberId))
                .fetchOne();

        return new LoanView(newId, title, m.value1(), m.value2(), nowLdt, dueLdt, null, "BORROWED");
    }

    public LoanView returnLoan(long loanId) {
        OffsetDateTime now = OffsetDateTime.now(ZoneId.systemDefault());

        dsl.execute("UPDATE dbo.LOAN SET RETURNED_AT = ?, STATUS = 'RETURNED' WHERE ID = ?", now, loanId);

        org.jooq.Record r = dsl.fetchOne(
                "SELECT L.ID, B.TITLE, M.CODE, M.NAME, " +
                        "L.BORROWED_AT, L.DUE_AT, L.RETURNED_AT, L.STATUS " +
                        "FROM dbo.LOAN L " +
                        "JOIN dbo.BOOK B ON B.ID = L.BOOK_ID " +
                        "JOIN dbo.MEMBER M ON M.ID = L.MEMBER_ID " +
                        "WHERE L.ID = ?", loanId
        );

        if (r == null) throw new IllegalArgumentException("Loan not found");

        return new LoanView(
                r.getValue("ID", Long.class),
                r.getValue("TITLE", String.class),
                r.getValue("CODE", String.class),
                r.getValue("NAME", String.class),
                r.getValue("BORROWED_AT", java.sql.Timestamp.class) != null
                        ? r.getValue("BORROWED_AT", java.sql.Timestamp.class).toLocalDateTime()
                        : null,
                r.getValue("DUE_AT", java.sql.Timestamp.class) != null
                        ? r.getValue("DUE_AT", java.sql.Timestamp.class).toLocalDateTime()
                        : null,
                r.getValue("RETURNED_AT", java.sql.Timestamp.class) != null
                        ? r.getValue("RETURNED_AT", java.sql.Timestamp.class).toLocalDateTime()
                        : null,
                r.getValue("STATUS", String.class)
        );
    }

    public int markOverdueAndCount() {
        return dsl.execute("UPDATE dbo.LOAN " +
                "SET STATUS = 'OVERDUE' " +
                "WHERE RETURNED_AT IS NULL AND DUE_AT < GETDATE()");
    }

    public LoanView borrowByMemberCode(long bookId, String memberCode, int days) {
        // Tìm member ID từ code
        String sql = "SELECT ID FROM dbo.MEMBER WHERE CODE = ?";
        org.jooq.Record record = dsl.fetchOne(sql, memberCode);

        if (record == null) {
            throw new IllegalArgumentException("Member not found with code: " + memberCode);
        }

        Long memberId = record.getValue("ID", Long.class);

        // Gọi lại method borrow với member ID
        return borrow(bookId, memberId, days);
    }
}
