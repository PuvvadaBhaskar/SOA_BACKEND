package com.archivalia.borrow.dto;

public class FineResponse {

    private Long id;
    private Long borrowId;
    private Long userId;
    private long lateDays;
    private double fineAmount;
    private String status;

    public Long getId() {
        return id;
    }

    public Long getBorrowId() {
        return borrowId;
    }

    public Long getUserId() {
        return userId;
    }

    public long getLateDays() {
        return lateDays;
    }

    public double getFineAmount() {
        return fineAmount;
    }

    public String getStatus() {
        return status;
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

    public void setLateDays(long lateDays) {
        this.lateDays = lateDays;
    }

    public void setFineAmount(double fineAmount) {
        this.fineAmount = fineAmount;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
