package com.archivalia.fine.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.archivalia.fine.dto.FineCreateRequest;
import com.archivalia.fine.dto.FineUpdateRequest;
import com.archivalia.fine.entity.Fine;
import com.archivalia.fine.entity.FineStatus;
import com.archivalia.fine.exception.BadRequestException;
import com.archivalia.fine.exception.ForbiddenException;
import com.archivalia.fine.exception.ResourceNotFoundException;
import com.archivalia.fine.repository.FineRepository;
import com.archivalia.fine.security.GatewayUser;

@ExtendWith(MockitoExtension.class)
class FineServiceTest {

    private static final GatewayUser ALICE = new GatewayUser(7L, "alice", "USER");
    private static final GatewayUser BOB = new GatewayUser(8L, "bob", "USER");
    private static final GatewayUser ADMIN = new GatewayUser(1L, "admin", "ADMIN");

    @Mock
    private FineRepository repository;

    private FineService service;

    @BeforeEach
    void setUp() {
        service = new FineService(repository, 10.0);
    }

    @Test
    void lateReturnProducesUnpaidFine() {
        when(repository.findFirstByBorrowIdOrderByIdAsc(1L)).thenReturn(Optional.empty());
        when(repository.save(any(Fine.class))).thenAnswer(inv -> inv.getArgument(0));
        LocalDate due = LocalDate.of(2026, 3, 1);

        Fine fine = service.calculateFine(1L, 7L, 3L, due, due.plusDays(4));

        assertThat(fine.getLateDays()).isEqualTo(4);
        assertThat(fine.getFineAmount()).isEqualTo(40.0);
        assertThat(fine.getStatus()).isEqualTo(FineStatus.UNPAID);
    }

    @Test
    void onTimeReturnProducesNoFine() {
        when(repository.findFirstByBorrowIdOrderByIdAsc(1L)).thenReturn(Optional.empty());
        when(repository.save(any(Fine.class))).thenAnswer(inv -> inv.getArgument(0));
        LocalDate due = LocalDate.of(2026, 3, 1);

        Fine fine = service.calculateFine(1L, 7L, 3L, due, due.minusDays(1));

        assertThat(fine.getLateDays()).isZero();
        assertThat(fine.getFineAmount()).isZero();
        assertThat(fine.getStatus()).isEqualTo(FineStatus.NO_FINE);
    }

    @Test
    void calculateIsIdempotentPerBorrow() {
        Fine existing = fine(5L, 7L, 30.0, FineStatus.UNPAID);
        when(repository.findFirstByBorrowIdOrderByIdAsc(1L)).thenReturn(Optional.of(existing));

        Fine fine = service.calculateFine(1L, 7L, 3L, LocalDate.now(), LocalDate.now());

        assertThat(fine).isSameAs(existing);
        verify(repository, never()).save(any());
    }

    @Test
    void calculateRejectsInvalidIds() {
        assertThatThrownBy(() -> service.calculateFine(0L, 7L, 3L, LocalDate.now(), LocalDate.now()))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void ownerCanReadOwnFine() {
        when(repository.findById(5L)).thenReturn(Optional.of(fine(5L, 7L, 10.0, FineStatus.UNPAID)));

        assertThat(service.getFine(5L, ALICE).getId()).isEqualTo(5L);
    }

    @Test
    void otherUserCannotReadFine() {
        when(repository.findById(5L)).thenReturn(Optional.of(fine(5L, 7L, 10.0, FineStatus.UNPAID)));

        assertThatThrownBy(() -> service.getFine(5L, BOB)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void adminCanReadAnyFine() {
        when(repository.findById(5L)).thenReturn(Optional.of(fine(5L, 7L, 10.0, FineStatus.UNPAID)));

        assertThat(service.getFine(5L, ADMIN)).isNotNull();
    }

    @Test
    void userCannotListSomeoneElsesFines() {
        assertThatThrownBy(() -> service.getUserFines(7L, BOB)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void getAllFiltersByStatus() {
        when(repository.findByStatus(FineStatus.PAID)).thenReturn(List.of(fine(1L, 7L, 5.0, FineStatus.PAID)));

        assertThat(service.getAllFines(FineStatus.PAID)).hasSize(1);
    }

    @Test
    void markAsPaidSetsPaidDate() {
        when(repository.findById(5L)).thenReturn(Optional.of(fine(5L, 7L, 10.0, FineStatus.UNPAID)));
        when(repository.save(any(Fine.class))).thenAnswer(inv -> inv.getArgument(0));

        Fine updated = service.updateFine(5L, new FineUpdateRequest(FineStatus.PAID, null, null));

        assertThat(updated.getStatus()).isEqualTo(FineStatus.PAID);
        assertThat(updated.getPaidDate()).isEqualTo(LocalDate.now());
    }

    @Test
    void cannotPayAZeroFine() {
        when(repository.findById(5L)).thenReturn(Optional.of(fine(5L, 7L, 0.0, FineStatus.NO_FINE)));

        assertThatThrownBy(() -> service.updateFine(5L, new FineUpdateRequest(FineStatus.PAID, null, null)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void emptyUpdateIsRejected() {
        assertThatThrownBy(() -> service.updateFine(5L, new FineUpdateRequest()))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void changingAmountRecomputesStatus() {
        when(repository.findById(5L)).thenReturn(Optional.of(fine(5L, 7L, 0.0, FineStatus.NO_FINE)));
        when(repository.save(any(Fine.class))).thenAnswer(inv -> inv.getArgument(0));

        Fine updated = service.updateFine(5L, new FineUpdateRequest(null, 15.0, "Damaged cover"));

        assertThat(updated.getFineAmount()).isEqualTo(15.0);
        assertThat(updated.getStatus()).isEqualTo(FineStatus.UNPAID);
        assertThat(updated.getReason()).isEqualTo("Damaged cover");
    }

    @Test
    void manualFineIsUnpaid() {
        when(repository.save(any(Fine.class))).thenAnswer(inv -> inv.getArgument(0));

        Fine fine = service.createFine(new FineCreateRequest(null, 7L, 3L, 120.0, "Lost book"));

        assertThat(fine.getStatus()).isEqualTo(FineStatus.UNPAID);
        assertThat(fine.getFineAmount()).isEqualTo(120.0);
    }

    @Test
    void deleteUnknownFineThrowsNotFound() {
        when(repository.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteFine(9L)).isInstanceOf(ResourceNotFoundException.class);
    }

    private static Fine fine(Long id, Long userId, double amount, FineStatus status) {
        Fine fine = new Fine();
        fine.setId(id);
        fine.setUserId(userId);
        fine.setBorrowId(100L + id);
        fine.setBookId(3L);
        fine.setFineAmount(amount);
        fine.setStatus(status);
        return fine;
    }
}
