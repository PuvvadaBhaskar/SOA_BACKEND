package com.archivalia.book.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.archivalia.book.entity.Book;

public interface BookRepository extends JpaRepository<Book, Long> {

    List<Book> findByTitleContainingIgnoreCase(String title);

    List<Book> findByAuthorContainingIgnoreCase(String author);

    List<Book> findByCategoryContainingIgnoreCase(String category);

    boolean existsByIsbn(String isbn);

    boolean existsByIsbnAndIdNot(String isbn, Long id);

    /**
     * Atomically takes one copy. The WHERE clause makes it race-free: two concurrent borrows of the
     * last copy cannot both succeed because only one UPDATE matches "availableCopies > 0".
     *
     * @return 1 if a copy was taken, 0 if none was available
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Book b set b.availableCopies = b.availableCopies - 1, b.version = b.version + 1 "
            + "where b.id = :id and b.availableCopies > 0")
    int decrementAvailableCopies(@Param("id") Long id);

    /**
     * Atomically puts one copy back, never exceeding totalCopies.
     *
     * @return 1 if a copy was returned, 0 if the book was already fully stocked
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Book b set b.availableCopies = b.availableCopies + 1, b.version = b.version + 1 "
            + "where b.id = :id and b.availableCopies < b.totalCopies")
    int incrementAvailableCopies(@Param("id") Long id);
}
