package com.archivalia.book.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.archivalia.book.dto.BookRequest;
import com.archivalia.book.entity.Book;
import com.archivalia.book.exception.BadRequestException;
import com.archivalia.book.exception.ConflictException;
import com.archivalia.book.exception.ResourceNotFoundException;
import com.archivalia.book.repository.BookRepository;

@ExtendWith(MockitoExtension.class)
class BookServiceTest {

    @Mock
    private BookRepository repository;

    @InjectMocks
    private BookService service;

    @Test
    void addBookSetsAvailableCopiesToTotal() {
        when(repository.existsByIsbn("9780134685991")).thenReturn(false);
        when(repository.save(any(Book.class))).thenAnswer(inv -> inv.getArgument(0));

        Book saved = service.addBook(request("Effective Java", "9780134685991", 4));

        assertThat(saved.getTotalCopies()).isEqualTo(4);
        assertThat(saved.getAvailableCopies()).isEqualTo(4);
        assertThat(saved.getTitle()).isEqualTo("Effective Java");
    }

    @Test
    void addBookWithDuplicateIsbnThrowsConflict() {
        when(repository.existsByIsbn("9780134685991")).thenReturn(true);

        assertThatThrownBy(() -> service.addBook(request("Dup", "9780134685991", 1)))
                .isInstanceOf(ConflictException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void getBookThrowsNotFound() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getBook(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void updateKeepsCopiesOnLoan() {
        Book book = book(1L, 5, 3); // 2 on loan
        when(repository.findById(1L)).thenReturn(Optional.of(book));
        when(repository.existsByIsbnAndIdNot("9780134685991", 1L)).thenReturn(false);
        when(repository.save(any(Book.class))).thenAnswer(inv -> inv.getArgument(0));

        Book updated = service.updateBook(1L, request("New title", "9780134685991", 10));

        assertThat(updated.getTotalCopies()).isEqualTo(10);
        assertThat(updated.getAvailableCopies()).isEqualTo(8);
        assertThat(updated.getTitle()).isEqualTo("New title");
    }

    @Test
    void updateCannotReduceTotalBelowCopiesOnLoan() {
        when(repository.findById(1L)).thenReturn(Optional.of(book(1L, 5, 1))); // 4 on loan
        when(repository.existsByIsbnAndIdNot("9780134685991", 1L)).thenReturn(false);

        assertThatThrownBy(() -> service.updateBook(1L, request("T", "9780134685991", 3)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("on loan");
    }

    @Test
    void borrowWhenNoCopiesThrowsConflict() {
        when(repository.findById(1L)).thenReturn(Optional.of(book(1L, 2, 0)));
        when(repository.decrementAvailableCopies(1L)).thenReturn(0);

        assertThatThrownBy(() -> service.borrowBook(1L)).isInstanceOf(ConflictException.class);
    }

    @Test
    void borrowUsesAtomicDecrement() {
        when(repository.findById(1L)).thenReturn(Optional.of(book(1L, 2, 2)), Optional.of(book(1L, 2, 1)));
        when(repository.decrementAvailableCopies(1L)).thenReturn(1);

        Book result = service.borrowBook(1L);

        assertThat(result.getAvailableCopies()).isEqualTo(1);
        verify(repository).decrementAvailableCopies(1L);
        verify(repository, never()).save(any());
    }

    @Test
    void borrowUnknownBookThrowsNotFound() {
        when(repository.findById(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.borrowBook(5L)).isInstanceOf(ResourceNotFoundException.class);
        verify(repository, never()).decrementAvailableCopies(any());
    }

    @Test
    void deleteWithCopiesOnLoanThrowsConflict() {
        when(repository.findById(1L)).thenReturn(Optional.of(book(1L, 3, 2)));

        assertThatThrownBy(() -> service.deleteBook(1L)).isInstanceOf(ConflictException.class);
        verify(repository, never()).delete(any());
    }

    @Test
    void deleteUnknownBookThrowsNotFound() {
        when(repository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteBook(1L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void searchRejectsBlankTitle() {
        assertThatThrownBy(() -> service.search("  ")).isInstanceOf(BadRequestException.class);
    }

    @Test
    void searchDelegatesToRepository() {
        when(repository.findByTitleContainingIgnoreCase("java")).thenReturn(List.of(book(1L, 1, 1)));

        assertThat(service.search(" java ")).hasSize(1);
    }

    private static BookRequest request(String title, String isbn, int copies) {
        return new BookRequest(title, "Author", isbn, "Programming", "desc", copies);
    }

    private static Book book(Long id, int total, int available) {
        Book book = new Book();
        book.setId(id);
        book.setTitle("Book " + id);
        book.setIsbn("9780134685991");
        book.setTotalCopies(total);
        book.setAvailableCopies(available);
        return book;
    }
}
