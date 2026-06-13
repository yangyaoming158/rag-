package com.ragdocs.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ragdocs.common.ApiResponse;
import com.ragdocs.common.ErrorCode;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class JwtAuthenticationFilter implements Filter {
    public static final String CURRENT_USER_ATTRIBUTE = "currentUser";

    private final JwtService jwtService;
    private final ObjectMapper objectMapper;

    public JwtAuthenticationFilter(JwtService jwtService, ObjectMapper objectMapper) {
        this.jwtService = jwtService;
        this.objectMapper = objectMapper;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        if (isPublic(httpRequest)) {
            chain.doFilter(request, response);
            return;
        }

        String authorization = httpRequest.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            writeUnauthorized(httpResponse);
            return;
        }

        try {
            CurrentUser currentUser = jwtService.parse(authorization.substring("Bearer ".length()).trim());
            httpRequest.setAttribute(CURRENT_USER_ATTRIBUTE, currentUser);
            chain.doFilter(request, response);
        } catch (RuntimeException ex) {
            writeUnauthorized(httpResponse);
        }
    }

    private boolean isPublic(HttpServletRequest request) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        return "POST".equalsIgnoreCase(request.getMethod()) && "/api/auth/login".equals(request.getRequestURI());
    }

    private void writeUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json;charset=UTF-8");
        objectMapper.writeValue(response.getWriter(),
                ApiResponse.error(ErrorCode.UNAUTHENTICATED, ErrorCode.UNAUTHENTICATED.defaultMessage()));
    }
}
