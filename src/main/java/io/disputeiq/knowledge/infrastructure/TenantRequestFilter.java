package io.disputeiq.knowledge.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.disputeiq.platform.tenancy.TenantContext;
import io.disputeiq.platform.tenancy.TenantId;
import io.disputeiq.platform.web.CorrelationHeaders;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Binds {@link TenantContext} for knowledge RAG endpoints from trusted internal {@code
 * X-Tenant-Id}.
 */
@Component
public class TenantRequestFilter extends OncePerRequestFilter {

    private final ObjectMapper objectMapper;

    public TenantRequestFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/v1/knowledge");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String rawTenantId = request.getHeader(CorrelationHeaders.TENANT_ID);
        if (rawTenantId == null || rawTenantId.isBlank()) {
            writeProblem(response, "Missing required header: " + CorrelationHeaders.TENANT_ID);
            return;
        }
        TenantId tenantId;
        try {
            tenantId = TenantId.of(rawTenantId);
        } catch (IllegalArgumentException ex) {
            writeProblem(response, "Invalid UUID in header: " + CorrelationHeaders.TENANT_ID);
            return;
        }
        try {
            TenantContext.callWith(
                    tenantId,
                    () -> {
                        filterChain.doFilter(request, response);
                        return null;
                    });
        } catch (ServletException | IOException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ServletException("Unexpected failure inside tenant request scope", ex);
        }
    }

    private void writeProblem(HttpServletResponse response, String detail) throws IOException {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
        problem.setType(URI.create("https://disputeiq.io/errors/invalid-tenant-header"));
        problem.setTitle("Invalid Tenant Header");
        response.setStatus(HttpStatus.BAD_REQUEST.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        try {
            objectMapper.writeValue(response.getOutputStream(), problem);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
}
