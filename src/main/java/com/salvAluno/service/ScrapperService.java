package com.salvAluno.service; 


// Este código será responsável por fazer o scrapping de dados da página da faculdade e extrair os dados 

// Importações necessárias para o funcionamento do serviço de scrapping
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.ElementHandle;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.salvAluno.domain.Task;
import com.salvAluno.repository.TaskRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

// Outras importações necessárias para o Java 
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// A tag @Service indica que esta classe é um serviço do Spring, permitindo que seja injetada em outras partes da aplicação.
// O Spring vai instanciar essa classe como um "Bean" que é um objeto especial que é mantido e gerido pelo próprio Spring
@Service 
public class ScrapperService { 

    // Versão do scrapper para fins de controle de versão 
    private static final String SCRAPER_VERSION = "3.0.0";

    // Padrões de data para extrair a data do cronograma da Sala Online 
    private static final Pattern DATA_COMPLETA_4 = Pattern.compile("(\\d{2}/\\d{2}/\\d{4})");
    private static final Pattern DATA_COMPLETA_2 = Pattern.compile("(\\d{2}/\\d{2}/\\d{2})");
    private static final Pattern DATA_DIA_MES = Pattern.compile("(\\d{2}/\\d{2})(?!\\d|/)");

    // Injeta o repositório para podermos guardar as tarefas diretamente no banco de dados 
    @Autowired 
    private TaskRepository taskRepository; 

    // Injeta a URL do portal da faculdade, que será usada para fazer o scrapping 
    @Value("${portal.url:https://ea.uniceub.br/Sistema/Acesso/Login}")
    private String portalUrl; 

    // Injeta o RA do usuário que será usado no login do portal da faculdade 
    @Value("${portal.RA:seu_RA}") 
    private String RA; 

    // Injeta a senha do usuário que será usada no login do portal da faculdade 
    @Value("${portal.password:sua_senha}")
    private String password;

    @Value("${portal.salaOnline:Sala Online (2025)}")
    private String salaOnlineLabel;

    private final AtomicBoolean syncEmAndamento = new AtomicBoolean(false);
    private volatile String syncMensagem = "";

    public boolean isSyncEmAndamento() {
        return syncEmAndamento.get();
    }

    public String getSyncMensagem() {
        return syncMensagem;
    }

    private void atualizarSyncMensagem(String mensagem) {
        syncMensagem = mensagem;
    }

    // Método principal para o Scrapping 
    public void scrapData(){ 
        if (!syncEmAndamento.compareAndSet(false, true)) {
            System.out.println("[Playwright] Sincronização já em andamento; ignorando nova solicitação.");
            return;
        }

        try {
            System.out.println("Iniciando o scrapping de dados... [scraper=" + SCRAPER_VERSION + "]");
            atualizarSyncMensagem("Iniciando automação no portal…");

            RA = RA.trim();
            password = password.trim();

            if (RA.isEmpty() || password.isEmpty() || "seu_RA".equals(RA) || "sua_senha".equals(password)) {
                System.err.println("[Playwright] Credenciais do portal não configuradas. "
                        + "Preencha portal.RA e portal.password em src/main/resources/application.properties "
                        + "e gere o JAR novamente (mvn clean package), ou coloque application.properties na pasta do JAR.");
                atualizarSyncMensagem("Credenciais do portal não configuradas.");
                return;
            }

            executarScraping();
        } finally {
            syncEmAndamento.set(false);
            syncMensagem = "";
        }
    }

