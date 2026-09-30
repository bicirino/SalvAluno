package com.salvAluno;

import com.salvAluno.domain.Student;
import com.salvAluno.repository.StudentRepository;
import com.salvAluno.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
		"spring.datasource.url=jdbc:h2:mem:auth-test;DB_CLOSE_DELAY=-1",
		"spring.jpa.hibernate.ddl-auto=create-drop",
		"app.crypto.key-path=target/auth-test.key"
})
class AuthFlowTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private StudentRepository studentRepository;

	@Autowired
	private AuthService authService;

	@Test
	void cadastroProtegeSenhaELiberaOPainelComONome() throws Exception {
		mockMvc.perform(get("/"))
				.andExpect(status().isFound())
				.andExpect(redirectedUrl("/login.html"));

		mockMvc.perform(get("/api/tasks"))
				.andExpect(status().isUnauthorized());

		MvcResult cadastro = mockMvc.perform(post("/api/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\":\"Ana Souza\",\"ra\":\"12345678\",\"password\":\"segredo-portal\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.name").value("Ana Souza"))
				.andExpect(jsonPath("$.ra").value("12345678"))
				.andExpect(jsonPath("$.password").doesNotExist())
				.andReturn();

		Student salvo = studentRepository.findByRa("12345678").orElseThrow();
		assertThat(salvo.getPasswordHash()).doesNotContain("segredo-portal");
		assertThat(salvo.getPasswordHash()).startsWith("$2");
		assertThat(salvo.getPortalPasswordCipher()).doesNotContain("segredo-portal");
		assertThat(authService.senhaDoPortal(salvo)).isEqualTo("segredo-portal");

		MockHttpSession sessao = (MockHttpSession) cadastro.getRequest().getSession(false);
		assertThat(sessao).isNotNull();
		assertThat(sessao.getAttribute(AuthService.SESSION_ALUNO_ID)).isEqualTo(salvo.getId());

		mockMvc.perform(get("/api/auth/me").session(sessao))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("Ana Souza"));

		mockMvc.perform(get("/").session(sessao))
				.andExpect(status().isOk())
				.andExpect(forwardedUrl("index.html"));

		mockMvc.perform(get("/index.html").session(sessao))
				.andExpect(status().isOk())
				.andExpect(content().string(org.hamcrest.Matchers.containsString("id=\"greeting\"")))
				.andExpect(content().string(org.hamcrest.Matchers.containsString("aluno.name")));

		mockMvc.perform(get("/login.html"))
				.andExpect(status().isOk())
				.andExpect(content().string(org.hamcrest.Matchers.containsString("Criar conta")))
				.andExpect(content().string(org.hamcrest.Matchers.containsString("id=\"ra\"")))
				.andExpect(content().string(org.hamcrest.Matchers.containsString("id=\"name\"")));

		mockMvc.perform(get("/api/tasks").session(sessao))
				.andExpect(status().isOk())
				.andExpect(content().json("[]"));

		mockMvc.perform(post("/api/auth/logout").session(sessao))
				.andExpect(status().isNoContent());

		mockMvc.perform(post("/api/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"ra\":\"12345678\",\"password\":\"errada\"}"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.message").value("RA ou senha inválidos."))
				.andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("segredo-portal"))));

		mockMvc.perform(post("/api/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"ra\":\"12345678\",\"password\":\"segredo-portal\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("Ana Souza"));
	}
}
