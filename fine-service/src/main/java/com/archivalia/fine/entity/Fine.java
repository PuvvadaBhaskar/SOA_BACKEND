package com.archivalia.fine.entity;

import java.time.LocalDate;

import jakarta.persistence.*;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

@Entity
@Table(name = "fines")
public class Fine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long borrowId;

    private Long userId;

    private Long bookId;

    @PositiveOrZero
    private long lateDays;

    @PositiveOrZero
    private double fineAmount;

    /** Stored as text (NO_FINE / UNPAID / PAID / WAIVED) - compatible with the previous String column. */
    @Enumerated(EnumType.STRING)
    private FineStatus status;

    private LocalDate paidDate;

    @Size(max = 255)
    private String reason;

    public Fine() {
    }

    public Long getId() {
        return id;
    }

    public Long getBorrowId() {
        return borrowId;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getBookId() {
        return bookId;
    }

    public long getLateDays() {
        return lateDays;
    }

    public double getFineAmount() {
        return fineAmount;
    }

    public FineStatus getStatus() {
        return status;
    }

    public LocalDate getPaidDate() {
        return paidDate;
    }

    public String getReason() {
        return reason;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setBorrowId(Long borrowId) {
        this.borrowId = borrowId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public void setBookId(Long bookId) {
        this.bookId = bookId;
    }

    public void setLateDays(long lateDays) {
        this.lateDays = lateDays;
    }

    public void setFineAmount(double fineAmount) {
        this.fineAmount = fineAmount;
    }

    public void setStatus(FineStatus status) {
        this.status = status;
    }

    public void setPaidDate(LocalDate paidDate) {
        this.paidDate = paidDate;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
