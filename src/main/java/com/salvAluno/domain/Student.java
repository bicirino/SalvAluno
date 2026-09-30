package com.salvAluno.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "alunos")
public class Student {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 80)
	private String name;

	@Column(nullable = false, unique = true, length = 20)
	private String ra;

	/** Hash BCrypt. Serve só para entrar no SalvAluno e não volta para a senha original. */
	@Column(name = "password_hash", nullable = false, length = 100)
	private String passwordHash;

	/** Senha do portal cifrada com AES-256-GCM, para o robô conseguir fazer login. */
	@Column(name = "portal_password_cipher", nullable = false, length = 512)
	private String portalPasswordCipher;

	protected Student() {
	}

	public Student(String name, String ra, String passwordHash, String portalPasswordCipher) {
		this.name = name;
		this.ra = ra;
		this.passwordHash = passwordHash;
		this.portalPasswordCipher = portalPasswordCipher;
	}

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public String getRa() {
		return ra;
	}

	public String getPasswordHash() {
		return passwordHash;
	}

	public String getPortalPasswordCipher() {
		return portalPasswordCipher;
	}
}
