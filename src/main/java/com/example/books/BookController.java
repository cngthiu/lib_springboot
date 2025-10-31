package com.example.books;

import jakarta.validation.Valid;
import org.jooq.SortField;
import org.jooq.SortOrder;
import org.jooq.impl.DSL;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import static com.example.jooq.tables.Book.BOOK;

@Controller
@RequestMapping("/books")
public class BookController {

    private final BookService service;

    public BookController(BookService service) {
        this.service = service;
    }

    private SortField<?> resolveSort(String sort) {
        String[] s = sort.split(",");
        String field = s[0];
        switch (field) {
            case "title": return BOOK.TITLE.sortDefault();
            case "author": return BOOK.AUTHOR.sortDefault();
            case "price": return BOOK.PRICE.sortDefault();
            case "publishedDate": return BOOK.PUBLISHED_DATE.sortDefault();
            case "isbn": return BOOK.ISBN.sortDefault();
            default: return BOOK.ID.sortDefault();
        }
    }

    private SortOrder resolveOrder(String sort) {
        String[] s = sort.split(",");
        return (s.length > 1 && s[1].equalsIgnoreCase("asc")) ? SortOrder.ASC : SortOrder.DESC;
    }

    @GetMapping
    public String list(@RequestParam(defaultValue = "0") int page,
                       @RequestParam(defaultValue = "10") int size,
                       @RequestParam(defaultValue = "id,desc") String sort,
                       @RequestParam(required = false) String q,
                       Model model) {

        Pageable pageable = PageRequest.of(Math.max(page,0), Math.max(size,1));
        SortField<?> sortField = resolveSort(sort);
        SortOrder order = resolveOrder(sort);

        Page<BookDTO> data = service.findAll(q, pageable, sortField, order);

        model.addAttribute("data", data);
        model.addAttribute("books", data.getContent());
        model.addAttribute("q", q == null ? "" : q);
        model.addAttribute("page", data.getNumber());
        model.addAttribute("size", data.getSize());
        model.addAttribute("totalPages", data.getTotalPages());
        model.addAttribute("sort", sort);
        return "books/list";
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        model.addAttribute("book", new BookDTO());
        model.addAttribute("mode", "create");
        return "books/form";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("book") BookDTO book, BindingResult binding, Model model) {
        if (binding.hasErrors()) {
            model.addAttribute("mode", "create");
            return "books/form";
        }
        service.save(book);
        return "redirect:/books";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        model.addAttribute("book", service.findById(id));
        model.addAttribute("mode", "edit");
        return "books/form";
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id, @Valid @ModelAttribute("book") BookDTO book, BindingResult binding, Model model) {
        if (binding.hasErrors()) {
            model.addAttribute("mode", "edit");
            return "books/form";
        }
        book.setId(id);
        service.save(book);
        return "redirect:/books";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id) {
        service.delete(id);
        return "redirect:/books";
    }
}
