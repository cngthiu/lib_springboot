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
import static com.example.jooq.tables.Member.MEMBER; // Import tĩnh

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
            countParams.add(like);
            countParams.add(like);
            countParams.add(like);
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

        // SELECT có thêm STATUS và MAX_LOAN_LIMIT
        String sql = "SELECT ID, CODE, NAME, EMAIL, STATUS, MAX_LOAN_LIMIT " +
                "FROM dbo.MEMBER " +
                "WHERE " + whereClause + " " +
                "ORDER BY ID DESC " +
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
                    m.setStatus(r.get("STATUS", String.class));
                    m.setMaxLoanLimit(r.get("MAX_LOAN_LIMIT", Integer.class));
                    return m;
                })
                .toList();

        return new PageImpl<>(content, pageable, total);
    }

    public MemberDTO findById(Long id) {
        String sql = "SELECT ID, CODE, NAME, EMAIL, STATUS, MAX_LOAN_LIMIT " +
                "FROM dbo.MEMBER WHERE ID = ?";

        var record = dsl.fetchOne(sql, id);
        if (record == null)
            throw new IllegalArgumentException("Member not found");

        MemberDTO m = new MemberDTO();
        m.setId(record.get("ID", Long.class));
        m.setCode(record.get("CODE", String.class));
        m.setName(record.get("NAME", String.class));
        m.setEmail(record.get("EMAIL", String.class));
        m.setStatus(record.get("STATUS", String.class));
        m.setMaxLoanLimit(record.get("MAX_LOAN_LIMIT", Integer.class));
        return m;
    }

    public MemberDTO save(MemberDTO m) {
        if (m.getId() == null) {
            // INSERT
            String sql = "INSERT INTO dbo.MEMBER (CODE, NAME, EMAIL, STATUS, MAX_LOAN_LIMIT) " +
                    "VALUES (?, ?, ?, ?, ?); " +
                    "SELECT CAST(SCOPE_IDENTITY() AS BIGINT) AS ID";

            Long newId = dsl.fetchOne(sql,
                            m.getCode(),
                            m.getName(),
                            m.getEmail(),
                            m.getStatus(),
                            m.getMaxLoanLimit())
                    .get(0, Long.class);

            m.setId(newId);
        } else {
            // UPDATE
            String sql = "UPDATE dbo.MEMBER " +
                    "SET CODE = ?, NAME = ?, EMAIL = ?, STATUS = ?, MAX_LOAN_LIMIT = ? " +
                    "WHERE ID = ?";

            dsl.execute(sql,
                    m.getCode(),
                    m.getName(),
                    m.getEmail(),
                    m.getStatus(),
                    m.getMaxLoanLimit(),
                    m.getId());
        }
        return m;
    }

    public void delete(Long id) {
        String sql = "DELETE FROM dbo.MEMBER WHERE ID = ?";
        dsl.execute(sql, id);
    }
}
