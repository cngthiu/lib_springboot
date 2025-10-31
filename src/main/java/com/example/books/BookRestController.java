package com.example.books;

import jakarta.validation.Valid;
import org.jooq.SortField;
import org.jooq.SortOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import static com.example.jooq.tables.Book.BOOK;

@RestController
@RequestMapping("/api/books")
public class BookRestController {

    private final BookService service;

    public BookRestController(BookService service) {
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
    public Page<BookDTO> list(@RequestParam(defaultValue = "0") int page,
                           @RequestParam(defaultValue = "10") int size,
                           @RequestParam(defaultValue = "id,desc") String sort,
                           @RequestParam(required = false) String q) {
        Pageable pageable = PageRequest.of(Math.max(page,0), Math.max(size,1));
        return service.findAll(q, pageable, resolveSort(sort), resolveOrder(sort));
    }

    @GetMapping("/{id}")
    public BookDTO get(@PathVariable Long id) { return service.findById(id); }

    @PostMapping
    public ResponseEntity<BookDTO> create(@Valid @RequestBody BookDTO book) {
        BookDTO saved = service.save(book);
        return new ResponseEntity<>(saved, HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    public BookDTO update(@PathVariable Long id, @Valid @RequestBody BookDTO book) {
        book.setId(id);
        return service.save(book);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) { service.delete(id); }
}
