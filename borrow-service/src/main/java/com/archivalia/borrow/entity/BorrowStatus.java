package com.archivalia.borrow.entity;

/**
 * Borrow life-cycle:
 * <pre>
 * PENDING --(book-service decremented stock)--> BORROWED --(returned)--> RETURNED
 *    \--(book-service refused / unavailable)--> FAILED
 * </pre>
 */
public enum BorrowStatus {
    PENDING,
    BORROWED,
    RETURNED,
    FAILED
}
