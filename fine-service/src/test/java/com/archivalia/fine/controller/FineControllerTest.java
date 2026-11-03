package com.archivalia.fine.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.archivalia.fine.dto.FineUpdateRequest;
import com.archivalia.fine.entity.Fine;
import com.archivalia.fine.entity.FineStatus;
import com.archivalia.fine.exception.ForbiddenException;
import com.archivalia.fine.exception.ResourceNotFoundException;
import com.archivalia.fine.security.GatewayUser;
import com.archivalia.fine.service.FineService;
import com.archivalia.fine.web.ServedByFilter;

import jakarta.servlet.Filter;

@SpringBootTest
class FineControllerTest {

    private static final String SECRET = "test-gateway-secret";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ServedByFilter servedByFilter;

    @MockitoBean
    private FineService fineService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        Filter security = context.getBean("springSecurityFilterChain", Filter.class);
        mockMvc = MockMvcBuilders.webAppContextSetup(context).addFilters(servedByFilter, security).build();
    }

    @Test
    void withoutGatewayHeadersReturns401() throws Exception {
        mockMvc.perform(get("/fines/1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void ownerGetsOwnFine() throws Exception {
        when(fineService.getFine(eq(5L), any(GatewayUser.class))).thenReturn(fine(5L, 7L));

        mockMvc.perform(asUser(get("/fines/5")))
                .andExpect(status().isOk())
                .andExpect(header().exists(ServedByFilter.HEADER))
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.status").value("UNPAID"));
    }

    @Test
    void someoneElsesFineReturns403() throws Exception {
        when(fineService.getFine(eq(5L), any(GatewayUser.class)))
                .thenThrow(new ForbiddenException("You can only access your own fines"));

        mockMvc.perform(asUser(get("/fines/5")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("You can only access your own fines"));
    }

    @Test
    void unknownFineReturns404() throws Exception {
        when(fineService.getFine(eq(99L), any(GatewayUser.class)))
                .thenThrow(new ResourceNotFoundException("Fine not found with id 99"));

        mockMvc.perform(asAdmin(get("/fines/99")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.path").value("/fines/99"));
    }

    @Test
    void listAllAsUserReturns403() throws Exception {
        mockMvc.perform(asUser(get("/fines")))
                .andExpect(status().isForbidden());
        verify(fineService, never()).getAllFines(any());
    }

    @Test
    void listAllAsAdminReturns200() throws Exception {
        when(fineService.getAllFines(null)).thenReturn(List.of(fine(1L, 7L)));

        mockMvc.perform(asAdmin(get("/fines")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1));
    }

    @Test
    void invalidStatusFilterReturns400() throws Exception {
        mockMvc.perform(asAdmin(get("/fines").param("status", "MAYBE")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void markAsPaidAsAdminReturns200() throws Exception {
        Fine paid = fine(5L, 7L);
        paid.setStatus(FineStatus.PAID);
        when(fineService.updateFine(eq(5L), any(FineUpdateRequest.class))).thenReturn(paid);

        mockMvc.perform(asAdmin(put("/fines/5")).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"PAID\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"));
    }

    @Test
    void updateAsUserReturns403() throws Exception {
        mockMvc.perform(asUser(put("/fines/5")).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"PAID\"}"))
                .andExpect(status().isForbidden());
        verify(fineService, never()).updateFine(any(), any());
    }

    @Test
    void updateWithNegativeAmountReturns400() throws Exception {
        mockMvc.perform(asAdmin(put("/fines/5")).contentType(MediaType.APPLICATION_JSON).content("{\"fineAmount\":-5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.fineAmount").exists());
    }

    @Test
    void createManualFineWithoutUserIdReturns400() throws Exception {
        mockMvc.perform(asAdmin(post("/fines")).contentType(MediaType.APPLICATION_JSON).content("{\"fineAmount\":50}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.userId").exists());
    }

    @Test
    void deleteAsAdminReturns200() throws Exception {
        mockMvc.perform(asAdmin(delete("/fines/5")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Fine deleted successfully"));
        verify(fineService).deleteFine(5L);
    }

    @Test
    void deleteAsUserReturns403() throws Exception {
        mockMvc.perform(asUser(delete("/fines/5")))
                .andExpect(status().isForbidden());
    }

    @Test
    void calculateWithBadDateReturns400() throws Exception {
        mockMvc.perform(asUser(post("/fines/calculate"))
                        .param("borrowId", "1").param("userId", "7").param("bookId", "3")
                        .param("dueDate", "2026-13-45").param("returnDate", "2026-01-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void calculateWithMissingParamReturns400() throws Exception {
        mockMvc.perform(asUser(post("/fines/calculate")).param("borrowId", "1"))
                .andExpect(status().isBadRequest());
    }

    private static MockHttpServletRequestBuilder asUser(MockHttpServletRequestBuilder builder) {
        return builder.header("X-Gateway-Secret", SECRET)
                .header("X-User-Id", "7").header("X-User-Name", "alice").header("X-User-Role", "USER");
    }

    private static MockHttpServletRequestBuilder asAdmin(MockHttpServletRequestBuilder builder) {
        return builder.header("X-Gateway-Secret", SECRET)
                .header("X-User-Id", "1").header("X-User-Name", "admin").header("X-User-Role", "ADMIN");
    }

    private static Fine fine(Long id, Long userId) {
        Fine fine = new Fine();
        fine.setId(id);
        fine.setUserId(userId);
        fine.setBorrowId(10L);
        fine.setBookId(3L);
        fine.setFineAmount(20.0);
        fine.setLateDays(2);
        fine.setStatus(FineStatus.UNPAID);
        return fine;
    }
}
