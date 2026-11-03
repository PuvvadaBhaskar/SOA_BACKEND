package com.archivalia.book.controller;

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

import com.archivalia.book.dto.BookRequest;
import com.archivalia.book.entity.Book;
import com.archivalia.book.exception.ConflictException;
import com.archivalia.book.exception.ResourceNotFoundException;
import com.archivalia.book.service.BookService;
import com.archivalia.book.web.ServedByFilter;

import jakarta.servlet.Filter;

/**
 * MockMvc tests through the real Spring Security filter chain (gateway-header authentication,
 * @PreAuthorize role checks) and the global exception handler.
 */
@SpringBootTest
class BookControllerTest {

    private static final String SECRET = "test-gateway-secret";
    private static final String VALID_BOOK = "{\"title\":\"Effective Java\",\"author\":\"J. Bloch\","
            + "\"isbn\":\"978-0134685991\",\"category\":\"Programming\",\"totalCopies\":3}";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ServedByFilter servedByFilter;

    @MockitoBean
    private BookService bookService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        Filter security = context.getBean("springSecurityFilterChain", Filter.class);
        mockMvc = MockMvcBuilders.webAppContextSetup(context).addFilters(servedByFilter, security).build();
    }

    // ---------- defence in depth: no / forged gateway headers ----------

    @Test
    void requestWithoutGatewayHeadersReturns401() throws Exception {
        mockMvc.perform(get("/books"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.path").value("/books"))
                .andExpect(jsonPath("$.message").value(
                        "Missing gateway credentials - this service must be called through the API gateway"));
    }

    @Test
    void forgedIdentityWithWrongSecretReturns401() throws Exception {
        mockMvc.perform(post("/books")
                        .header("X-User-Id", "1").header("X-User-Name", "admin").header("X-User-Role", "ADMIN")
                        .header("X-Gateway-Secret", "guessed")
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BOOK))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid gateway credentials"));
        verify(bookService, never()).addBook(any());
    }

    @Test
    void unknownRoleReturns401() throws Exception {
        mockMvc.perform(get("/books").header("X-Gateway-Secret", SECRET)
                        .header("X-User-Id", "1").header("X-User-Name", "x").header("X-User-Role", "SUPERUSER"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void healthIsPublic() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    // ---------- reads ----------

    @Test
    void listBooksAsUserReturns200WithServedByHeader() throws Exception {
        when(bookService.getAllBooks()).thenReturn(List.of(book(1L)));

        mockMvc.perform(asUser(get("/books")))
                .andExpect(status().isOk())
                .andExpect(header().exists(ServedByFilter.HEADER))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].title").value("Book 1"));
    }

    @Test
    void getUnknownBookReturns404Json() throws Exception {
        when(bookService.getBook(99L)).thenThrow(new ResourceNotFoundException("Book not found with id 99"));

        mockMvc.perform(asUser(get("/books/99")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Book not found with id 99"))
                .andExpect(jsonPath("$.path").value("/books/99"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    @Test
    void nonNumericIdReturns400() throws Exception {
        mockMvc.perform(asUser(get("/books/abc")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void instanceEndpointDescribesInstance() throws Exception {
        mockMvc.perform(asUser(get("/books/instance")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.service").value("book-service"))
                .andExpect(jsonPath("$.port").exists());
    }

    // ---------- writes (ADMIN only) ----------

    @Test
    void createBookAsUserReturns403() throws Exception {
        mockMvc.perform(asUser(post("/books")).contentType(MediaType.APPLICATION_JSON).content(VALID_BOOK))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
        verify(bookService, never()).addBook(any());
    }

    @Test
    void deleteBookAsUserReturns403() throws Exception {
        mockMvc.perform(asUser(delete("/books/1")))
                .andExpect(status().isForbidden());
        verify(bookService, never()).deleteBook(any());
    }

    @Test
    void createBookAsAdminReturns201() throws Exception {
        when(bookService.addBook(any(BookRequest.class))).thenReturn(book(5L));

        mockMvc.perform(asAdmin(post("/books")).contentType(MediaType.APPLICATION_JSON).content(VALID_BOOK))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/books/5"))
                .andExpect(jsonPath("$.id").value(5));
    }

    @Test
    void createInvalidBookReturns400WithFieldErrors() throws Exception {
        String invalid = "{\"title\":\"\",\"isbn\":\"12\",\"totalCopies\":-1}";

        mockMvc.perform(asAdmin(post("/books")).contentType(MediaType.APPLICATION_JSON).content(invalid))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.fieldErrors.title").exists())
                .andExpect(jsonPath("$.fieldErrors.isbn").exists())
                .andExpect(jsonPath("$.fieldErrors.totalCopies").exists());
        verify(bookService, never()).addBook(any());
    }

    @Test
    void malformedJsonReturns400() throws Exception {
        mockMvc.perform(asAdmin(post("/books")).contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Malformed or missing JSON request body"));
    }

    @Test
    void updateConflictReturns409() throws Exception {
        when(bookService.updateBook(eq(1L), any(BookRequest.class)))
                .thenThrow(new ConflictException("Another book already uses ISBN 978-0134685991"));

        mockMvc.perform(asAdmin(put("/books/1")).contentType(MediaType.APPLICATION_JSON).content(VALID_BOOK))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void deleteAsAdminReturns200() throws Exception {
        mockMvc.perform(asAdmin(delete("/books/1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Book deleted successfully"));
        verify(bookService).deleteBook(1L);
    }

    @Test
    void internalBorrowCallWithForwardedUserHeadersIsAllowed() throws Exception {
        when(bookService.borrowBook(1L)).thenReturn(book(1L));

        mockMvc.perform(asUser(put("/books/1/borrow")))
                .andExpect(status().isOk());
    }

    // ---------- helpers ----------

    private static MockHttpServletRequestBuilder asUser(MockHttpServletRequestBuilder builder) {
        return builder.header("X-Gateway-Secret", SECRET)
                .header("X-User-Id", "7").header("X-User-Name", "alice").header("X-User-Role", "USER");
    }

    private static MockHttpServletRequestBuilder asAdmin(MockHttpServletRequestBuilder builder) {
        return builder.header("X-Gateway-Secret", SECRET)
                .header("X-User-Id", "1").header("X-User-Name", "admin").header("X-User-Role", "ADMIN");
    }

    private static Book book(Long id) {
        Book book = new Book();
        book.setId(id);
        book.setTitle("Book " + id);
        book.setIsbn("978-0134685991");
        book.setTotalCopies(3);
        book.setAvailableCopies(3);
        return book;
    }
}
