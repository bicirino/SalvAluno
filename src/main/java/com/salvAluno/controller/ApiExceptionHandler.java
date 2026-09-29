package com.salvAluno.controller;

import com.salvAluno.service.AuthFailure;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

	@ExceptionHandler(AuthFailure.class)
	public ResponseEntity<Map<String, String>> tratarAuth(AuthFailure falha) {
		return ResponseEntity.status(falha.getStatus()).body(Map.of("message", falha.getMessage()));
	}
}
