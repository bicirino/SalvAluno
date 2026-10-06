package com.salvAluno.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class QuestionariosSalaTest {

	@Test
	void questionariosHerdamAbertoEFechadoDaSecaoEIgnoramMateriais() {
		List<QuestionariosSala.Extraido> atividades = QuestionariosSala.interpretar(List.of(
				secao("Aberto: quinta-feira, 20 ago. 2026, 20:30 Fechado: quinta-feira, 27 ago. 2026, 18:05"),
				questionario("04 Questionário Avaliação - JS Questionário", "https://salaonline.ceub.br/mod/quiz/view.php?id=4"),
				secao("Aberto: quinta-feira, 27 ago. 2026, 21:00 Fechado: segunda-feira, 31 ago. 2026, 16:56"),
				questionario("05 Questionário Avaliação - Responsabilidade", "https://salaonline.ceub.br/mod/quiz/view.php?id=5"),
				secao("Aberto: quinta-feira, 17 set. 2026, 20:30 Fechado: quinta-feira, 24 set. 2026, 17:02")
		));

		assertThat(atividades).hasSize(2);
		assertThat(atividades.get(0).titulo()).isEqualTo("04 Questionário Avaliação - JS");
		assertThat(atividades.get(0).inicio()).isEqualTo(LocalDateTime.of(2026, 8, 20, 20, 30));
		assertThat(atividades.get(0).fim()).isEqualTo(LocalDateTime.of(2026, 8, 27, 18, 5));
		assertThat(atividades.get(1).titulo()).isEqualTo("05 Questionário Avaliação - Responsabilidade");
		assertThat(atividades.get(1).inicio()).isEqualTo(LocalDateTime.of(2026, 8, 27, 21, 0));
		assertThat(atividades.get(1).fim()).isEqualTo(LocalDateTime.of(2026, 8, 31, 16, 56));
	}

	@Test
	void blocoComDatasAtualizaOPrazoDosQuestionariosSeguintes() {
		List<QuestionariosSala.Extraido> atividades = QuestionariosSala.interpretar(List.of(
				secao(""),
				bloco("Material - Revisão CSS"),
				bloco("Aberto: quinta-feira, 20 de agosto de 2026, 20:30\nFechado: quinta-feira, 27 de agosto de 2026, 18:05"),
				questionario("04 Questionário Avaliação - JS", "https://salaonline.ceub.br/mod/quiz/view.php?id=4")
		));

		assertThat(atividades).singleElement().satisfies(atividade -> {
			assertThat(atividade.inicio()).isEqualTo(LocalDateTime.of(2026, 8, 20, 20, 30));
			assertThat(atividade.fim()).isEqualTo(LocalDateTime.of(2026, 8, 27, 18, 5));
		});
	}

	@Test
	void tarefaDaSalaEntraEMaterialDeApoioFicaDeFora() {
		List<QuestionariosSala.Extraido> atividades = QuestionariosSala.interpretar(List.of(
				secao("Aberto: 10/08/2026, 08:00 Fechado: 20/08/2026, 23:00"),
				new QuestionariosSala.Evento("atividade", "assign", "Entrega do trabalho Tarefa", "https://salaonline.ceub.br/mod/assign/view.php?id=9"),
				new QuestionariosSala.Evento("atividade", "resource", "Material - Revisão CSS", "https://salaonline.ceub.br/mod/resource/view.php?id=3")
		));

		assertThat(atividades).singleElement().satisfies(atividade -> {
			assertThat(atividade.titulo()).isEqualTo("Entrega do trabalho");
			assertThat(atividade.href()).contains("/mod/assign/");
		});
	}

	@Test
	void aceitaPrazoNumericoENaoRepeteOMesmoQuestionario() {
		List<QuestionariosSala.Extraido> atividades = QuestionariosSala.interpretar(List.of(
				secao("Aberto: 20/08/2026, 20:30 Fechado: 27/08/26 18:05"),
				questionario("Questionário 1", "https://salaonline.ceub.br/mod/quiz/view.php?id=1"),
				questionario("Questionário 1", "https://salaonline.ceub.br/mod/quiz/view.php?id=1")
		));

		assertThat(atividades).singleElement().satisfies(atividade -> {
			assertThat(atividade.inicio()).isEqualTo(LocalDateTime.of(2026, 8, 20, 20, 30));
			assertThat(atividade.fim()).isEqualTo(LocalDateTime.of(2026, 8, 27, 18, 5));
		});
	}

	private static QuestionariosSala.Evento secao(String texto) {
		return new QuestionariosSala.Evento("periodo", "secao", texto, "");
	}

	private static QuestionariosSala.Evento bloco(String texto) {
		return new QuestionariosSala.Evento("periodo", "bloco", texto, "");
	}

	private static QuestionariosSala.Evento questionario(String titulo, String href) {
		return new QuestionariosSala.Evento("questionario", "", titulo, href);
	}
}
