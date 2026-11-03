package com.archivalia.fine.dto;

import com.archivalia.fine.entity.FineStatus;

import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Admin update of a fine, e.g. {@code {"status":"PAID"}} or {@code {"fineAmount":25.0}}.
 * At least one field must be present.
 */
public class FineUpdateRequest {

    private FineStatus status;

    @PositiveOrZero(message = "fineAmount must be >= 0")
    private Double fineAmount;

    @Size(max = 255, message = "reason must be at most 255 characters")
    private String reason;

    public FineUpdateRequest() {
    }

    public FineUpdateRequest(FineStatus status, Double fineAmount, String reason) {
        this.status = status;
        this.fineAmount = fineAmount;
        this.reason = reason;
    }

    public FineStatus getStatus() {
        return status;
    }

    public void setStatus(FineStatus status) {
        this.status = status;
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
