package com.example.maintenance.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.*;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.UUID;

@Component
public class TraceFilter extends OncePerRequestFilter {
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws ServletException,IOException {
        String trace = UUID.randomUUID().toString(); MDC.put("traceId",trace);response.setHeader("X-Trace-Id",trace);
        try { chain.doFilter(request,response); } finally { MDC.remove("traceId"); }
    }
}
