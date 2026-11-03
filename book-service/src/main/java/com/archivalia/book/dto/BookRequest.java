package com.archivalia.book.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Create / update payload for a book. availableCopies is managed by the service and cannot be set
 * by clients.
 */
public class BookRequest {

    /** ISBN-10 or ISBN-13, digits optionally separated by '-' or ' '; ISBN-10 may end in X. */
    public static final String ISBN_REGEX = "^(?:(?:\\d[- ]?){9}[\\dXx]|(?:\\d[- ]?){12}\\d)$";

    @NotBlank(message = "title is required")
    @Size(max = 255, message = "title must be at most 255 characters")
    private String title;

    @Size(max = 255, message = "author must be at most 255 characters")
    private String author;

    @NotBlank(message = "isbn is required")
    @Pattern(regexp = ISBN_REGEX, message = "isbn must be a valid ISBN-10 or ISBN-13")
    private String isbn;

    @Size(max = 100, message = "category must be at most 100 characters")
    private String category;

    @Size(max = 1000, message = "description must be at most 1000 characters")
    private String description;

    @NotNull(message = "totalCopies is required")
    @Min(value = 0, message = "totalCopies must be >= 0")
    private Integer totalCopies;

    public BookRequest() {
    }

    public BookRequest(String title, String author, String isbn, String category, String description,
            Integer totalCopies) {
        this.title = title;
        this.author = author;
        this.isbn = isbn;
        this.category = category;
        this.description = description;
        this.totalCopies = totalCopies;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public String getIsbn() {
        return isbn;
    }

    public void setIsbn(String isbn) {
        this.isbn = isbn;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Integer getTotalCopies() {
        return totalCopies;
    }

    public void setTotalCopies(Integer totalCopies) {
        this.totalCopies = totalCopies;
    }
}
