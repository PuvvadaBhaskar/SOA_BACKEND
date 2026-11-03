package com.archivalia.book.web;

import java.io.IOException;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Adds {@code X-Served-By: <service>:<port>} to every response so that load balancing across
 * several instances is visible from the client (e.g. curl -i through the gateway).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ServedByFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Served-By";

    private final InstanceInfo instanceInfo;

    public ServedByFilter(InstanceInfo instanceInfo) {
        this.instanceInfo = instanceInfo;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        response.setHeader(HEADER, instanceInfo.servedBy());
        chain.doFilter(request, response);
    }
}
