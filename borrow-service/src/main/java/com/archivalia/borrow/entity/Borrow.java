package com.archivalia.borrow.entity;

import java.time.LocalDate;

import jakarta.persistence.*;

@Entity
@Table(name = "borrows")
public class Borrow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    private Long bookId;

    private LocalDate borrowDate;

    private LocalDate dueDate;

    private LocalDate returnDate;

    /** Stored as text - compatible with the previous String column ("BORROWED" / "RETURNED"). */
    @Enumerated(EnumType.STRING)
    private BorrowStatus status;

    /** How many times the due date was extended. */
    private Integer extensionCount;

    /** Fine created in fine-service for this borrow (set on return). */
    private Long fineId;

    /** True when the book was returned but fine-service could not be reached; retried in the background. */
    private Boolean finePending;

    /** Why a PENDING borrow ended up FAILED. */
    @Column(length = 255)
    private String failureReason;

    public Borrow() {
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getBookId() {
        return bookId;
    }

    public LocalDate getBorrowDate() {
        return borrowDate;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public LocalDate getReturnDate() {
        return returnDate;
    }

    public BorrowStatus getStatus() {
        return status;
    }

    public int getExtensionCount() {
        return extensionCount == null ? 0 : extensionCount;
    }

    public Long getFineId() {
        return fineId;
    }

    public boolean isFinePending() {
        return Boolean.TRUE.equals(finePending);
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public void setBookId(Long bookId) {
        this.bookId = bookId;
    }

    public void setBorrowDate(LocalDate borrowDate) {
        this.borrowDate = borrowDate;
    }

    public void setDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
    }

    public void setReturnDate(LocalDate returnDate) {
        this.returnDate = returnDate;
    }

    public void setStatus(BorrowStatus status) {
        this.status = status;
    }

    public void setExtensionCount(Integer extensionCount) {
        this.extensionCount = extensionCount;
    }

    public void setFineId(Long fineId) {
        this.fineId = fineId;
    }

    public void setFinePending(Boolean finePending) {
        this.finePending = finePending;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }
}
