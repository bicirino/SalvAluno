package com.salvAluno.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// Classe de teste para verificar a interpretação de datas em cronogramas 
class CronogramaDatasTest {

	@Test
	void periodoUsaDataDeTerminoComoFim() {
		CronogramaDatas.Periodo periodo = CronogramaDatas.interpretar(List.of(
				"Período para elaboração e envio do Desafio - Entrega Final",
				"12/10/26",
				"25/10/26"
		));

		assertThat(periodo).isNotNull();
		assertThat(periodo.inicio()).isEqualTo(LocalDate.of(2026, 10, 12));
		assertThat(periodo.fim()).isEqualTo(LocalDate.of(2026, 10, 25));
	}

	@Test
	void oficinaComDataSoNoTituloMantemOMesmoDia() {
		CronogramaDatas.Periodo periodo = CronogramaDatas.interpretar(List.of(
				"Data para realização da Oficina Prática - Webaula Síncrona 06 - Dia 29/10/26 às 16h",
				"",
				""
		));

		assertThat(periodo.inicio()).isEqualTo(LocalDate.of(2026, 10, 29));
		assertThat(periodo.fim()).isEqualTo(LocalDate.of(2026, 10, 29));
	}

	@Test
	void intervaloNaMesmaCelulaHerdaOAnoDoTermino() {
		CronogramaDatas.Periodo periodo = CronogramaDatas.interpretar(List.of(
				"Período de Ambientação",
				"06/08 a 09/08/26",
				""
		));

		assertThat(periodo.inicio()).isEqualTo(LocalDate.of(2026, 8, 6));
		assertThat(periodo.fim()).isEqualTo(LocalDate.of(2026, 8, 9));
	}

	@Test
	void colunasSeparadasHerdamOAnoDoTermino() {
		CronogramaDatas.Periodo periodo = CronogramaDatas.interpretar(List.of(
				"Período para realização da Disciplina",
				"10/08",
				"07/11/26"
		));

		assertThat(periodo.inicio()).isEqualTo(LocalDate.of(2026, 8, 10));
		assertThat(periodo.fim()).isEqualTo(LocalDate.of(2026, 11, 7));
	}

	@Test
	void linhaSemDataEIgnorada() {
		assertThat(CronogramaDatas.interpretar(List.of(
				"Atividades",
				"Data de Início",
				"Data de Término"
		))).isNull();
	}
}
