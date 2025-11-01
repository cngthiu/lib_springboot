package com.example.books;

import org.jooq.DSLContext;
import org.jooq.SortOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@Transactional
public class MemberService {

    private final DSLContext dsl;

    public MemberService(DSLContext dsl) {
        this.dsl = dsl;
    }

    public Page<MemberDTO> findAll(String q, Pageable pageable) {
        // Build WHERE clause và parameters
        StringBuilder whereClause = new StringBuilder("1=1");
        List<Object> countParams = new ArrayList<>();
        List<Object> queryParams = new ArrayList<>();

        if (q != null && !q.isBlank()) {
            String like = "%" + q.trim() + "%";
            whereClause.append(" AND (NAME LIKE ? OR CODE LIKE ? OR EMAIL LIKE ?)");
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
        String countSql = "SELECT COUNT(*) FROM dbo.MEMBER WHERE " + whereClause;
        Integer total = dsl.fetchOne(countSql, countParams.toArray())
                .get(0, Integer.class);

        // Pagination
        int offset = (int) pageable.getOffset();
        int pageSize = pageable.getPageSize();

        // Query với OFFSET/FETCH (SQL Server syntax)
        String sql = "SELECT ID, CODE, NAME, EMAIL " +
                "FROM dbo.MEMBER " +
                "WHERE " + whereClause + " " +
                "ORDER BY ID DESC " + // Simple sort by ID for simplicity
                "OFFSET ? ROWS FETCH NEXT ? ROWS ONLY";

        queryParams.add(offset);
        queryParams.add(pageSize);

        List<MemberDTO> content = dsl.fetch(sql, queryParams.toArray())
                .stream()
                .map(r -> {
                    MemberDTO m = new MemberDTO();
                    m.setId(r.get("ID", Long.class));
                    m.setCode(r.get("CODE", String.class));
                    m.setName(r.get("NAME", String.class));
                    m.setEmail(r.get("EMAIL", String.class));
                    return m;
                })
                .toList();

        return new PageImpl<>(content, pageable, total);
    }

    public MemberDTO findById(Long id) {
        String sql = "SELECT ID, CODE, NAME, EMAIL " +
                "FROM dbo.MEMBER WHERE ID = ?";

        org.jooq.Record record = dsl.fetchOne(sql, id);

        if (record == null)
            throw new IllegalArgumentException("Member not found");

        MemberDTO m = new MemberDTO();
        m.setId(record.get("ID", Long.class));
        m.setCode(record.get("CODE", String.class));
        m.setName(record.get("NAME", String.class));
        m.setEmail(record.get("EMAIL", String.class));
        return m;
    }

    public MemberDTO save(MemberDTO m) {
        if (m.getId() == null) {
            // INSERT
            String sql = "INSERT INTO dbo.MEMBER (CODE, NAME, EMAIL) " +
                    "VALUES (?, ?, ?); " +
                    "SELECT CAST(SCOPE_IDENTITY() AS BIGINT) AS ID";

            Long newId = dsl.fetchOne(sql,
                            m.getCode(),
                            m.getName(),
                            m.getEmail())
                    .get(0, Long.class);

            m.setId(newId);
        } else {
            // UPDATE
            String sql = "UPDATE dbo.MEMBER " +
                    "SET CODE = ?, NAME = ?, EMAIL = ? " +
                    "WHERE ID = ?";

            dsl.execute(sql,
                    m.getCode(),
                    m.getName(),
                    m.getEmail(),
                    m.getId());
        }
        return m;
    }

    public void delete(Long id) {
        String sql = "DELETE FROM dbo.MEMBER WHERE ID = ?";
        dsl.execute(sql, id);
    }
}