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
import java.util.Optional;

import static com.example.jooq.tables.Book.BOOK;
import static com.example.jooq.tables.Loan.LOAN;
import static com.example.jooq.tables.Member.MEMBER;
import static com.example.jooq.tables.Reservation.RESERVATION;

@Service
@Transactional
public class LoanService {

    private final DSLContext dsl;
        private final NotificationService notificationService;

    public LoanService(DSLContext dsl) {
        this.dsl = dsl;
        this.notificationService = notificationService;
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
        // 1. Kiểm tra thành viên
        Record member = validateMember(memberId);

        // 2. Kiểm tra sách
        Record book = validateBook(bookId);

        // 3. Giảm số lượng sách
        dsl.update(BOOK)
                .set(BOOK.AVAILABLE_COPIES, BOOK.AVAILABLE_COPIES.minus(1))
                .where(BOOK.ID.eq(bookId))
                .execute();

        // 4. Tạo khoản mượn
        OffsetDateTime now = OffsetDateTime.now(ZoneId.systemDefault());
        OffsetDateTime due = now.plusDays(Math.max(days, 1));
        LocalDateTime nowLdt = now.toLocalDateTime();
        LocalDateTime dueLdt = due.toLocalDateTime();

        String insertSql = "INSERT INTO dbo.LOAN (BOOK_ID, MEMBER_ID, BORROWED_AT, DUE_AT, STATUS) " +
                "VALUES (?, ?, ?, ?, 'BORROWED'); " +
                "SELECT CAST(SCOPE_IDENTITY() AS BIGINT) AS ID";

        Long newId = dsl.fetchOne(insertSql, bookId, memberId, nowLdt, dueLdt).get(0, Long.class);

        // Trả về LoanView
        return new LoanView(newId,
                book.get(BOOK.TITLE),
                member.get(MEMBER.CODE),
                member.get(MEMBER.NAME),
                nowLdt, dueLdt, null, "BORROWED"
        );
    }
    /**
     * Helper kiểm tra điều kiện thành viên
     */
    private Record validateMember(long memberId) {
        Record member = dsl.select(MEMBER.CODE, MEMBER.NAME, MEMBER.STATUS, MEMBER.MAX_LOAN_LIMIT)
                .from(MEMBER)
                .where(MEMBER.ID.eq(memberId))
                .fetchOne();

        if (member == null) {
            throw new IllegalArgumentException("Member not found");
        }
        
        if (!"ACTIVE".equals(member.get(MEMBER.STATUS))) {
            throw new IllegalStateException("Member account is " + member.get(MEMBER.STATUS));
        }

        Integer currentLoans = dsl.selectCount()
                .from(LOAN)
                .where(LOAN.MEMBER_ID.eq(memberId)
                        .and(LOAN.STATUS.in("BORROWED", "OVERDUE")))
                .fetchOne(0, Integer.class);
        
        if (currentLoans >= member.get(MEMBER.MAX_LOAN_LIMIT)) {
            throw new IllegalStateException("Member has reached loan limit of " + member.get(MEMBER.MAX_LOAN_LIMIT));
        }
        
        return member;
    }
    /**
     * Helper kiểm tra điều kiện sách
     */
    private Record validateBook(long bookId) {
        Record book = dsl.select(BOOK.TITLE, BOOK.AVAILABLE_COPIES)
                .from(BOOK)
                .where(BOOK.ID.eq(bookId))
                .fetchOne();

        if (book == null) {
            throw new IllegalArgumentException("Book not found");
        }

        if (book.get(BOOK.AVAILABLE_COPIES) <= 0) {
            throw new IllegalStateException("Book is currently unavailable. Please place a reservation.");
        }
        return book;
    }
        /**
     * Cập nhật hàm trả sách để xử lý TỒN KHO và HÀNG CHỜ
     */
    public LoanView returnLoan(long loanId) {
        OffsetDateTime now = OffsetDateTime.now(ZoneId.systemDefault());

        // 1. Cập nhật trạng thái mượn
        // Lấy BOOK_ID TRƯỚC KHI CẬP NHẬT
        Long bookId = dsl.select(LOAN.BOOK_ID)
                .from(LOAN)
                .where(LOAN.ID.eq(loanId))
                .fetchOne(LOAN.BOOK_ID);
                
        if (bookId == null) {
             throw new IllegalArgumentException("Loan not found");
        }

        dsl.execute("UPDATE dbo.LOAN SET RETURNED_AT = ?, STATUS = 'RETURNED' WHERE ID = ?", now, loanId);

        // 2. Xử lý logic tồn kho và hàng chờ (RESERVATION)
        Optional<Record> nextInQueue = dsl.select(RESERVATION.ID, RESERVATION.MEMBER_ID)
                .from(RESERVATION)
                .where(RESERVATION.BOOK_ID.eq(bookId)
                        .and(RESERVATION.STATUS.eq("PENDING")))
                .orderBy(RESERVATION.REQUESTED_AT.asc())
                .limit(1)
                .fetchOptional();
        
        if (nextInQueue.isPresent()) {
            // Có người chờ -> Gán sách cho họ
            Record reservation = nextInQueue.get();
            long reservationId = reservation.get(RESERVATION.ID);
            long memberIdToNotify = reservation.get(RESERVATION.MEMBER_ID);

            // Cập nhật trạng thái đặt chỗ
            dsl.update(RESERVATION)
                    .set(RESERVATION.STATUS, "FULFILLED")
                    // Có thể set thêm ngày hết hạn lấy sách
                    .where(RESERVATION.ID.eq(reservationId))
                    .execute();
            
            // Gửi thông báo (Sách không quay lại kho AVAILABLE)
            // Giả định bạn có hàm này trong NotificationService
            // notificationService.createReservationAvailableNotification(bookId, memberIdToNotify);
            
        } else {
            // Không có ai chờ -> Tăng số lượng sách sẵn có
            dsl.update(BOOK)
                    .set(BOOK.AVAILABLE_COPIES, BOOK.AVAILABLE_COPIES.plus(1))
                    .where(BOOK.ID.eq(bookId))
                    .execute();
        }

        // 3. Lấy thông tin chi tiết để trả về (logic cũ)
        org.jooq.Record r = dsl.fetchOne(
                "SELECT L.ID, B.TITLE, M.CODE, M.NAME, " +
                        "L.BORROWED_AT, L.DUE_AT, L.RETURNED_AT, L.STATUS " +
                        "FROM dbo.LOAN L " +
                        "JOIN dbo.BOOK B ON B.ID = L.BOOK_ID " +
                        "JOIN dbo.MEMBER M ON M.ID = L.MEMBER_ID " +
                        "WHERE L.ID = ?", loanId
        );

        if (r == null) throw new IllegalArgumentException("Loan not found after update");

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
