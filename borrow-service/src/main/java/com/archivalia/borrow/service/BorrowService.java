package com.archivalia.borrow.service;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.archivalia.borrow.client.BookClient;
import com.archivalia.borrow.client.FineClient;
import com.archivalia.borrow.client.InternalHeaders;
import com.archivalia.borrow.dto.BorrowRequest;
import com.archivalia.borrow.dto.FineResponse;
import com.archivalia.borrow.dto.ReturnResponse;
import com.archivalia.borrow.entity.Borrow;
import com.archivalia.borrow.entity.BorrowStatus;
import com.archivalia.borrow.exception.BadRequestException;
import com.archivalia.borrow.exception.ConflictException;
import com.archivalia.borrow.exception.ForbiddenException;
import com.archivalia.borrow.exception.ResourceNotFoundException;
import com.archivalia.borrow.repository.BorrowRepository;
import com.archivalia.borrow.security.GatewayUser;

/**
 * Borrow workflow across three services.
 * <p>
 * Remote calls are deliberately NOT wrapped in one database transaction (a DB transaction cannot
 * roll back another service). Instead every step is committed and failures are handled explicitly:
 * <ul>
 *   <li><b>Borrow</b>: save PENDING -> book-service decrements stock -> save BORROWED.
 *       If book-service refuses/fails the record becomes FAILED and the mapped error (404/409/503) is returned.
 *       If the final save fails after stock was taken, the stock is given back (compensation).</li>
 *   <li><b>Return</b>: book-service increments stock first (if it is down the return fails with 503 and
 *       nothing changes) -> save RETURNED -> fine-service calculates the fine. If fine-service is down
 *       the return still succeeds, the borrow is flagged finePending and the response carries a warning;
 *       {@link FineReconciler} retries in the background (fine calculation is idempotent per borrow).</li>
 * </ul>
 */
@Service
public class BorrowService {

    private static final Logger log = LoggerFactory.getLogger(BorrowService.class);

    static final String FINE_DEFERRED_WARNING =
            "Book returned successfully, but fine-service is unavailable: the fine will be calculated automatically when it is back.";

    private final BorrowRepository repository;
    private final BookClient bookClient;
    private final FineClient fineClient;
    private final InternalHeaders internalHeaders;
    private final int loanDays;
    private final int extensionDays;
    private final int maxExtensions;

    public BorrowService(
            BorrowRepository repository,
            BookClient bookClient,
            FineClient fineClient,
            InternalHeaders internalHeaders,
            @Value("${borrow.loan-days:7}") int loanDays,
            @Value("${borrow.extension-days:7}") int extensionDays,
            @Value("${borrow.max-extensions:2}") int maxExtensions) {

        this.repository = repository;
        this.bookClient = bookClient;
        this.fineClient = fineClient;
        this.internalHeaders = internalHeaders;
        this.loanDays = loanDays;
        this.extensionDays = extensionDays;
        this.maxExtensions = maxExtensions;
    }

    /** The borrower is always the authenticated caller - never a userId from the request body. */
    public Borrow borrowBook(BorrowRequest request, GatewayUser caller) {

        Long bookId = request.getBookId();

        if (repository.existsByUserIdAndBookIdAndStatusIn(caller.id(), bookId,
                EnumSet.of(BorrowStatus.PENDING, BorrowStatus.BORROWED))) {
            throw new ConflictException("You already have an active borrow for book " + bookId);
        }

        // 1. Record the intent first, so a crash or failure below is never invisible.
        LocalDate today = LocalDate.now();
        Borrow borrow = new Borrow();
        borrow.setUserId(caller.id());
        borrow.setBookId(bookId);
        borrow.setBorrowDate(today);
        borrow.setDueDate(today.plusDays(loanDays));
        borrow.setStatus(BorrowStatus.PENDING);
        borrow.setExtensionCount(0);
        borrow.setFinePending(false);
        borrow = repository.save(borrow);

        // 2. Take a copy in book-service (atomic there; 404 unknown book, 409 no copies, 503 unavailable).
        try {
            bookClient.borrowBook(bookId, internalHeaders.forUser(caller));
        } catch (RuntimeException ex) {
            borrow.setStatus(BorrowStatus.FAILED);
            borrow.setFailureReason(truncate(ex.getMessage()));
            repository.save(borrow);
            log.info("Borrow {} for book {} FAILED: {}", borrow.getId(), bookId, ex.getMessage());
            throw ex;
        }

        // 3. Confirm. If this save fails the copy is handed back (compensation).
        borrow.setStatus(BorrowStatus.BORROWED);
        try {
            return repository.save(borrow);
        } catch (RuntimeException dbFailure) {
            compensateStock(bookId, caller);
            throw dbFailure;
        }
    }

