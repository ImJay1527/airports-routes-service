package pt.isep.sidis.airports.common.tracing;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Traces requests across distributed instances.
 * Propagates or generates X-Request-Id and attaches X-Instance to response and MDC.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestTracingFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String INSTANCE_HEADER = "X-Instance";
    public static final String MDC_REQUEST_ID = "requestId";
    public static final String MDC_INSTANCE = "instance";

    private final String instanceName;

    public RequestTracingFilter(@Value("${airports.instance-name:airports-1}") String instanceName) {
        this.instanceName = instanceName;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String requestId = request.getHeader(REQUEST_ID_HEADER);
        if (!StringUtils.hasText(requestId) || requestId.length() > 64) {
            requestId = UUID.randomUUID().toString().substring(0, 8);
        }
        MDC.put(MDC_REQUEST_ID, requestId);
        MDC.put(MDC_INSTANCE, instanceName);
        response.setHeader(REQUEST_ID_HEADER, requestId);
        response.setHeader(INSTANCE_HEADER, instanceName);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_REQUEST_ID);
            MDC.remove(MDC_INSTANCE);
        }
    }
}