    private void executarScraping() {
        try (Playwright playwright = Playwright.create()) { 

            // Cria instância Chromium do Playwright   
            Browser browser = playwright.chromium().launch(
                // Lança em modo headless (sem interface gráfica) para que o scrapping seja feito em segundo plano 
                new BrowserType.LaunchOptions().setHeadless(true)
            );
            
            // Cria uma nova aba no navegador para fazer o scrapping 
            Page page = browser.newPage(); 

            try { 

                atualizarSyncMensagem("Acessando o Espaço Aluno…");
                System.out.println("[Playwright] Acessando a página de login: " + portalUrl);
                page.navigate(portalUrl); 

                page.fill("#coAcesso", RA); 
                page.fill("#coSenha", password); 

                page.click("#btn-login");
                page.waitForURL(
                        url -> !url.contains("/Sistema/Acesso/Login"),
                        new Page.WaitForURLOptions().setTimeout(30_000)
                );
                page.waitForLoadState();

                System.out.println("[Playwright] Login realizado. URL: " + page.url());
                atualizarSyncMensagem("Abrindo a Sala Online…");

                Page salaOnline = abrirSalaOnline(page);
                salaOnline.waitForLoadState();
                salaOnline.locator("div.card-course[data-course-id]").first().waitFor(
                        new Locator.WaitForOptions().setTimeout(60_000)
                );

                // Lista de tarefas encontradas 
                List<Task> tarefasEncontradas = new ArrayList<>();
                String paginaCursosUrl = salaOnline.url();
                
                // Lista de disciplinas encontradas 
                List<DisciplinaPortal> disciplinas = listarDisciplinas(salaOnline);
                int quantidadeMaterias = disciplinas.size();

                System.out.println("[Playwright] Matérias encontradas: " + quantidadeMaterias);
                atualizarSyncMensagem("Encontradas " + quantidadeMaterias + " disciplinas. Lendo cronogramas…");

                int indice = 0;
                for (DisciplinaPortal disciplina : disciplinas) {
                    indice++;
                    atualizarSyncMensagem("Cronograma " + indice + " de " + quantidadeMaterias + ": " + disciplina.nome());
                    System.out.println("[Playwright] Processando curso: " + disciplina.nome());
                    salaOnline.navigate(disciplina.url());
                    salaOnline.waitForLoadState();
                    salaOnline.waitForTimeout(800);

                    String nomeMateria = resolverNomeDisciplina(salaOnline, disciplina);

                    ElementHandle linkCronograma = salaOnline.querySelector("h3.overviewCard-title a[title='Cronograma']");
                    if (linkCronograma == null) {
                        if (!disciplina.administrativa()) {
                            System.out.println("[Playwright] Nenhum cronograma encontrado para a matéria: " + nomeMateria);
                        }
                        continue;
                    }

                    linkCronograma.click();
                    salaOnline.waitForLoadState();

                    List<ElementHandle> linhasCronograma = salaOnline.querySelectorAll("ul.content_cronogramadv");

                    for (ElementHandle linha : linhasCronograma) {
                        List<ElementHandle> colunas = linha.querySelectorAll("li");

                        if (colunas.size() >= 3) {
                            String tituloAtividade = colunas.get(0).innerText().trim();
                            if (tituloAtividade.isBlank()) {
                                continue;
                            }
                            String dataPrazoStr = extrairTextoData(colunas);
                            LocalDateTime prazoFinal = converterDataPrazo(dataPrazoStr);

                            if (prazoFinal != null) {
                                String urlCronograma = salaOnline.url();
                                Task task = new Task(tituloAtividade, nomeMateria, prazoFinal, urlCronograma);
                                tarefasEncontradas.add(task);

                                System.out.println(" [Playwright] Atividade Encontrada: " + tituloAtividade + " | Prazo: " + dataPrazoStr);
                            }
                        }
                    }

                    salaOnline.navigate(paginaCursosUrl);
                    salaOnline.waitForLoadState();
                }

                atualizarSyncMensagem("Salvando tarefas no aplicativo…");
                if (tarefasEncontradas.isEmpty()){ 
                    System.out.println("[Playwright] Nenhuma tarefa encontrada"); 
                } else { 
                    taskRepository.saveAll(tarefasEncontradas);

                    System.out.println("[Playwright] Tarefas armazenadas: " + tarefasEncontradas.size());
                }
                atualizarSyncMensagem("Sincronização concluída.");


            }catch (Exception e) { 
                System.err.println("[Playwright] Erro durante a execução do scraping: " + e.getMessage());
                e.printStackTrace(); 

            }finally{ 
                browser.close(); 
                System.out.println("[Playwright] Varredura finalizada.");
            }

        } catch (Exception e) { 
            System.err.println("[Playwright] Erro ao iniciar o Playwright: " + e.getMessage()); 
        }
    } 


    private Page abrirSalaOnline(Page espacoAluno) {
        var salaOnlineTexto = new Page.GetByTextOptions().setExact(true);
        try {
            Page popup = espacoAluno.context().waitForPage(
                    new com.microsoft.playwright.BrowserContext.WaitForPageOptions().setTimeout(15_000),
                    () -> espacoAluno.getByText(salaOnlineLabel, salaOnlineTexto).click()
            );
            popup.waitForLoadState();
            System.out.println("[Playwright] Sala Online aberta em nova aba: " + popup.url());
            return popup;
        } catch (Exception e) {
            System.out.println("[Playwright] Popup não detectado, tentando mesma aba: " + e.getMessage());
            espacoAluno.getByText(salaOnlineLabel, salaOnlineTexto).click();
            espacoAluno.waitForLoadState();
            System.out.println("[Playwright] Sala Online na mesma aba: " + espacoAluno.url());
            return espacoAluno;
        }
    }

    private List<DisciplinaPortal> listarDisciplinas(Page salaOnline) {
        Set<String> urlsVistas = new LinkedHashSet<>();
        List<DisciplinaPortal> disciplinas = new ArrayList<>();
        Locator cards = salaOnline.locator("div.card-course[data-course-id]");
        int total = cards.count();
        for (int i = 0; i < total; i++) {
            // Pega o card da disciplina 
            Locator card = cards.nth(i);
            // Pega o link da disciplina 
            String href = card.locator("a[href*='course/view.php']").first().getAttribute("href");
            
            if (href == null || href.isBlank() || !urlsVistas.add(href)) {
                continue;
            }
            // Cronograma CEUB (overviewCard) fica nas salas salaonline.ceub.br
            if (!href.contains("salaonline.ceub.br")) {
                continue;
            }

            String nome = normalizarTexto(card.locator(".infos-course .course-name h4").first().textContent());
            if (nome.isBlank()) {
                nome = normalizarTexto(card.locator(".course-name a").first().textContent());
            }
            if (nome.isBlank()) {
                nome = "Curso " + card.getAttribute("data-course-id");
            }
            boolean administrativa = nome.toLowerCase().contains("coordenação")
                    || nome.toLowerCase().contains("coordenacao");
            disciplinas.add(new DisciplinaPortal(href, nome, administrativa));
        }
        return disciplinas;
    }

