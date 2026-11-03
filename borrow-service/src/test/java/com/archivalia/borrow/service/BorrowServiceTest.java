package com.archivalia.borrow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.archivalia.borrow.client.BookClient;
import com.archivalia.borrow.client.FineClient;
import com.archivalia.borrow.client.InternalHeaders;
import com.archivalia.borrow.dto.BookResponse;
import com.archivalia.borrow.dto.BorrowRequest;
import com.archivalia.borrow.dto.FineResponse;
import com.archivalia.borrow.dto.ReturnResponse;
import com.archivalia.borrow.entity.Borrow;
import com.archivalia.borrow.entity.BorrowStatus;
import com.archivalia.borrow.exception.BadRequestException;
import com.archivalia.borrow.exception.ConflictException;
import com.archivalia.borrow.exception.ForbiddenException;
import com.archivalia.borrow.exception.ResourceNotFoundException;
import com.archivalia.borrow.exception.ServiceUnavailableException;
import com.archivalia.borrow.repository.BorrowRepository;
import com.archivalia.borrow.security.GatewayUser;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BorrowServiceTest {

    private static final GatewayUser ALICE = new GatewayUser(7L, "alice", "USER");
    private static final GatewayUser BOB = new GatewayUser(8L, "bob", "USER");
    private static final GatewayUser ADMIN = new GatewayUser(1L, "admin", "ADMIN");

    @Mock
    private BorrowRepository repository;
    @Mock
    private BookClient bookClient;
    @Mock
    private FineClient fineClient;

    private BorrowService service;

    /** Status of the entity at the moment of each save() call. */
    private final List<BorrowStatus> savedStatuses = new ArrayList<>();

    @BeforeEach
    void setUp() {
        service = new BorrowService(repository, bookClient, fineClient,
                new InternalHeaders("secret"), 7, 7, 2);
        when(repository.save(any(Borrow.class))).thenAnswer(inv -> {
            Borrow b = inv.getArgument(0);
            if (b.getId() == null) {
                b.setId(100L);
            }
            savedStatuses.add(b.getStatus());
            return b;
        });
    }

    // ---------- borrow ----------

    @Test
    void borrowSavesPendingThenBorrowedAndUsesCallerIdentity() {
        when(bookClient.borrowBook(eq(3L), anyMap())).thenReturn(new BookResponse());

        Borrow borrow = service.borrowBook(new BorrowRequest(3L), ALICE);

        assertThat(savedStatuses).containsExactly(BorrowStatus.PENDING, BorrowStatus.BORROWED);
        assertThat(borrow.getUserId()).isEqualTo(7L);
        assertThat(borrow.getDueDate()).isEqualTo(LocalDate.now().plusDays(7));
    }

    @Test
    void borrowForwardsGatewayHeadersToBookService() {
        when(bookClient.borrowBook(eq(3L), anyMap())).thenReturn(new BookResponse());

        service.borrowBook(new BorrowRequest(3L), ALICE);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> headers = ArgumentCaptor.forClass(Map.class);
        verify(bookClient).borrowBook(eq(3L), headers.capture());
        assertThat(headers.getValue())
                .containsEntry("X-User-Id", "7")
                .containsEntry("X-User-Name", "alice")
                .containsEntry("X-User-Role", "USER")
                .containsEntry("X-Gateway-Secret", "secret");
    }

    @Test
    void borrowMarksFailedWhenNoCopiesAvailable() {
        when(bookClient.borrowBook(eq(3L), anyMap()))
                .thenThrow(new ConflictException("No copies of 'X' are currently available"));

        assertThatThrownBy(() -> service.borrowBook(new BorrowRequest(3L), ALICE))
                .isInstanceOf(ConflictException.class);

        assertThat(savedStatuses).containsExactly(BorrowStatus.PENDING, BorrowStatus.FAILED);
    }

    @Test
    void borrowMarksFailedAndReturns503WhenBookServiceDown() {
        when(bookClient.borrowBook(eq(3L), anyMap()))
                .thenThrow(new ServiceUnavailableException("book-service is unavailable"));

        assertThatThrownBy(() -> service.borrowBook(new BorrowRequest(3L), ALICE))
                .isInstanceOf(ServiceUnavailableException.class);

        assertThat(savedStatuses).containsExactly(BorrowStatus.PENDING, BorrowStatus.FAILED);
    }

    @Test
    void borrowUnknownBookIs404() {
        when(bookClient.borrowBook(eq(99L), anyMap()))
                .thenThrow(new ResourceNotFoundException("Book not found with id 99"));

        assertThatThrownBy(() -> service.borrowBook(new BorrowRequest(99L), ALICE))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void duplicateActiveBorrowIsRejected() {
        when(repository.existsByUserIdAndBookIdAndStatusIn(eq(7L), eq(3L), any())).thenReturn(true);

        assertThatThrownBy(() -> service.borrowBook(new BorrowRequest(3L), ALICE))
                .isInstanceOf(ConflictException.class);
        verify(bookClient, never()).borrowBook(anyLong(), anyMap());
    }

    // ---------- return ----------

    @Test
    void returnIncludesFineDetails() {
        Borrow borrow = borrow(5L, 7L, BorrowStatus.BORROWED, LocalDate.now().minusDays(2));
        when(repository.findById(5L)).thenReturn(Optional.of(borrow));
        FineResponse fine = new FineResponse();
        fine.setId(40L);
        fine.setFineAmount(20.0);
        fine.setLateDays(2);
        fine.setStatus("UNPAID");
        when(fineClient.calculateFine(eq(5L), eq(7L), eq(3L), anyString(), anyString(), anyMap())).thenReturn(fine);

        ReturnResponse response = service.returnBook(5L, ALICE);

        assertThat(response.borrow().getStatus()).isEqualTo(BorrowStatus.RETURNED);
        assertThat(response.borrow().getFineId()).isEqualTo(40L);
        assertThat(response.borrow().isFinePending()).isFalse();
        assertThat(response.fine().getFineAmount()).isEqualTo(20.0);
        assertThat(response.warning()).isNull();
        verify(bookClient).returnBook(eq(3L), anyMap());
    }

    @Test
    void returnSucceedsWithWarningWhenFineServiceDown() {
        Borrow borrow = borrow(5L, 7L, BorrowStatus.BORROWED, LocalDate.now().minusDays(2));
        when(repository.findById(5L)).thenReturn(Optional.of(borrow));
        when(fineClient.calculateFine(anyLong(), anyLong(), anyLong(), anyString(), anyString(), anyMap()))
                .thenThrow(new ServiceUnavailableException("fine-service is unavailable"));

        ReturnResponse response = service.returnBook(5L, ALICE);

        assertThat(response.borrow().getStatus()).isEqualTo(BorrowStatus.RETURNED);
        assertThat(response.borrow().getReturnDate()).isEqualTo(LocalDate.now());
        assertThat(response.borrow().isFinePending()).isTrue();
        assertThat(response.fine()).isNull();
        assertThat(response.warning()).contains("fine");
    }

    @Test
    void returnFailsWithoutChangesWhenBookServiceDown() {
        Borrow borrow = borrow(5L, 7L, BorrowStatus.BORROWED, LocalDate.now().plusDays(3));
        when(repository.findById(5L)).thenReturn(Optional.of(borrow));
        when(bookClient.returnBook(eq(3L), anyMap()))
                .thenThrow(new ServiceUnavailableException("book-service is unavailable"));

        assertThatThrownBy(() -> service.returnBook(5L, ALICE)).isInstanceOf(ServiceUnavailableException.class);

        assertThat(borrow.getStatus()).isEqualTo(BorrowStatus.BORROWED);
        assertThat(savedStatuses).isEmpty();
    }

    @Test
    void userCannotReturnSomeoneElsesBorrow() {
        when(repository.findById(5L)).thenReturn(Optional.of(borrow(5L, 7L, BorrowStatus.BORROWED, LocalDate.now())));

        assertThatThrownBy(() -> service.returnBook(5L, BOB)).isInstanceOf(ForbiddenException.class);
        verify(bookClient, never()).returnBook(anyLong(), anyMap());
    }

    @Test
    void adminCanReturnAnyBorrow() {
        when(repository.findById(5L)).thenReturn(Optional.of(borrow(5L, 7L, BorrowStatus.BORROWED, LocalDate.now())));
        when(fineClient.calculateFine(anyLong(), anyLong(), anyLong(), anyString(), anyString(), anyMap()))
                .thenReturn(new FineResponse());

        assertThat(service.returnBook(5L, ADMIN).borrow().getStatus()).isEqualTo(BorrowStatus.RETURNED);
    }

    @Test
    void returningTwiceIsConflict() {
        when(repository.findById(5L)).thenReturn(Optional.of(borrow(5L, 7L, BorrowStatus.RETURNED, LocalDate.now())));

        assertThatThrownBy(() -> service.returnBook(5L, ALICE)).isInstanceOf(ConflictException.class);
    }

    @Test
    void unknownBorrowIs404() {
        when(repository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getBorrow(404L, ADMIN)).isInstanceOf(ResourceNotFoundException.class);
    }

    // ---------- extend / delete / read ----------

    @Test
    void extendMovesDueDateAndCountsExtensions() {
        LocalDate due = LocalDate.now().plusDays(2);
        when(repository.findById(5L)).thenReturn(Optional.of(borrow(5L, 7L, BorrowStatus.BORROWED, due)));

        Borrow extended = service.extendBorrow(5L, null, ALICE);

        assertThat(extended.getDueDate()).isEqualTo(due.plusDays(7));
        assertThat(extended.getExtensionCount()).isEqualTo(1);
    }

    @Test
    void extendOverdueBorrowIsRejected() {
        when(repository.findById(5L))
                .thenReturn(Optional.of(borrow(5L, 7L, BorrowStatus.BORROWED, LocalDate.now().minusDays(1))));

        assertThatThrownBy(() -> service.extendBorrow(5L, 3, ALICE)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void extendBeyondLimitIsRejected() {
        Borrow borrow = borrow(5L, 7L, BorrowStatus.BORROWED, LocalDate.now().plusDays(5));
        borrow.setExtensionCount(2);
        when(repository.findById(5L)).thenReturn(Optional.of(borrow));

        assertThatThrownBy(() -> service.extendBorrow(5L, 3, ALICE)).isInstanceOf(ConflictException.class);
    }

    @Test
    void deleteActiveBorrowIsRejected() {
        when(repository.findById(5L)).thenReturn(Optional.of(borrow(5L, 7L, BorrowStatus.BORROWED, LocalDate.now())));

        assertThatThrownBy(() -> service.deleteBorrow(5L)).isInstanceOf(ConflictException.class);
        verify(repository, never()).delete(any());
    }

    @Test
    void deleteReturnedBorrow() {
        Borrow borrow = borrow(5L, 7L, BorrowStatus.RETURNED, LocalDate.now());
        when(repository.findById(5L)).thenReturn(Optional.of(borrow));

        service.deleteBorrow(5L);

        verify(repository).delete(borrow);
    }

    @Test
    void userCannotListAnotherUsersBorrows() {
        assertThatThrownBy(() -> service.getUserBorrows(7L, BOB)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void reconcilerCalculatesPendingFines() {
        Borrow pending = borrow(5L, 7L, BorrowStatus.RETURNED, LocalDate.now().minusDays(1));
        pending.setReturnDate(LocalDate.now());
        pending.setFinePending(true);
        when(repository.findByFinePendingTrue()).thenReturn(List.of(pending));
        FineResponse fine = new FineResponse();
        fine.setId(77L);
        when(fineClient.calculateFine(eq(5L), eq(7L), eq(3L), anyString(), anyString(), anyMap())).thenReturn(fine);

        assertThat(service.retryPendingFines()).isEqualTo(1);
        assertThat(pending.isFinePending()).isFalse();
        assertThat(pending.getFineId()).isEqualTo(77L);
    }

    private static Borrow borrow(Long id, Long userId, BorrowStatus status, LocalDate dueDate) {
        Borrow borrow = new Borrow();
        borrow.setId(id);
        borrow.setUserId(userId);
        borrow.setBookId(3L);
        borrow.setBorrowDate(dueDate.minusDays(7));
        borrow.setDueDate(dueDate);
        borrow.setStatus(status);
        return borrow;
    }
}
