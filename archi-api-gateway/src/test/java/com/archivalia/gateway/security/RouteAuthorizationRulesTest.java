package com.archivalia.gateway.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

class RouteAuthorizationRulesTest {

    private final RouteAuthorizationRules rules = new RouteAuthorizationRules();

    @Test
    void onlyLoginAndRegisterArePublic() {
        assertThat(rules.isPublic(HttpMethod.POST, "/auth/login")).isTrue();
        assertThat(rules.isPublic(HttpMethod.POST, "/auth/register")).isTrue();
        assertThat(rules.isPublic(HttpMethod.GET, "/auth/validate")).isFalse();
        assertThat(rules.isPublic(HttpMethod.GET, "/books")).isFalse();
    }

    @Test
    void bookRules() {
        assertThat(rules.isAllowed(HttpMethod.GET, "/books", "USER")).isTrue();
        assertThat(rules.isAllowed(HttpMethod.GET, "/books/1", "USER")).isTrue();
        assertThat(rules.isAllowed(HttpMethod.POST, "/books", "USER")).isFalse();
        assertThat(rules.isAllowed(HttpMethod.PUT, "/books/1", "USER")).isFalse();
        assertThat(rules.isAllowed(HttpMethod.PUT, "/books/1/borrow", "USER")).isFalse();
        assertThat(rules.isAllowed(HttpMethod.DELETE, "/books/1", "USER")).isFalse();
        assertThat(rules.isAllowed(HttpMethod.POST, "/books", "ADMIN")).isTrue();
        assertThat(rules.isAllowed(HttpMethod.DELETE, "/books/1", "ADMIN")).isTrue();
    }

    @Test
    void borrowRules() {
        assertThat(rules.isAllowed(HttpMethod.POST, "/borrows", "USER")).isTrue();
        assertThat(rules.isAllowed(HttpMethod.PUT, "/borrows/4/return", "USER")).isTrue();
        assertThat(rules.isAllowed(HttpMethod.PUT, "/borrows/4/extend", "USER")).isTrue();
        assertThat(rules.isAllowed(HttpMethod.GET, "/borrows/me", "USER")).isTrue();
        assertThat(rules.isAllowed(HttpMethod.GET, "/borrows", "USER")).isFalse();
        assertThat(rules.isAllowed(HttpMethod.GET, "/borrows", "ADMIN")).isTrue();
        assertThat(rules.isAllowed(HttpMethod.DELETE, "/borrows/4", "USER")).isFalse();
        assertThat(rules.isAllowed(HttpMethod.DELETE, "/borrows/4", "ADMIN")).isTrue();
    }

    @Test
    void fineRules() {
        assertThat(rules.isAllowed(HttpMethod.GET, "/fines/2", "USER")).isTrue();
        assertThat(rules.isAllowed(HttpMethod.GET, "/fines", "USER")).isFalse();
        assertThat(rules.isAllowed(HttpMethod.GET, "/fines", "ADMIN")).isTrue();
        assertThat(rules.isAllowed(HttpMethod.PUT, "/fines/2", "USER")).isFalse();
        assertThat(rules.isAllowed(HttpMethod.PUT, "/fines/2", "ADMIN")).isTrue();
        assertThat(rules.isAllowed(HttpMethod.POST, "/fines/calculate", "USER")).isFalse();
        assertThat(rules.isAllowed(HttpMethod.DELETE, "/fines/2", "ADMIN")).isTrue();
    }

    @Test
    void unknownRoutesAreDeniedByDefaultExceptForAdmin() {
        assertThat(rules.isAllowed(HttpMethod.PATCH, "/borrows/4", "USER")).isFalse();
        assertThat(rules.isAllowed(HttpMethod.PATCH, "/borrows/4", "ADMIN")).isTrue();
        assertThat(rules.isAllowed(HttpMethod.GET, "/books", null)).isFalse();
        assertThat(rules.isAllowed(HttpMethod.GET, "/books", "GUEST")).isFalse();
    }
}
