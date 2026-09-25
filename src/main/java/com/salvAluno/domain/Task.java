package com.salvAluno.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.LocalDateTime;

// "Entity" indica que esta classe é uma entidade, ou seja, ela representa uma tabela no banco de dados 
@Entity
public class Task {

    // O @Id indica que o campo "id" é a chave primária da tabela 
    // O @GeneratedValue indica que o valor do campo "id" será gerado automaticamente pelo banco de dados 
    @Id 
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	private String title;
	private String subject;
	private LocalDateTime dueDate;
	private String url;

    // Construtor protegido para uso pela JPA (Java Persistence API) 
	protected Task() {
	}

    // Construtor público para criar uma nova instância de Task com os atributos fornecidos
	public Task(String title, String subject, LocalDateTime dueDate, String url) {
		this.title = title;
		this.subject = subject;
		this.dueDate = dueDate;
		this.url = url;
	}

	public Long getId() {
		return id;
	}

	public String getTitle() {
		return title;
	}

	public String getSubject() {
		return subject;
	}

	public LocalDateTime getDueDate() {
		return dueDate;
	}

	public String getUrl() {
		return url;
	}
}