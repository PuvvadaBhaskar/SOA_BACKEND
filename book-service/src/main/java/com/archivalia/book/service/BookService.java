package com.archivalia.book.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.archivalia.book.dto.BookRequest;
import com.archivalia.book.entity.Book;
import com.archivalia.book.exception.BadRequestException;
import com.archivalia.book.exception.ConflictException;
import com.archivalia.book.exception.ResourceNotFoundException;
import com.archivalia.book.repository.BookRepository;

@Service
public class BookService {

    private static final Logger log = LoggerFactory.getLogger(BookService.class);

    private final BookRepository repository;

    public BookService(BookRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public Book addBook(BookRequest request) {

        String isbn = request.getIsbn().trim();
        if (repository.existsByIsbn(isbn)) {
            throw new ConflictException("A book with ISBN " + isbn + " already exists");
        }

        Book book = new Book();
        apply(book, request);
        book.setAvailableCopies(book.getTotalCopies());

        return repository.save(book);
    }

    @Transactional(readOnly = true)
    public List<Book> getAllBooks() {

        return repository.findAll();
    }

    @Transactional(readOnly = true)
    public Book getBook(Long id) {

        return repository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Book not found with id " + id));
    }

    /**
     * Full update. availableCopies is shifted by the change in totalCopies so that copies currently
     * on loan are preserved; totalCopies cannot drop below the number of copies on loan.
     */
    @Transactional
    public Book updateBook(Long id, BookRequest request) {

        Book book = getBook(id);

        String isbn = request.getIsbn().trim();
        if (repository.existsByIsbnAndIdNot(isbn, id)) {
            throw new ConflictException("Another book already uses ISBN " + isbn);
        }

        int onLoan = book.getTotalCopies() - book.getAvailableCopies();
        if (request.getTotalCopies() < onLoan) {
            throw new BadRequestException("totalCopies cannot be lower than the " + onLoan
                    + " copies currently on loan");
        }

        apply(book, request);
        book.setAvailableCopies(request.getTotalCopies() - onLoan);

        return repository.save(book);
    }

    @Transactional
    public void deleteBook(Long id) {

        Book book = getBook(id);

        if (book.getAvailableCopies() < book.getTotalCopies()) {
            throw new ConflictException("Cannot delete book " + id + ": "
                    + (book.getTotalCopies() - book.getAvailableCopies()) + " copies are still on loan");
        }

        repository.delete(book);
    }

    @Transactional(readOnly = true)
    public List<Book> search(String title) {

        if (title == null || title.isBlank()) {
            throw new BadRequestException("Query parameter 'title' must not be blank");
        }

        return repository.findByTitleContainingIgnoreCase(title.trim());
    }

    /** Takes one copy atomically; 404 if the book does not exist, 409 if no copy is available. */
    @Transactional
    public Book borrowBook(Long id) {

        Book book = getBook(id);

        if (repository.decrementAvailableCopies(id) == 0) {
            throw new ConflictException("No copies of '" + book.getTitle() + "' are currently available");
        }

        return getBook(id);
    }

    /** Puts one copy back atomically (idempotent at full stock). */
    @Transactional
    public Book returnBook(Long id) {

        getBook(id);

        if (repository.incrementAvailableCopies(id) == 0) {
            log.warn("Return of book {} ignored: all copies are already in stock", id);
        }

        return getBook(id);
    }

    private static void apply(Book book, BookRequest request) {
        book.setTitle(request.getTitle().trim());
        book.setAuthor(request.getAuthor());
        book.setIsbn(request.getIsbn().trim());
        book.setCategory(request.getCategory());
        book.setDescription(request.getDescription());
        book.setTotalCopies(request.getTotalCopies());
    }
}
