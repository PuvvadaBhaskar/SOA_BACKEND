package com.archivalia.fine.controller;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import com.archivalia.fine.dto.FineCreateRequest;
import com.archivalia.fine.dto.FineUpdateRequest;
import com.archivalia.fine.entity.Fine;
import com.archivalia.fine.entity.FineStatus;
import com.archivalia.fine.security.GatewayUser;
import com.archivalia.fine.service.FineService;
import com.archivalia.fine.web.InstanceInfo;

import jakarta.validation.Valid;

/**
 * Fines. Owners can read their own fines, ADMIN can read/update/delete all.
 * The gateway enforces the same rules - these checks are the second line of defence.
 */
@RestController
@RequestMapping("/fines")
public class FineController {

    private final FineService service;
    private final InstanceInfo instanceInfo;

    public FineController(FineService service, InstanceInfo instanceInfo) {
        this.service = service;
        this.instanceInfo = instanceInfo;
    }

    /**
     * Called by borrow-service when a book is returned (internal call carrying the gateway secret;
     * at the gateway POST /fines/** is ADMIN-only). Idempotent per borrowId.
     */
    @PostMapping("/calculate")
    public ResponseEntity<Fine> calculateFine(

            @RequestParam Long borrowId,
            @RequestParam Long userId,
            @RequestParam Long bookId,
            @RequestParam String dueDate,
            @RequestParam String returnDate) {

        Fine fine = service.calculateFine(
                borrowId,
                userId,
                bookId,
                LocalDate.parse(dueDate),
                LocalDate.parse(returnDate)
        );

        return ResponseEntity.ok(fine);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Fine> createFine(@Valid @RequestBody FineCreateRequest request) {

        Fine fine = service.createFine(request);

        return ResponseEntity.created(URI.create("/fines/" + fine.getId())).body(fine);
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Fine>> getAllFines(
            @RequestParam(required = false) FineStatus status) {

        return ResponseEntity.ok(
                service.getAllFines(status)
        );
    }

    @GetMapping("/me")
    public ResponseEntity<List<Fine>> getMyFines(
            @AuthenticationPrincipal GatewayUser user) {

        return ResponseEntity.ok(
                service.getUserFines(user.id(), user)
        );
    }

    @GetMapping("/instance")
    public ResponseEntity<Map<String, Object>> instance() {

        return ResponseEntity.ok(
                instanceInfo.describe()
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<Fine> getFine(
            @PathVariable Long id,
            @AuthenticationPrincipal GatewayUser user) {

        return ResponseEntity.ok(
                service.getFine(id, user)
        );
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<Fine>> getUserFines(
            @PathVariable Long userId,
            @AuthenticationPrincipal GatewayUser user) {

        return ResponseEntity.ok(
                service.getUserFines(userId, user)
        );
    }

    /** ADMIN: e.g. {"status":"PAID"} to mark a fine as paid. */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Fine> updateFine(
            @PathVariable Long id,
            @Valid @RequestBody FineUpdateRequest request) {

        return ResponseEntity.ok(
                service.updateFine(id, request)
        );
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> deleteFine(
            @PathVariable Long id) {

        service.deleteFine(id);

        return ResponseEntity.ok(
                Map.of("message", "Fine deleted successfully")
        );
    }
}
