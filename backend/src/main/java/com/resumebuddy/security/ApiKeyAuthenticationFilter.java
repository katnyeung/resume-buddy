package com.resumebuddy.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

/**
 * Filter to authenticate service-to-service requests using X-API-Key header.
 *
 * This filter allows internal microservices (like interview-practice-service)
 * to call Resume API endpoints without requiring user JWT tokens.
 *
 * The filter checks for X-API-Key header and validates it against a shared secret.
 * If valid, it sets a system authentication in SecurityContext.
 */
@Component
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private static final String API_KEY_HEADER = "X-API-Key";

    @Value("${app.security.internal-api-key:}")
    private String internalApiKey;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        // Only process if API key is configured and present in request
        if (internalApiKey != null && !internalApiKey.isEmpty()) {
            String apiKey = request.getHeader(API_KEY_HEADER);

            if (apiKey != null && apiKey.equals(internalApiKey)) {
                // Valid API key - authenticate as system user
                UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                        "SYSTEM_SERVICE",  // Principal (service name)
                        null,              // Credentials (not needed)
                        Collections.singletonList(new SimpleGrantedAuthority("ROLE_SERVICE"))
                    );

                SecurityContextHolder.getContext().setAuthentication(authentication);

                logger.debug("Authenticated service request via API key from: " + request.getRemoteAddr());
            }
        }

        // Continue filter chain (JWT filter will run next if API key wasn't valid)
        filterChain.doFilter(request, response);
    }
}
