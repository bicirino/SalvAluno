package com.salvAluno.service;

import com.salvAluno.domain.Student;
import com.salvAluno.repository.StudentRepository;
import com.salvAluno.security.CredentialCipher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

@Service
public class AuthService {

	public static final String SESSION_ALUNO_ID = "ALUNO_ID";

	private static final Pattern NOME = Pattern.compile("^[\\p{L}][\\p{L}\\s'.-]{1,79}$");
	private static final Pattern RA = Pattern.compile("^[0-9]{5,20}$");
	private static final int MAX_TENTATIVAS = 8;
	private static final Duration JANELA_BLOQUEIO = Duration.ofMinutes(15);
	private static final String LOGIN_INVALIDO = "RA ou senha inválidos.";

	private final StudentRepository studentRepository;
	private final CredentialCipher credentialCipher;
	private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);
	private final String dummyHash;
	private final ConcurrentHashMap<String, Tentativa> tentativas = new ConcurrentHashMap<>();

	public AuthService(StudentRepository studentRepository, CredentialCipher credentialCipher) {
		this.studentRepository = studentRepository;
		this.credentialCipher = credentialCipher;
		this.dummyHash = encoder.encode(digest("senha-invalida-salv-aluno"));
	}

	public Student registrar(String name, String ra, String password, HttpServletRequest request) {
		String nome = normalizarNome(name);
		String raNormalizado = ra == null ? "" : ra.trim();
		if (!NOME.matcher(nome).matches()) {
			throw new AuthFailure(HttpStatus.BAD_REQUEST, "Informe seu nome com pelo menos 2 letras.");
		}
		if (!RA.matcher(raNormalizado).matches()) {
			throw new AuthFailure(HttpStatus.BAD_REQUEST, "Informe o RA apenas com números.");
		}
		validarSenha(password);
		if (studentRepository.existsByRa(raNormalizado)) {
			throw new AuthFailure(HttpStatus.CONFLICT, "Este RA já está cadastrado. Entre com a sua senha.");
		}

		Student student = new Student(
				nome,
				raNormalizado,
				encoder.encode(digest(password)),
				credentialCipher.encrypt(password)
		);
		try {
			student = studentRepository.saveAndFlush(student);
		} catch (DataIntegrityViolationException e) {
			throw new AuthFailure(HttpStatus.CONFLICT, "Este RA já está cadastrado. Entre com a sua senha.");
		}
		iniciarSessao(student, request);
		return student;
	}

	public Student entrar(String ra, String password, HttpServletRequest request) {
		String raNormalizado = ra == null ? "" : ra.trim();
		if (!RA.matcher(raNormalizado).matches() || password == null || password.isBlank()) {
			throw new AuthFailure(HttpStatus.UNAUTHORIZED, LOGIN_INVALIDO);
		}
		verificarBloqueio(raNormalizado);

		Student student = studentRepository.findByRa(raNormalizado).orElse(null);
		boolean senhaConfere = student != null && encoder.matches(digest(password), student.getPasswordHash());
		if (!senhaConfere) {
			if (student == null) {
				encoder.matches(digest(password), dummyHash);
			}
			registrarFalha(raNormalizado);
			throw new AuthFailure(HttpStatus.UNAUTHORIZED, LOGIN_INVALIDO);
		}

		tentativas.remove(raNormalizado);
		iniciarSessao(student, request);
		return student;
	}

	public void sair(HttpServletRequest request) {
		HttpSession session = request.getSession(false);
		if (session != null) {
			session.invalidate();
		}
	}

	public Student alunoDaSessao(HttpSession session) {
		if (session == null) {
			throw new AuthFailure(HttpStatus.UNAUTHORIZED, "Faça login para continuar.");
		}
		Object valor = session.getAttribute(SESSION_ALUNO_ID);
		if (!(valor instanceof Long id)) {
			throw new AuthFailure(HttpStatus.UNAUTHORIZED, "Faça login para continuar.");
		}
		return studentRepository.findById(id)
				.orElseThrow(() -> new AuthFailure(HttpStatus.UNAUTHORIZED, "Faça login para continuar."));
	}

	public String senhaDoPortal(Student student) {
		try {
			return credentialCipher.decrypt(student.getPortalPasswordCipher());
		} catch (GeneralSecurityException e) {
			throw new AuthFailure(HttpStatus.INTERNAL_SERVER_ERROR,
					"Não foi possível ler a senha salva. Cadastre-se novamente.");
		}
	}

	private void iniciarSessao(Student student, HttpServletRequest request) {
		HttpSession antiga = request.getSession(false);
		if (antiga != null) {
			antiga.invalidate();
		}
		request.getSession(true).setAttribute(SESSION_ALUNO_ID, student.getId());
	}

	private void validarSenha(String password) {
		if (password == null || password.isBlank()) {
			throw new AuthFailure(HttpStatus.BAD_REQUEST, "Informe a senha do portal.");
		}
		if (password.length() > 128) {
			throw new AuthFailure(HttpStatus.BAD_REQUEST, "A senha deve ter no máximo 128 caracteres.");
		}
	}

	private void verificarBloqueio(String ra) {
		Tentativa tentativa = tentativas.get(ra);
		if (tentativa == null) {
			return;
		}
		if (tentativa.ultima.isBefore(Instant.now().minus(JANELA_BLOQUEIO))) {
			tentativas.remove(ra);
			return;
		}
		if (tentativa.quantidade >= MAX_TENTATIVAS) {
			throw new AuthFailure(HttpStatus.TOO_MANY_REQUESTS,
					"Muitas tentativas. Aguarde alguns minutos e tente de novo.");
		}
	}

	private void registrarFalha(String ra) {
		tentativas.compute(ra, (chave, atual) -> {
			Tentativa tentativa = atual == null ? new Tentativa() : atual;
			if (tentativa.ultima.isBefore(Instant.now().minus(JANELA_BLOQUEIO))) {
				tentativa.quantidade = 0;
			}
			tentativa.quantidade++;
			tentativa.ultima = Instant.now();
			return tentativa;
		});
	}

	private static String normalizarNome(String name) {
		if (name == null) {
			return "";
		}
		return name.trim().replaceAll("\\s+", " ");
	}

	/** Evita o limite de 72 bytes do BCrypt sem guardar a senha em claro. */
	private static String digest(String password) {
		try {
			byte[] hash = MessageDigest.getInstance("SHA-256")
					.digest(password.getBytes(StandardCharsets.UTF_8));
			return Base64.getEncoder().encodeToString(hash);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 indisponível.", e);
		}
	}

	private static final class Tentativa {
		private int quantidade;
		private Instant ultima = Instant.EPOCH;
	}
}
