package com.archivalia.borrow.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Borrow request. There is intentionally NO userId: the borrower is always the authenticated
 * caller (X-User-Id set by the gateway from the JWT). A userId sent by the client is ignored.
 */
public class BorrowRequest {

    @NotNull(message = "bookId is required")
    @Positive(message = "bookId must be positive")
    private Long bookId;

    public BorrowRequest() {
    }

    public BorrowRequest(Long bookId) {
        this.bookId = bookId;
    }

    public Long getBookId() {
        return bookId;
    }

    public void setBookId(Long bookId) {
        this.bookId = bookId;
    }
}
