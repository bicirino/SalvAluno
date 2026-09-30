package com.salvAluno.controller;

import com.salvAluno.domain.Student;
import com.salvAluno.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

	private final AuthService authService;

	public AuthController(AuthService authService) {
		this.authService = authService;
	}

	@PostMapping("/register")
	public ResponseEntity<AlunoResponse> registrar(@RequestBody CredenciaisRequest body, HttpServletRequest request) {
		Student aluno = authService.registrar(body.name(), body.ra(), body.password(), request);
		return ResponseEntity.status(HttpStatus.CREATED).body(AlunoResponse.from(aluno));
	}

	@PostMapping("/login")
	public AlunoResponse entrar(@RequestBody CredenciaisRequest body, HttpServletRequest request) {
		Student aluno = authService.entrar(body.ra(), body.password(), request);
		return AlunoResponse.from(aluno);
	}

	@PostMapping("/logout")
	public ResponseEntity<Void> sair(HttpServletRequest request) {
		authService.sair(request);
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/me")
	public AlunoResponse eu(HttpSession session) {
		return AlunoResponse.from(authService.alunoDaSessao(session));
	}
}
