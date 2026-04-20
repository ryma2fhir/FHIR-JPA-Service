package com.nhs;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.Enumeration;

/**
 * Servlet filter that intercepts FHIR terminology operations
 * ($expand, $validate-code, $lookup, $translate) and proxies
 * them directly to the remote terminology server with a Bearer token.
 *
 * This runs at the servlet level, before HAPI processes the request.
 */
@Component
public class TerminologyOperationInterceptor extends OncePerRequestFilter {

    private static final String[] TERMINOLOGY_OPERATIONS = {
        "$expand", "$lookup", "$translate"
    };

    private final String ontoServerUrl;
    private final TerminologyInterceptor tokenInterceptor;

    @Autowired
    public TerminologyOperationInterceptor(TerminologyInterceptor tokenInterceptor) {
        this.tokenInterceptor = tokenInterceptor;
        this.ontoServerUrl = requireEnv("ONTO_SERVER_URL").replaceAll("/$", "");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String uri = request.getRequestURI();

        if (!isTerminologyOperation(uri)) {
            // Not a terminology operation — pass through to HAPI normally
            filterChain.doFilter(request, response);
            return;
        }

        System.out.printf("[PROXY] Intercepted %s %s — forwarding to %s%n",
                request.getMethod(), uri, ontoServerUrl);

        // Build the target URL: strip /fhir prefix, append query string
        String targetPath = uri.replaceFirst("^/fhir", "");
        String queryString = request.getQueryString();
        String targetUrl = ontoServerUrl + targetPath +
                (queryString != null ? "?" + queryString : "");

        proxyRequest(request, response, targetUrl);
        // Do NOT call filterChain.doFilter() — we've handled the response
    }

    private void proxyRequest(
            HttpServletRequest inbound,
            HttpServletResponse outbound,
            String targetUrl) throws IOException {

        String token = tokenInterceptor.getBearerToken();

        HttpURLConnection conn = (HttpURLConnection)
                URI.create(targetUrl).toURL().openConnection();
        conn.setRequestMethod(inbound.getMethod());
        conn.setDoOutput(inbound.getMethod().equals("POST") ||
                         inbound.getMethod().equals("PUT"));
        conn.setConnectTimeout(10_000);
        conn.setReadTimeout(30_000);

        // Forward original headers (except Host)
        Enumeration<String> headerNames = inbound.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String name = headerNames.nextElement();
            if (!name.equalsIgnoreCase("host")) {
                conn.setRequestProperty(name, inbound.getHeader(name));
            }
        }

        // Inject the Bearer token
        if (token != null && !token.isEmpty()) {
            conn.setRequestProperty("Authorization", "Bearer " + token);
        }

        // Forward request body if present
        if (conn.getDoOutput()) {
            try (InputStream in = inbound.getInputStream();
                 OutputStream out = conn.getOutputStream()) {
                in.transferTo(out);
            }
        }

        // Stream the remote response back to the caller
        int status = conn.getResponseCode();
        outbound.setStatus(status);
        outbound.setContentType(conn.getContentType());

        InputStream responseStream = status >= 400
                ? conn.getErrorStream()
                : conn.getInputStream();

        if (responseStream != null) {
            try (OutputStream out = outbound.getOutputStream()) {
                responseStream.transferTo(out);
            }
        }
    }

    private boolean isTerminologyOperation(String uri) {
        for (String op : TERMINOLOGY_OPERATIONS) {
            if (uri.contains(op)) return true;
        }
        return false;
    }

    private static String requireEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "[FILTER] Required environment variable '" + name + "' is not set.");
        }
        return value;
    }
}