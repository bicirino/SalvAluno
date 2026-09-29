package com.salvAluno.controller;

import com.salvAluno.domain.Student;

public record AlunoResponse(String name, String ra) {

	public static AlunoResponse from(Student student) {
		return new AlunoResponse(student.getName(), student.getRa());
	}
}
