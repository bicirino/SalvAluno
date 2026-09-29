package com.salvAluno.security;

import com.salvAluno.service.AuthService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class SessionAuthFilter extends HttpFilter {

	@Override
	protected void doFilter(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws IOException, ServletException {
		response.setHeader("X-Content-Type-Options", "nosniff");
		response.setHeader("Referrer-Policy", "same-origin");
		response.setHeader("X-Frame-Options", "SAMEORIGIN");

		String path = caminho(request);
		if (publico(request.getMethod(), path)) {
			chain.doFilter(request, response);
			return;
		}

		HttpSession session = request.getSession(false);
		if (session != null && session.getAttribute(AuthService.SESSION_ALUNO_ID) != null) {
			chain.doFilter(request, response);
			return;
		}

		if (path.startsWith("/api/")) {
			response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
			response.setContentType("application/json;charset=UTF-8");
			response.setHeader("Cache-Control", "no-store");
			response.getWriter().write("{\"message\":\"Faça login para continuar.\"}");
			return;
		}

		response.sendRedirect(request.getContextPath() + "/login.html");
	}

	private static boolean publico(String method, String path) {
		boolean leitura = "GET".equals(method) || "HEAD".equals(method);
		if (leitura && (path.equals("/login.html") || path.equals("/style.css") || path.equals("/favicon.ico"))) {
			return true;
		}
		return "POST".equals(method)
				&& (path.equals("/api/auth/login")
				|| path.equals("/api/auth/register")
				|| path.equals("/api/auth/logout"));
	}

	private static String caminho(HttpServletRequest request) {
		String uri = request.getRequestURI();
		String context = request.getContextPath();
		if (context != null && !context.isEmpty() && uri.startsWith(context)) {
			return uri.substring(context.length());
		}
		return uri;
	}
}
