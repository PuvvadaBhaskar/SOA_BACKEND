package com.archivalia.borrow.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.archivalia.borrow.dto.BorrowRequest;
import com.archivalia.borrow.dto.FineResponse;
import com.archivalia.borrow.dto.ReturnResponse;
import com.archivalia.borrow.entity.Borrow;
import com.archivalia.borrow.entity.BorrowStatus;
import com.archivalia.borrow.exception.ForbiddenException;
import com.archivalia.borrow.exception.ResourceNotFoundException;
import com.archivalia.borrow.exception.ServiceUnavailableException;
import com.archivalia.borrow.security.GatewayUser;
import com.archivalia.borrow.service.BorrowService;
import com.archivalia.borrow.web.ServedByFilter;

import jakarta.servlet.Filter;

@SpringBootTest
class BorrowControllerTest {

    private static final String SECRET = "test-gateway-secret";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ServedByFilter servedByFilter;

    @MockitoBean
    private BorrowService borrowService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        Filter security = context.getBean("springSecurityFilterChain", Filter.class);
        mockMvc = MockMvcBuilders.webAppContextSetup(context).addFilters(servedByFilter, security).build();
    }

    @Test
    void withoutGatewayHeadersReturns401() throws Exception {
        mockMvc.perform(post("/borrows").contentType(MediaType.APPLICATION_JSON).content("{\"bookId\":3}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
        verify(borrowService, never()).borrowBook(any(), any());
    }

    @Test
    void borrowUsesUserIdFromHeaderNotFromBody() throws Exception {
        when(borrowService.borrowBook(any(BorrowRequest.class), any(GatewayUser.class))).thenReturn(borrow(10L, 7L));

        mockMvc.perform(asUser(post("/borrows")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookId\":3,\"userId\":999}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/borrows/10"))
                .andExpect(header().exists(ServedByFilter.HEADER))
                .andExpect(jsonPath("$.userId").value(7));

        ArgumentCaptor<GatewayUser> caller = ArgumentCaptor.forClass(GatewayUser.class);
        verify(borrowService).borrowBook(any(BorrowRequest.class), caller.capture());
        org.assertj.core.api.Assertions.assertThat(caller.getValue().id()).isEqualTo(7L);
    }

    @Test
    void borrowWithoutBookIdReturns400() throws Exception {
        mockMvc.perform(asUser(post("/borrows")).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fieldErrors.bookId").exists());
    }

    @Test
    void bookServiceDownReturns503() throws Exception {
        when(borrowService.borrowBook(any(BorrowRequest.class), any(GatewayUser.class)))
                .thenThrow(new ServiceUnavailableException("book-service is unavailable, please retry later"));

        mockMvc.perform(asUser(post("/borrows")).contentType(MediaType.APPLICATION_JSON).content("{\"bookId\":3}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.error").value("Service Unavailable"));
    }

    @Test
    void listAllAsUserReturns403() throws Exception {
        mockMvc.perform(asUser(get("/borrows")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
        verify(borrowService, never()).getAllBorrows(any());
    }

    @Test
    void listAllAsAdminReturns200() throws Exception {
        when(borrowService.getAllBorrows(isNull())).thenReturn(List.of(borrow(1L, 7L)));

        mockMvc.perform(asAdmin(get("/borrows")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1));
    }

    @Test
    void deleteAsUserReturns403() throws Exception {
        mockMvc.perform(asUser(delete("/borrows/1")))
                .andExpect(status().isForbidden());
        verify(borrowService, never()).deleteBorrow(any());
    }

    @Test
    void deleteAsAdminReturns200() throws Exception {
        mockMvc.perform(asAdmin(delete("/borrows/1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Borrow record deleted successfully"));
    }

    @Test
    void unknownBorrowReturns404() throws Exception {
        when(borrowService.getBorrow(eq(99L), any(GatewayUser.class)))
                .thenThrow(new ResourceNotFoundException("Borrow record not found with id 99"));

        mockMvc.perform(asUser(get("/borrows/99")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Borrow record not found with id 99"))
                .andExpect(jsonPath("$.path").value("/borrows/99"));
    }

    @Test
    void someoneElsesBorrowReturns403() throws Exception {
        when(borrowService.getBorrow(eq(5L), any(GatewayUser.class)))
                .thenThrow(new ForbiddenException("You can only access your own borrows"));

        mockMvc.perform(asUser(get("/borrows/5")))
                .andExpect(status().isForbidden());
    }

    @Test
    void returnIncludesFine() throws Exception {
        Borrow returned = borrow(5L, 7L);
        returned.setStatus(BorrowStatus.RETURNED);
        FineResponse fine = new FineResponse();
        fine.setId(40L);
        fine.setFineAmount(20.0);
        when(borrowService.returnBook(eq(5L), any(GatewayUser.class)))
                .thenReturn(new ReturnResponse(returned, fine, null));

        mockMvc.perform(asUser(put("/borrows/5/return")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.borrow.status").value("RETURNED"))
                .andExpect(jsonPath("$.fine.fineAmount").value(20.0));
    }

    @Test
    void extendWithInvalidDaysReturns400() throws Exception {
        mockMvc.perform(asUser(put("/borrows/5/extend")).contentType(MediaType.APPLICATION_JSON).content("{\"days\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.days").exists());
    }

    @Test
    void extendWithoutBodyUsesDefault() throws Exception {
        when(borrowService.extendBorrow(eq(5L), isNull(), any(GatewayUser.class))).thenReturn(borrow(5L, 7L));

        mockMvc.perform(asUser(put("/borrows/5/extend")))
                .andExpect(status().isOk());
    }

    @Test
    void instanceEndpointWorks() throws Exception {
        mockMvc.perform(asUser(get("/borrows/instance")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.service").value("borrow-service"));
    }

    private static MockHttpServletRequestBuilder asUser(MockHttpServletRequestBuilder builder) {
        return builder.header("X-Gateway-Secret", SECRET)
                .header("X-User-Id", "7").header("X-User-Name", "alice").header("X-User-Role", "USER");
    }

    private static MockHttpServletRequestBuilder asAdmin(MockHttpServletRequestBuilder builder) {
        return builder.header("X-Gateway-Secret", SECRET)
                .header("X-User-Id", "1").header("X-User-Name", "admin").header("X-User-Role", "ADMIN");
    }

    private static Borrow borrow(Long id, Long userId) {
        Borrow borrow = new Borrow();
        borrow.setId(id);
        borrow.setUserId(userId);
        borrow.setBookId(3L);
        borrow.setBorrowDate(LocalDate.now());
        borrow.setDueDate(LocalDate.now().plusDays(7));
        borrow.setStatus(BorrowStatus.BORROWED);
        return borrow;
    }
}
