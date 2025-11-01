package com.example.books;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/loans")
public class LoanRestController {

    private final LoanService service;

    public LoanRestController(LoanService service) {
        this.service = service;
    }

    @GetMapping
    public Page<LoanView> list(@RequestParam(defaultValue = "0") int page,
                               @RequestParam(defaultValue = "10") int size,
                               @RequestParam(required = false) String q,
                               @RequestParam(required = false) String status) {
        Pageable pageable = PageRequest.of(Math.max(page,0), Math.max(size,1));
        return service.list(q, status, pageable);
    }

    @PostMapping("/borrow")
    public ResponseEntity<LoanView> borrow(@RequestParam long bookId,
                                           @RequestParam long memberId,
                                           @RequestParam(defaultValue = "14") int days) {
        return new ResponseEntity<>(service.borrow(bookId, memberId, days), HttpStatus.CREATED);
    }

    @PostMapping("/{loanId}/return")
    public LoanView returnLoan(@PathVariable long loanId) {
        return service.returnLoan(loanId);
    }
    /**
     * Xử lý các lỗi nghiệp vụ
     * Bắt các lỗi IllegalStateException và IllegalArgumentException từ LoanService
     */
    @ExceptionHandler({IllegalStateException.class, IllegalArgumentException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseEntity<String> handleBusinessException(Exception ex) {
        // Trả về lỗi 400 với thông báo lỗi rõ ràng cho client
        return new ResponseEntity<>(ex.getMessage(), HttpStatus.BAD_REQUEST);
    }
}
