package com.archivalia.fine.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.archivalia.fine.dto.FineCreateRequest;
import com.archivalia.fine.dto.FineUpdateRequest;
import com.archivalia.fine.entity.Fine;
import com.archivalia.fine.entity.FineStatus;
import com.archivalia.fine.exception.BadRequestException;
import com.archivalia.fine.exception.ForbiddenException;
import com.archivalia.fine.exception.ResourceNotFoundException;
import com.archivalia.fine.repository.FineRepository;
import com.archivalia.fine.security.GatewayUser;

@Service
public class FineService {

    private final FineRepository repository;
    private final double finePerDay;

    public FineService(FineRepository repository, @Value("${fine.per-day:10.0}") double finePerDay) {
        this.repository = repository;
        this.finePerDay = finePerDay;
    }

    /**
     * Calculates (and stores) the fine for a returned borrow. Idempotent per borrowId: calling it
     * again - e.g. when borrow-service retries after a timeout - returns the existing fine.
     */
    @Transactional
    public Fine calculateFine(
            Long borrowId,
            Long userId,
            Long bookId,
            LocalDate dueDate,
            LocalDate returnDate) {

        requirePositive(borrowId, "borrowId");
        requirePositive(userId, "userId");
        requirePositive(bookId, "bookId");

        return repository.findFirstByBorrowIdOrderByIdAsc(borrowId)
                .orElseGet(() -> repository.save(newCalculatedFine(borrowId, userId, bookId, dueDate, returnDate)));
    }

    /** Manual fine created by an administrator. */
    @Transactional
    public Fine createFine(FineCreateRequest request) {

        Fine fine = new Fine();
        fine.setBorrowId(request.getBorrowId());
        fine.setUserId(request.getUserId());
        fine.setBookId(request.getBookId());
        fine.setFineAmount(request.getFineAmount());
        fine.setLateDays(0);
        fine.setReason(request.getReason());
        fine.setStatus(FineStatus.UNPAID);

        return repository.save(fine);
    }

    @Transactional(readOnly = true)
    public List<Fine> getAllFines(FineStatus status) {

        return status == null ? repository.findAll() : repository.findByStatus(status);
    }

    /** Owner or ADMIN only. */
    @Transactional(readOnly = true)
    public Fine getFine(Long id, GatewayUser caller) {

        Fine fine = findFine(id);
        assertOwnerOrAdmin(fine.getUserId(), caller);
        return fine;
    }

    /** Owner or ADMIN only. */
    @Transactional(readOnly = true)
    public List<Fine> getUserFines(Long userId, GatewayUser caller) {

        assertOwnerOrAdmin(userId, caller);
        return repository.findByUserId(userId);
    }

    /** ADMIN: change status (e.g. mark as PAID / WAIVED) and/or amount. */
    @Transactional
    public Fine updateFine(Long id, FineUpdateRequest request) {

        if (request.getStatus() == null && request.getFineAmount() == null && request.getReason() == null) {
            throw new BadRequestException("Provide at least one of: status, fineAmount, reason");
        }

        Fine fine = findFine(id);

        if (request.getFineAmount() != null) {
            fine.setFineAmount(request.getFineAmount());
        }
        if (request.getReason() != null) {
            fine.setReason(request.getReason());
        }

        if (request.getStatus() != null) {
            FineStatus target = request.getStatus();
            if ((target == FineStatus.PAID || target == FineStatus.UNPAID) && fine.getFineAmount() <= 0) {
                throw new BadRequestException("Fine " + id + " has no amount due; it cannot be " + target);
            }
            fine.setStatus(target);
            fine.setPaidDate(target == FineStatus.PAID ? LocalDate.now() : null);
        } else if (request.getFineAmount() != null && fine.getStatus() != FineStatus.PAID
                && fine.getStatus() != FineStatus.WAIVED) {
            fine.setStatus(fine.getFineAmount() > 0 ? FineStatus.UNPAID : FineStatus.NO_FINE);
        }

        return repository.save(fine);
    }

    @Transactional
    public void deleteFine(Long id) {

        repository.delete(findFine(id));
    }

    private Fine newCalculatedFine(Long borrowId, Long userId, Long bookId, LocalDate dueDate, LocalDate returnDate) {

        if (dueDate == null || returnDate == null) {
            throw new BadRequestException("dueDate and returnDate are required");
        }

        long lateDays = 0;

        if (returnDate.isAfter(dueDate)) {

            lateDays = ChronoUnit.DAYS.between(
                    dueDate,
                    returnDate
            );
        }

        double amount = lateDays * finePerDay;

        Fine fine = new Fine();

        fine.setBorrowId(borrowId);
        fine.setUserId(userId);
        fine.setBookId(bookId);
        fine.setLateDays(lateDays);
        fine.setFineAmount(amount);
        fine.setStatus(amount > 0 ? FineStatus.UNPAID : FineStatus.NO_FINE);
        fine.setReason(amount > 0 ? "Returned " + lateDays + " day(s) late" : null);

        return fine;
    }

    private Fine findFine(Long id) {

        return repository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Fine not found with id " + id));
    }

    private static void assertOwnerOrAdmin(Long ownerId, GatewayUser caller) {
        if (!caller.isAdmin() && !caller.owns(ownerId)) {
            throw new ForbiddenException("You can only access your own fines");
        }
    }

    private static void requirePositive(Long value, String name) {
        if (value == null || value <= 0) {
            throw new BadRequestException(name + " must be a positive number");
        }
    }
}