    public ReturnResponse returnBook(Long borrowId, GatewayUser caller) {

        Borrow borrow = findBorrow(borrowId);
        assertOwnerOrAdmin(borrow, caller);

        if (borrow.getStatus() == BorrowStatus.RETURNED) {
            throw new ConflictException("Book already returned");
        }
        if (borrow.getStatus() != BorrowStatus.BORROWED) {
            throw new BadRequestException("Only BORROWED records can be returned (current status: "
                    + borrow.getStatus() + ")");
        }

        // 1. Put the copy back first: if book-service is down we fail (503) before changing anything.
        bookClient.returnBook(borrow.getBookId(), internalHeaders.forUser(caller));

        // 2. Record the return - this must never be lost.
        borrow.setReturnDate(LocalDate.now());
        borrow.setStatus(BorrowStatus.RETURNED);
        borrow.setFinePending(true);
        borrow = repository.save(borrow);

        // 3. Fine: best effort, deferred if fine-service is unavailable.
        FineResponse fine = null;
        String warning = null;
        try {
            fine = requestFine(borrow, internalHeaders.forUser(caller));
            borrow.setFineId(fine.getId());
            borrow.setFinePending(false);
            borrow = repository.save(borrow);
        } catch (RuntimeException ex) {
            log.warn("Fine calculation for borrow {} deferred: {}", borrow.getId(), ex.getMessage());
            warning = FINE_DEFERRED_WARNING;
        }

        return new ReturnResponse(borrow, fine, warning);
    }

    /** Extends the due date of an active, not overdue borrow (owner or ADMIN). */
    public Borrow extendBorrow(Long borrowId, Integer days, GatewayUser caller) {

        Borrow borrow = findBorrow(borrowId);
        assertOwnerOrAdmin(borrow, caller);

        if (borrow.getStatus() != BorrowStatus.BORROWED) {
            throw new BadRequestException("Only BORROWED records can be extended (current status: "
                    + borrow.getStatus() + ")");
        }
        if (borrow.getDueDate().isBefore(LocalDate.now())) {
            throw new BadRequestException("Overdue borrows cannot be extended - please return the book");
        }
        if (borrow.getExtensionCount() >= maxExtensions) {
            throw new ConflictException("Maximum number of extensions (" + maxExtensions + ") reached");
        }

        int extendBy = days != null ? days : extensionDays;
        borrow.setDueDate(borrow.getDueDate().plusDays(extendBy));
        borrow.setExtensionCount(borrow.getExtensionCount() + 1);

        return repository.save(borrow);
    }

    /** ADMIN only. Active borrows must be returned first so that book stock stays consistent. */
    public void deleteBorrow(Long borrowId) {

        Borrow borrow = findBorrow(borrowId);

        if (borrow.getStatus() == BorrowStatus.BORROWED) {
            throw new ConflictException("Borrow " + borrowId + " is still active - return the book before deleting it");
        }

        repository.delete(borrow);
    }

    public List<Borrow> getAllBorrows(BorrowStatus status) {

        return status == null ? repository.findAll() : repository.findByStatus(status);
    }

    public List<Borrow> getUserBorrows(Long userId, GatewayUser caller) {

        if (!caller.isAdmin() && !caller.owns(userId)) {
            throw new ForbiddenException("You can only view your own borrows");
        }
        return repository.findByUserId(userId);
    }

    public Borrow getBorrow(Long id, GatewayUser caller) {

        Borrow borrow = findBorrow(id);
        assertOwnerOrAdmin(borrow, caller);
        return borrow;
    }

    /**
     * Retries fine calculations that were deferred during a return. Safe to repeat because
     * fine-service returns the existing fine for a borrow that already has one.
     *
     * @return number of borrows whose fine was calculated
     */
    public int retryPendingFines() {

        int done = 0;
        for (Borrow borrow : repository.findByFinePendingTrue()) {
            try {
                FineResponse fine = requestFine(borrow, internalHeaders.forSystem());
                borrow.setFineId(fine.getId());
                borrow.setFinePending(false);
                repository.save(borrow);
                done++;
            } catch (RuntimeException ex) {
                log.debug("Fine for borrow {} still pending: {}", borrow.getId(), ex.getMessage());
            }
        }
        if (done > 0) {
            log.info("Calculated {} deferred fine(s)", done);
        }
        return done;
    }

    private FineResponse requestFine(Borrow borrow, Map<String, String> headers) {
        return fineClient.calculateFine(
                borrow.getId(),
                borrow.getUserId(),
                borrow.getBookId(),
                borrow.getDueDate().toString(),
                borrow.getReturnDate().toString(),
                headers
        );
    }

    private void compensateStock(Long bookId, GatewayUser caller) {
        try {
            bookClient.returnBook(bookId, internalHeaders.forUser(caller));
            log.warn("Compensated: returned the copy of book {} after a failed borrow save", bookId);
        } catch (RuntimeException ex) {
            log.error("Compensation FAILED for book {} - stock must be corrected manually", bookId, ex);
        }
    }

    private Borrow findBorrow(Long id) {

        return repository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Borrow record not found with id " + id
                        ));
    }

    private static void assertOwnerOrAdmin(Borrow borrow, GatewayUser caller) {
        if (!caller.isAdmin() && !caller.owns(borrow.getUserId())) {
            throw new ForbiddenException("You can only access your own borrows");
        }
    }

    private static String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= 255 ? message : message.substring(0, 255);
    }
}
