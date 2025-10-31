package com.example.books;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/loans")
public class LoanMvcController {

    private final LoanService service;

    public LoanMvcController(LoanService service) {
        this.service = service;
    }

    @GetMapping
    public String list(@RequestParam(defaultValue = "0") int page,
                       @RequestParam(defaultValue = "10") int size,
                       @RequestParam(required = false) String q,
                       @RequestParam(required = false) String status,
                       Model model) {
        Pageable pageable = PageRequest.of(Math.max(page,0), Math.max(size,1));
        Page<LoanView> data = service.list(q, status, pageable);
        model.addAttribute("data", data);
        model.addAttribute("loans", data.getContent());
        model.addAttribute("q", q == null ? "" : q);
        model.addAttribute("status", status == null ? "" : status);
        model.addAttribute("page", data.getNumber());
        model.addAttribute("size", data.getSize());
        model.addAttribute("totalPages", data.getTotalPages());
        return "loans/list";
    }
}