    private String extrairTextoData(List<ElementHandle> colunas) {
        String melhorTexto = "";
        int melhorPontuacao = 0;
        for (ElementHandle coluna : colunas) {
            String texto = normalizarTexto(coluna.innerText());
            int pontuacao = pontuacaoTextoData(texto);
            if (pontuacao > melhorPontuacao) {
                melhorPontuacao = pontuacao;
                melhorTexto = texto;
            }
        }
        return melhorTexto;
    }

    private int pontuacaoTextoData(String texto) {
        if (texto.isBlank()) {
            return 0;
        }
        if (DATA_COMPLETA_4.matcher(texto).find()) {
            return 4;
        }
        if (DATA_COMPLETA_2.matcher(texto).find()) {
            return 3;
        }
        if (DATA_DIA_MES.matcher(texto).find()) {
            return 2;
        }
        return 0;
    }

    private boolean contemPadraoData(String texto) {
        if (texto == null || texto.isBlank()) {
            return false;
        }
        return DATA_COMPLETA_4.matcher(texto).find()
                || DATA_COMPLETA_2.matcher(texto).find()
                || DATA_DIA_MES.matcher(texto).find();
    }

    private LocalDateTime converterDataPrazo(String dataStr) {
        dataStr = normalizarTexto(dataStr);
        if (dataStr.isBlank()) {
            return null;
        }

        try {
            LocalDate data = parseDataPortal(dataStr);
            return data.atTime(23, 59);
        } catch (RuntimeException e) {
            if (contemPadraoData(dataStr)) {
                System.err.println("[Playwright] Erro ao converter a data: " + dataStr);
            }
            return null;
        }
    }

    /** Usa a última data encontrada no texto (ex.: "06/08 a 09/08/26" → término). */
    private LocalDate parseDataPortal(String dataStr) {
        dataStr = normalizarTexto(dataStr);
        LocalDate ultima = null;

        Matcher comAno4 = DATA_COMPLETA_4.matcher(dataStr);
        while (comAno4.find()) {
            ultima = LocalDate.parse(comAno4.group(1), DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        }
        if (ultima != null) {
            return ultima;
        }

        Matcher comAno2 = DATA_COMPLETA_2.matcher(dataStr);
        while (comAno2.find()) {
            ultima = LocalDate.parse(comAno2.group(1), DateTimeFormatter.ofPattern("dd/MM/yy"));
        }
        if (ultima != null) {
            return ultima;
        }

        Matcher diaMes = DATA_DIA_MES.matcher(dataStr);
        while (diaMes.find()) {
            String[] partes = diaMes.group(1).split("/");
            int dia = Integer.parseInt(partes[0]);
            int mes = Integer.parseInt(partes[1]);
            ultima = inferirAno(dia, mes);
        }
        if (ultima != null) {
            return ultima;
        }

        throw new DateTimeParseException("Formato de data não reconhecido", dataStr, 0);
    }

    private String resolverNomeDisciplina(Page salaOnline, DisciplinaPortal disciplina) {
        String[] seletoresTitulo = {
                ".page-header-headings h1",
                "#page-header-headings h1",
                ".page-context-header h1",
                "div[role='main'] h1"
        };
        for (String seletor : seletoresTitulo) {
            Locator titulo = salaOnline.locator(seletor).first();
            if (titulo.count() > 0) {
                String texto = normalizarTexto(titulo.innerText());
                if (!texto.isBlank() && !texto.equalsIgnoreCase("cronograma")) {
                    return texto;
                }
            }
        }

        String tituloPagina = salaOnline.title();
        if (tituloPagina != null && tituloPagina.contains("|")) {
            String curto = normalizarTexto(tituloPagina.split("\\|")[0]);
            if (!curto.isBlank()) {
                return curto;
            }
        }

        if (!disciplina.nome().isBlank()) {
            return disciplina.nome();
        }
        return "Curso sem nome";
    }

    private String normalizarTexto(String texto) {
        if (texto == null) {
            return "";
        }
        return texto.replace('\u00a0', ' ')
                .replaceAll("\\s+", " ")
                .trim();
    }

    /** Semestre letivo: datas dd/MM costumam omitir o ano no cronograma CEUB. */
    private LocalDate inferirAno(int dia, int mes) {
        LocalDate hoje = LocalDate.now();
        LocalDate candidata = LocalDate.of(hoje.getYear(), mes, dia);
        if (candidata.isBefore(hoje.minusMonths(4))) {
            candidata = candidata.plusYears(1);
        } else if (candidata.isAfter(hoje.plusMonths(10))) {
            candidata = candidata.minusYears(1);
        }
        return candidata;
    }

    private record DisciplinaPortal(String url, String nome, boolean administrativa) {}
}

