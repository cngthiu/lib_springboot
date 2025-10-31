package com.example.books;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;

public record LoanView(
        Long id,
        String bookTitle,
        String memberCode,
        String memberName,
        LocalDateTime borrowedAt,
        LocalDateTime dueAt,
        LocalDateTime returnedAt,
        String status
) {}
