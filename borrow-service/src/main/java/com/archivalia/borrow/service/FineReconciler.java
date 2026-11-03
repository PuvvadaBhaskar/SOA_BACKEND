package com.archivalia.borrow.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Background retry for fines that could not be calculated while fine-service was down.
 * Disable with borrow.fine-reconciler.enabled=false.
 */
@Component
@ConditionalOnProperty(name = "borrow.fine-reconciler.enabled", havingValue = "true", matchIfMissing = true)
public class FineReconciler {

    private final BorrowService borrowService;

    public FineReconciler(BorrowService borrowService) {
        this.borrowService = borrowService;
    }

    @Scheduled(
            initialDelayString = "${borrow.fine-reconciler.initial-delay-ms:30000}",
            fixedDelayString = "${borrow.fine-reconciler.interval-ms:60000}")
    public void retryPendingFines() {
        borrowService.retryPendingFines();
    }
}
