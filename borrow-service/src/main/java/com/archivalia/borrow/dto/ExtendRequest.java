package com.archivalia.borrow.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** Optional body for PUT /borrows/{id}/extend; defaults to borrow.extension-days. */
public class ExtendRequest {

    @Min(value = 1, message = "days must be at least 1")
    @Max(value = 30, message = "days must be at most 30")
    private Integer days;

    public ExtendRequest() {
    }

    public ExtendRequest(Integer days) {
        this.days = days;
    }

    public Integer getDays() {
        return days;
    }

    public void setDays(Integer days) {
        this.days = days;
    }
}
