package com.example.books;

import org.jooq.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
@Transactional
public class BookService {

    private final DSLContext dsl;

    public BookService(DSLContext dsl) {
        this.dsl = dsl;
    }

    public Page<BookDTO> findAll(String q, Pageable pageable, SortField<?> sortField, SortOrder order) {
        // Build WHERE clause và parameters
        StringBuilder whereClause = new StringBuilder("1=1");
        List<Object> countParams = new ArrayList<>();
        List<Object> queryParams = new ArrayList<>();

        if (q != null && !q.isBlank()) {
            String like = "%" + q.trim() + "%";
            whereClause.append(" AND (TITLE LIKE ? OR AUTHOR LIKE ? OR ISBN LIKE ?)");
            // Params cho COUNT query
            countParams.add(like);
            countParams.add(like);
            countParams.add(like);
            // Params cho SELECT query
            queryParams.add(like);
            queryParams.add(like);
            queryParams.add(like);
        }

        // Đếm tổng
        String countSql = "SELECT COUNT(*) FROM dbo.BOOK WHERE " + whereClause;
        Integer total = dsl.fetchOne(countSql, countParams.toArray())
                .get(0, Integer.class);  // Lấy cột index 0

        // Pagination
        int offset = (int) pageable.getOffset();
        int pageSize = pageable.getPageSize();
        String orderDir = (order == SortOrder.ASC) ? "ASC" : "DESC";

        // Query với OFFSET/FETCH (SQL Server syntax)
        String sql = "SELECT ID, TITLE, AUTHOR, PRICE, PUBLISHED_DATE, ISBN " +
                "FROM dbo.BOOK " +
                "WHERE " + whereClause + " " +
                "ORDER BY TITLE " + orderDir + " " +
                "OFFSET ? ROWS FETCH NEXT ? ROWS ONLY";

        queryParams.add(offset);
        queryParams.add(pageSize);

        List<BookDTO> content = dsl.fetch(sql, queryParams.toArray())
                .stream()
                .map(r -> {
                    BookDTO b = new BookDTO();
                    b.setId(r.get("ID", Long.class));
                    b.setTitle(r.get("TITLE", String.class));
                    b.setAuthor(r.get("AUTHOR", String.class));
                    b.setPrice(r.get("PRICE", BigDecimal.class));
                    b.setPublishedDate(r.get("PUBLISHED_DATE", LocalDate.class));
                    b.setIsbn(r.get("ISBN", String.class));
                    return b;
                })
                .toList();

        return new PageImpl<>(content, pageable, total);
    }

    public BookDTO findById(Long id) {
        String sql = "SELECT ID, TITLE, AUTHOR, PRICE, PUBLISHED_DATE, ISBN " +
                "FROM dbo.BOOK WHERE ID = ?";

        org.jooq.Record record = dsl.fetchOne(sql, id);

        if (record == null)
            throw new IllegalArgumentException("Book not found");

        BookDTO b = new BookDTO();
        b.setId(record.get("ID", Long.class));
        b.setTitle(record.get("TITLE", String.class));
        b.setAuthor(record.get("AUTHOR", String.class));
        b.setPrice(record.get("PRICE", BigDecimal.class));
        b.setPublishedDate(record.get("PUBLISHED_DATE", LocalDate.class));
        b.setIsbn(record.get("ISBN", String.class));
        return b;
    }

    public BookDTO save(BookDTO b) {
        if (b.getId() == null) {
            // INSERT
            String sql = "INSERT INTO dbo.BOOK (TITLE, AUTHOR, PRICE, PUBLISHED_DATE, ISBN) " +
                    "VALUES (?, ?, ?, ?, ?); " +
                    "SELECT CAST(SCOPE_IDENTITY() AS BIGINT) AS ID";

            Long newId = dsl.fetchOne(sql,
                            b.getTitle(),
                            b.getAuthor(),
                            b.getPrice(),
                            b.getPublishedDate(),
                            b.getIsbn())
                    .get(0, Long.class);  // Lấy cột index 0

            b.setId(newId);
        } else {
            // UPDATE
            String sql = "UPDATE dbo.BOOK " +
                    "SET TITLE = ?, AUTHOR = ?, PRICE = ?, PUBLISHED_DATE = ?, ISBN = ? " +
                    "WHERE ID = ?";

            dsl.execute(sql,
                    b.getTitle(),
                    b.getAuthor(),
                    b.getPrice(),
                    b.getPublishedDate(),
                    b.getIsbn(),
                    b.getId());
        }
        return b;
    }

    public void delete(Long id) {
        String sql = "DELETE FROM dbo.BOOK WHERE ID = ?";
        dsl.execute(sql, id);
    }
}