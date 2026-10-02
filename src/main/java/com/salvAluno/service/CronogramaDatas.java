package com.salvAluno.service;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lê as colunas do cronograma CEUB (atividade, data de início, data de término)
 * e devolve o período. O prazo da tarefa é sempre a data final.
 */
final class CronogramaDatas {

	private static final Pattern TOKEN = Pattern.compile("(\\d{2})/(\\d{2})(?:/(\\d{4}|\\d{2}))?");

	record Periodo(LocalDate inicio, LocalDate fim) {
	}

	private record DataLida(LocalDate data, boolean anoExplicito) {
	}

	private CronogramaDatas() {
	}

	static Periodo interpretar(List<String> colunas) {
		if (colunas == null || colunas.isEmpty()) {
			return null;
		}

		// Colunas de data juntas, para um ano escrito só no término valer também para o início.
		StringBuilder datasDaLinha = new StringBuilder();
		for (int i = 1; i < colunas.size(); i++) {
			if (colunas.get(i) != null && !colunas.get(i).isBlank()) {
				datasDaLinha.append(' ').append(colunas.get(i));
			}
		}
		List<DataLida> datas = new ArrayList<>(lerDatas(datasDaLinha.toString()));
		if (datas.isEmpty()) {
			datas.addAll(lerDatas(colunas.get(0)));
		}
		if (datas.isEmpty()) {
			return null;
		}

		ajustarAnoQuandoPeriodoCruzaAno(datas);
		return new Periodo(datas.get(0).data(), datas.get(datas.size() - 1).data());
	}

	private static List<DataLida> lerDatas(String texto) {
		if (texto == null || texto.isBlank()) {
			return List.of();
		}

		List<TokenBruto> tokens = new ArrayList<>();
		Matcher matcher = TOKEN.matcher(texto);
		while (matcher.find()) {
			int dia = Integer.parseInt(matcher.group(1));
			int mes = Integer.parseInt(matcher.group(2));
			String anoTexto = matcher.group(3);
			Integer ano = anoTexto == null ? null : Integer.parseInt(anoTexto.length() == 2 ? "20" + anoTexto : anoTexto);
			tokens.add(new TokenBruto(dia, mes, ano));
		}
		if (tokens.isEmpty()) {
			return List.of();
		}

		Integer anoDaLinha = null;
		for (int i = tokens.size() - 1; i >= 0; i--) {
			if (tokens.get(i).ano() != null) {
				anoDaLinha = tokens.get(i).ano();
				break;
			}
		}

		List<DataLida> datas = new ArrayList<>();
		for (TokenBruto token : tokens) {
			boolean explicito = token.ano() != null;
			int ano;
			if (explicito) {
				ano = token.ano();
			} else if (anoDaLinha != null) {
				ano = anoDaLinha;
			} else {
				ano = inferirAno(token.dia(), token.mes()).getYear();
			}
			try {
				datas.add(new DataLida(LocalDate.of(ano, token.mes(), token.dia()), explicito));
			} catch (DateTimeException ignored) {
				// dia/mês inválido no texto da célula
			}
		}
		return datas;
	}

	/**
	 * Células como "20/12 a 05/01/27" herdam o ano da data final e ficam invertidas.
	 * Recua um ano nas datas sem ano explícito até a sequência voltar a andar para a frente.
	 */
	private static void ajustarAnoQuandoPeriodoCruzaAno(List<DataLida> datas) {
		boolean haAnoExplicito = datas.stream().anyMatch(DataLida::anoExplicito);
		if (!haAnoExplicito) {
			return;
		}
		for (int i = datas.size() - 2; i >= 0; i--) {
			DataLida atual = datas.get(i);
			LocalDate seguinte = datas.get(i + 1).data();
			if (!atual.anoExplicito() && atual.data().isAfter(seguinte)) {
				datas.set(i, new DataLida(atual.data().minusYears(1), false));
			}
		}
	}

	/** Semestre letivo: datas dd/MM costumam omitir o ano no cronograma CEUB. */
	private static LocalDate inferirAno(int dia, int mes) {
		LocalDate hoje = LocalDate.now();
		LocalDate candidata = LocalDate.of(hoje.getYear(), mes, dia);
		if (candidata.isBefore(hoje.minusMonths(4))) {
			candidata = candidata.plusYears(1);
		} else if (candidata.isAfter(hoje.plusMonths(10))) {
			candidata = candidata.minusYears(1);
		}
		return candidata;
	}

	private record TokenBruto(int dia, int mes, Integer ano) {
	}
}
