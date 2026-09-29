package com.salvAluno.repository;

import com.salvAluno.domain.Student;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StudentRepository extends JpaRepository<Student, Long> {

	Optional<Student> findByRa(String ra);

	boolean existsByRa(String ra);
}
