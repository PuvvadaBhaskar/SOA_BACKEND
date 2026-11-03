package com.archivalia.fine.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Manual fine created by an administrator (e.g. lost or damaged book). */
public class FineCreateRequest {

    @Positive(message = "borrowId must be positive")
    private Long borrowId;

    @NotNull(message = "userId is required")
    @Positive(message = "userId must be positive")
    private Long userId;

    @Positive(message = "bookId must be positive")
    private Long bookId;

    @NotNull(message = "fineAmount is required")
    @Positive(message = "fineAmount must be greater than 0")
    private Double fineAmount;

    @Size(max = 255, message = "reason must be at most 255 characters")
    private String reason;

    public FineCreateRequest() {
    }

    public FineCreateRequest(Long borrowId, Long userId, Long bookId, Double fineAmount, String reason) {
        this.borrowId = borrowId;
        this.userId = userId;
        this.bookId = bookId;
        this.fineAmount = fineAmount;
        this.reason = reason;
    }

    public Long getBorrowId() {
        return borrowId;
    }

    public void setBorrowId(Long borrowId) {
        this.borrowId = borrowId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getBookId() {
        return bookId;
    }

    public void setBookId(Long bookId) {
        this.bookId = bookId;
    }

    public Double getFineAmount() {
        return fineAmount;
    }

    public void setFineAmount(Double fineAmount) {
        this.fineAmount = fineAmount;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
