package com.salvAluno.service;

import java.text.Normalizer;
import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Atividades da sala, misturadas com materiais de apoio. O prazo vem do
 * bloco "Aberto" / "Fechado" da seção em que a atividade está.
 */
final class QuestionariosSala {

	record Evento(String tipo, String origem, String texto, String href) {
	}

	record Extraido(String titulo, String href, LocalDateTime inicio, LocalDateTime fim) {
	}

	private record Periodo(LocalDateTime inicio, LocalDateTime fim) {
	}

	private static final Pattern DATA_EXTENSO = Pattern.compile(
			"(?i)\\b(aberto|fechado)\\s*:\\s*"
					+ "(?:\\p{L}+(?:-\\p{L}+)?,\\s*)?"
					+ "(\\d{1,2})\\s+(?:de\\s+)?"
					+ "(\\p{L}+)\\.?\\s+(?:de\\s+)?"
					+ "(\\d{4})"
					+ "(?:(?:\\s*,\\s*|\\s+)(\\d{1,2})\\s*:\\s*(\\d{2}))?"
	);

	private static final Pattern DATA_NUMERICA = Pattern.compile(
			"(?i)\\b(aberto|fechado)\\s*:\\s*"
					+ "(?:\\p{L}+(?:-\\p{L}+)?,\\s*)?"
					+ "(\\d{1,2})/(\\d{1,2})/(\\d{2,4})"
					+ "(?:(?:\\s*,\\s*|\\s+)(\\d{1,2})\\s*:\\s*(\\d{2}))?"
	);

	private static final Map<String, Integer> MESES = meses();
	private static final Set<String> APOIO = Set.of(
			"resource", "url", "folder", "page", "book", "label", "imscp");

	private QuestionariosSala() {
	}

	static List<Extraido> interpretar(List<Evento> eventos) {
		if (eventos == null || eventos.isEmpty()) {
			return List.of();
		}

		LocalDateTime inicio = null;
		LocalDateTime fim = null;
		Map<String, Extraido> unicos = new LinkedHashMap<>();

		for (Evento evento : eventos) {
			if (evento == null || evento.tipo() == null) {
				continue;
			}
			if ("periodo".equals(evento.tipo())) {
				Periodo periodo = lerPeriodo(evento.texto());
				if ("secao".equals(evento.origem())) {
					inicio = periodo == null ? null : periodo.inicio();
					fim = periodo == null ? null : periodo.fim();
				} else if (periodo != null) {
					if (periodo.inicio() != null) {
						inicio = periodo.inicio();
					}
					if (periodo.fim() != null) {
						fim = periodo.fim();
					}
				}
				continue;
			}
			if (!"atividade".equals(evento.tipo()) && !"questionario".equals(evento.tipo())) {
				continue;
			}
			if (evento.origem() != null && APOIO.contains(evento.origem())) {
				continue;
			}

			String titulo = limparTitulo(evento.texto());
			if (titulo.isBlank()) {
				continue;
			}
			String href = evento.href() == null ? "" : evento.href().trim();
			String chave = href.isBlank() ? titulo + "|" + inicio + "|" + fim : href;
			unicos.putIfAbsent(chave, new Extraido(titulo, href, inicio, fim));
		}
		return List.copyOf(unicos.values());
	}

	private static Periodo lerPeriodo(String texto) {
		if (texto == null || texto.isBlank()) {
			return null;
		}
		LocalDateTime[] periodo = new LocalDateTime[2];
		coletar(DATA_EXTENSO, texto, true, periodo);
		coletar(DATA_NUMERICA, texto, false, periodo);
		if (periodo[0] == null && periodo[1] == null) {
			return null;
		}
		return new Periodo(periodo[0], periodo[1]);
	}

	private static void coletar(Pattern pattern, String texto, boolean mesPorExtenso, LocalDateTime[] periodo) {
		Matcher matcher = pattern.matcher(texto);
		while (matcher.find()) {
			boolean fechado = "fechado".equalsIgnoreCase(matcher.group(1));
			LocalDateTime data = mesPorExtenso ? dataExtenso(matcher, fechado) : dataNumerica(matcher, fechado);
			if (data == null) {
				continue;
			}
			int indice = fechado ? 1 : 0;
			if (periodo[indice] == null) {
				periodo[indice] = data;
			}
		}
	}

	private static LocalDateTime dataExtenso(Matcher matcher, boolean fechado) {
		Integer mes = MESES.get(semAcento(matcher.group(3)));
		if (mes == null) {
			return null;
		}
		return montar(matcher.group(2), mes, matcher.group(4), matcher.group(5), matcher.group(6), fechado);
	}

	private static LocalDateTime dataNumerica(Matcher matcher, boolean fechado) {
		return montar(matcher.group(2), Integer.parseInt(matcher.group(3)), matcher.group(4),
				matcher.group(5), matcher.group(6), fechado);
	}

	private static LocalDateTime montar(String diaTexto, int mes, String anoTexto, String horaTexto, String minutoTexto,
			boolean fechado) {
		int ano = Integer.parseInt(anoTexto);
		if (ano < 100) {
			ano += 2000;
		}
		int hora;
		int minuto;
		if (horaTexto == null) {
			hora = fechado ? 23 : 0;
			minuto = fechado ? 59 : 0;
		} else {
			hora = Integer.parseInt(horaTexto);
			minuto = Integer.parseInt(minutoTexto);
		}
		try {
			return LocalDateTime.of(ano, mes, Integer.parseInt(diaTexto), hora, minuto);
		} catch (DateTimeException ex) {
			return null;
		}
	}

	static String limparTitulo(String titulo) {
		if (titulo == null) {
			return "";
		}
		return titulo.replace('\u00a0', ' ')
				.replaceAll("\\s+", " ")
				.replaceAll("(?i)\\s+(question[aá]rio|tarefa|f[oó]rum|li[cç][aã]o)\\s*$", "")
				.trim();
	}

	private static String semAcento(String texto) {
		String normalizado = Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
		return normalizado.toLowerCase(Locale.ROOT).replace(".", "");
	}

	private static Map<String, Integer> meses() {
		Map<String, Integer> meses = new LinkedHashMap<>();
		meses.put("janeiro", 1);
		meses.put("jan", 1);
		meses.put("fevereiro", 2);
		meses.put("fev", 2);
		meses.put("marco", 3);
		meses.put("mar", 3);
		meses.put("abril", 4);
		meses.put("abr", 4);
		meses.put("maio", 5);
		meses.put("mai", 5);
		meses.put("junho", 6);
		meses.put("jun", 6);
		meses.put("julho", 7);
		meses.put("jul", 7);
		meses.put("agosto", 8);
		meses.put("ago", 8);
		meses.put("setembro", 9);
		meses.put("set", 9);
		meses.put("outubro", 10);
		meses.put("out", 10);
		meses.put("novembro", 11);
		meses.put("nov", 11);
		meses.put("dezembro", 12);
		meses.put("dez", 12);
		return Map.copyOf(meses);
	}
}
