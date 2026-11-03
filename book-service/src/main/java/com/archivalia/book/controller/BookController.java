package com.archivalia.book.controller;

import java.net.URI;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.archivalia.book.dto.BookRequest;
import com.archivalia.book.entity.Book;
import com.archivalia.book.service.BookService;
import com.archivalia.book.web.InstanceInfo;

import jakarta.validation.Valid;

/**
 * Book catalogue. Reads are open to any authenticated user; writes are ADMIN-only.
 * The gateway enforces the same rules - these annotations are the second line of defence.
 */
@RestController
@RequestMapping("/books")
public class BookController {

    private final BookService service;
    private final InstanceInfo instanceInfo;

    public BookController(BookService service, InstanceInfo instanceInfo) {
        this.service = service;
        this.instanceInfo = instanceInfo;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Book> addBook(
            @Valid @RequestBody BookRequest book) {

        Book created = service.addBook(book);

        return ResponseEntity
                .created(URI.create("/books/" + created.getId()))
                .body(created);
    }

    @GetMapping
    public ResponseEntity<List<Book>> getAllBooks() {

        return ResponseEntity.ok(
                service.getAllBooks()
        );
    }

    /** Which instance answered - used to demonstrate load balancing through the gateway. */
    @GetMapping("/instance")
    public ResponseEntity<Map<String, Object>> instance() {

        return ResponseEntity.ok(
                instanceInfo.describe()
        );
    }

    @GetMapping("/search")
    public ResponseEntity<List<Book>> search(
            @RequestParam String title) {

        return ResponseEntity.ok(
                service.search(title)
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<Book> getBook(
            @PathVariable Long id) {

        return ResponseEntity.ok(
                service.getBook(id)
        );
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Book> updateBook(
            @PathVariable Long id,
            @Valid @RequestBody BookRequest book) {

        return ResponseEntity.ok(
                service.updateBook(id, book)
        );
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> deleteBook(
            @PathVariable Long id) {

        service.deleteBook(id);

        return ResponseEntity.ok(
                Map.of("message", "Book deleted successfully")
        );
    }

    /**
     * Stock endpoints used by borrow-service (internal Feign calls carry the gateway secret).
     * At the gateway, PUT /books/** is ADMIN-only, so end users cannot change stock directly.
     */
    @PutMapping("/{id}/borrow")
    public ResponseEntity<Book> borrowBook(
            @PathVariable Long id) {

        return ResponseEntity.ok(
                service.borrowBook(id)
        );
    }

    @PutMapping("/{id}/return")
    public ResponseEntity<Book> returnBook(
            @PathVariable Long id) {

        return ResponseEntity.ok(
                service.returnBook(id)
        );
    }
}
