package com.cms.backend.security;

import com.cms.backend.client.SupabaseAdminClient;
import com.cms.backend.exception.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class SupabaseAuthFilter extends OncePerRequestFilter {

    private final SupabaseAdminClient supabaseAdminClient;
    private final ObjectMapper objectMapper;

    public SupabaseAuthFilter(SupabaseAdminClient supabaseAdminClient, ObjectMapper objectMapper) {
        this.supabaseAdminClient = supabaseAdminClient;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/api/") || path.startsWith("/api/jobs");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            write(response, HttpServletResponse.SC_UNAUTHORIZED, "Authentication required");
            return;
        }

        try {
            AuthenticatedUser user = supabaseAdminClient.authenticate(header.substring(7).trim());
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    user,
                    null,
                    List.of(new SimpleGrantedAuthority("ROLE_USER"))
            );
            SecurityContextHolder.getContext().setAuthentication(authentication);
            filterChain.doFilter(request, response);
        } catch (ApiException ex) {
            write(response, ex.getStatus().value(), ex.getMessage());
        } catch (Exception ex) {
            write(response, HttpServletResponse.SC_UNAUTHORIZED, "Invalid session");
        }
    }

    private void write(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), Map.of("message", message));
    }
}
