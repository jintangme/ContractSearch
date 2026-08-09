package org.scse.smartcontractsearchengine.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.scse.smartcontractsearchengine.context.UserContext;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

@Slf4j
@Component
public class GatewayUserContextFilter extends OncePerRequestFilter {
    private static final String USER_ID_HEADER = "X-User-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            String userIdHeader = request.getHeader(USER_ID_HEADER);
            log.info("Request URI: {}, X-User-Id header: {}", request.getRequestURI(), userIdHeader);

            if (userIdHeader != null && !userIdHeader.isBlank()) {
                try {
                    Long userId = Long.parseLong(userIdHeader);
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(userId, null, Collections.emptyList());
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                    UserContext.setUserId(userId);
                    log.info("Successfully set userId: {} to UserContext", userId);
                } catch (NumberFormatException e) {
                    log.warn("Invalid X-User-Id header value: {}", userIdHeader);
                }
            } else {
                log.debug("No X-User-Id header found, request will be treated as anonymous");
            }
            filterChain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
            UserContext.remove();
        }
    }
}
