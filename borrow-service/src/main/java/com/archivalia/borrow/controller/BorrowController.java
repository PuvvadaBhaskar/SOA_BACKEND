package com.archivalia.borrow.controller;

import java.net.URI;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import com.archivalia.borrow.dto.BorrowRequest;
import com.archivalia.borrow.dto.ExtendRequest;
import com.archivalia.borrow.dto.ReturnResponse;
import com.archivalia.borrow.entity.Borrow;
import com.archivalia.borrow.entity.BorrowStatus;
import com.archivalia.borrow.security.GatewayUser;
import com.archivalia.borrow.service.BorrowService;
import com.archivalia.borrow.web.InstanceInfo;

import jakarta.validation.Valid;

/**
 * Borrows. The borrower is always the authenticated caller (X-User-Id from the gateway).
 * USER can create / return / extend / read their own borrows; ADMIN can read all and delete.
 */
@RestController
@RequestMapping("/borrows")
public class BorrowController {

    private final BorrowService service;
    private final InstanceInfo instanceInfo;

    public BorrowController(BorrowService service, InstanceInfo instanceInfo) {
        this.service = service;
        this.instanceInfo = instanceInfo;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ResponseEntity<Borrow> borrowBook(
            @Valid @RequestBody BorrowRequest request,
            @AuthenticationPrincipal GatewayUser user) {

        Borrow borrow = service.borrowBook(request, user);

        return ResponseEntity
                .created(URI.create("/borrows/" + borrow.getId()))
                .body(borrow);
    }

    /** Returns the book; the response includes the fine details (or a warning if they are deferred). */
    @PutMapping("/{id}/return")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ResponseEntity<ReturnResponse> returnBook(
            @PathVariable Long id,
            @AuthenticationPrincipal GatewayUser user) {

        return ResponseEntity.ok(
                service.returnBook(id, user)
        );
    }

    /** Extends the due date; optional body {"days": n}. */
    @PutMapping("/{id}/extend")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ResponseEntity<Borrow> extendBorrow(
            @PathVariable Long id,
            @Valid @RequestBody(required = false) ExtendRequest request,
            @AuthenticationPrincipal GatewayUser user) {

        return ResponseEntity.ok(
                service.extendBorrow(id, request != null ? request.getDays() : null, user)
        );
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Borrow>> getAllBorrows(
            @RequestParam(required = false) BorrowStatus status) {

        return ResponseEntity.ok(
                service.getAllBorrows(status)
        );
    }

    @GetMapping("/me")
    public ResponseEntity<List<Borrow>> getMyBorrows(
            @AuthenticationPrincipal GatewayUser user) {

        return ResponseEntity.ok(
                service.getUserBorrows(user.id(), user)
        );
    }

    /** Which instance answered - used to demonstrate load balancing through the gateway. */
    @GetMapping("/instance")
    public ResponseEntity<Map<String, Object>> instance() {

        return ResponseEntity.ok(
                instanceInfo.describe()
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<Borrow> getBorrow(
            @PathVariable Long id,
            @AuthenticationPrincipal GatewayUser user) {

        return ResponseEntity.ok(
                service.getBorrow(id, user)
        );
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<Borrow>> getUserBorrows(
            @PathVariable Long userId,
            @AuthenticationPrincipal GatewayUser user) {

        return ResponseEntity.ok(
                service.getUserBorrows(userId, user)
        );
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> deleteBorrow(
            @PathVariable Long id) {

        service.deleteBorrow(id);

        return ResponseEntity.ok(
                Map.of("message", "Borrow record deleted successfully")
        );
    }
}
