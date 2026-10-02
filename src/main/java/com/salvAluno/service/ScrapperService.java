package com.salvAluno.service; 


// Este código será responsável por fazer o scrapping de dados da página da faculdade e extrair os dados 

// Importações necessárias para o funcionamento do serviço de scrapping
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.ElementHandle;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.TimeoutError;
import com.microsoft.playwright.options.WaitUntilState;
import com.salvAluno.domain.Task;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

// Outras importações necessárias para o Java 
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

// A tag @Service indica que esta classe é um serviço do Spring, permitindo que seja injetada em outras partes da aplicação.
// O Spring vai instanciar essa classe como um "Bean" que é um objeto especial que é mantido e gerido pelo próprio Spring
@Service 
public class ScrapperService { 

    // Versão do scrapper para fins de controle de versão 
    private static final String SCRAPER_VERSION = "3.2.1";
    private static final Pattern SALA_ONLINE_LINK = Pattern.compile("Sala Online \\(\\d{4}\\)");

    @Autowired
    private TaskStore taskStore;

    // Injeta a URL do portal da faculdade, que será usada para fazer o scrapping
    @Value("${portal.url:https://ea.uniceub.br/Sistema/Acesso/Login}")
    private String portalUrl;

    @Value("${portal.salaOnline:Sala Online (2025)}")
    private String salaOnlineLabel;

    @Value("${portal.loginTimeoutMs:90000}")
    private long loginTimeoutMs;

    private final AtomicBoolean syncEmAndamento = new AtomicBoolean(false);
    private volatile String syncMensagem = "";
    private volatile String ultimaSyncResultado = "";

    public boolean isSyncEmAndamento() {
        return syncEmAndamento.get();
    }

    public String getSyncMensagem() {
        return syncMensagem;
    }

    public String getUltimaSyncResultado() {
        return ultimaSyncResultado;
    }

    private void atualizarSyncMensagem(String mensagem) {
        syncMensagem = mensagem;
    }

    // Método principal para o Scrapping. 
    public void scrapData(String ra, String senha) {
        
        // Se a sincronização já estiver em andamento, ignora a nova solicitação 
        if (!syncEmAndamento.compareAndSet(false, true)) {
            System.out.println("[Playwright] Sincronização já em andamento; ignorando nova solicitação.");
            return;
        }

        try {
            System.out.println("Iniciando o scrapping de dados... [scraper=" + SCRAPER_VERSION + "]");
            ultimaSyncResultado = "";
            atualizarSyncMensagem("Iniciando automação no portal…");

            if (ra == null || senha == null || ra.isBlank() || senha.isBlank()) {
                System.err.println("[Playwright] RA ou senha do aluno não informados.");
                ultimaSyncResultado = "RA ou senha do portal não disponíveis. Saia e entre de novo no SalvAluno.";
                atualizarSyncMensagem(ultimaSyncResultado);
                return;
            }

            if (executarScraping(ra.trim(), senha)) {
                ultimaSyncResultado = "sucesso";
            }
        } finally {
            syncEmAndamento.set(false);
            syncMensagem = "";
        }
    }

    private boolean executarScraping(String ra, String senha) {
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
                realizarLogin(page, ra, senha);

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

                // Variável para contar o índice da disciplina atual 
                int indice = 0;
                // Loop para percorrer todas disciplinas encontradas
                for (DisciplinaPortal disciplina : disciplinas) {
                    indice++;
                    atualizarSyncMensagem("Cronograma " + indice + " de " + quantidadeMaterias + ": " + disciplina.nome());
                    System.out.println("[Playwright] Processando curso: " + disciplina.nome());
                    
                    // Navega para a página da disciplina 
                    salaOnline.navigate(disciplina.url());
                    // Espera o carregamento da página  
                    salaOnline.waitForLoadState();
                    // Espera 800ms para garantir que a página foi carregada 
                    salaOnline.waitForTimeout(800);

                    // Pega o nome da disciplina 
                    String nomeMateria = resolverNomeDisciplina(salaOnline, disciplina);

                    // Pega o link do cronograma da disciplina  
                    ElementHandle linkCronograma = salaOnline.querySelector("h3.overviewCard-title a[title='Cronograma']");
                    
                    if (linkCronograma == null) {
                        if (!disciplina.administrativa()) {
                            System.out.println("[Playwright] Nenhum cronograma encontrado para a matéria: " + nomeMateria);
                        }
                        continue;
                    }

                    // Clica no link do cronograma 
                    linkCronograma.click();
                    // Espera carregamento da página do sala online 
                    salaOnline.waitForLoadState();

                    // Pega cada linha do cronograma 
                    List<ElementHandle> linhasCronograma = salaOnline.querySelectorAll("ul.content_cronogramadv");

                    // Loop para percorrer cada linha encontrada do cronograma  
                    for (ElementHandle linha : linhasCronograma) {
                        // Pega cada coluna da linha 
                        List<ElementHandle> colunas = linha.querySelectorAll("li");
                        
                        // Verifica se o número de colunas é maior ou igual a 3 
                        // Colunas: atividade, data de início e data de término
                        if (colunas.size() >= 3) {

                            List<String> textos = new ArrayList<>();
                            for (ElementHandle coluna : colunas) {
                                textos.add(normalizarTexto(coluna.innerText()));
                            }

                            String tituloAtividade = textos.get(0);
                            if (tituloAtividade.isBlank()) {
                                continue;
                            }

                            CronogramaDatas.Periodo periodo = CronogramaDatas.interpretar(textos);
                            if (periodo != null) {
                                LocalDateTime inicio = periodo.inicio().atStartOfDay();
                                LocalDateTime prazoFinal = periodo.fim().atTime(23, 59);
                                String urlCronograma = salaOnline.url();
                                Task task = new Task(tituloAtividade, nomeMateria, inicio, prazoFinal, urlCronograma, ra);
                                tarefasEncontradas.add(task);

                                System.out.println(" [Playwright] Atividade Encontrada: " + tituloAtividade
                                        + " | Início: " + periodo.inicio() + " | Término: " + periodo.fim());
                            }
                        }
                    }

                    salaOnline.navigate(paginaCursosUrl);
                    salaOnline.waitForLoadState();
                }

                atualizarSyncMensagem("Salvando tarefas no aplicativo…");
                taskStore.substituirDoAluno(ra, tarefasEncontradas);
                if (tarefasEncontradas.isEmpty()) {
                    System.out.println("[Playwright] Nenhuma tarefa encontrada");
                } else {
                    System.out.println("[Playwright] Tarefas armazenadas: " + tarefasEncontradas.size());
                }
                atualizarSyncMensagem("Sincronização concluída.");
                return true;

            } catch (Exception e) {
                ultimaSyncResultado = mensagemErroSync(e);
                System.err.println("[Playwright] Erro durante a execução do scraping: " + e.getMessage());
                e.printStackTrace();
                return false;

            } finally {
                browser.close();
                System.out.println("[Playwright] Varredura finalizada.");
            }

        } catch (Exception e) {
            ultimaSyncResultado = mensagemErroSync(e);
            System.err.println("[Playwright] Erro ao iniciar o Playwright: " + e.getMessage());
            return false;
        }
    }

    private void realizarLogin(Page page, String ra, String senha) {
        page.navigate(portalUrl, new Page.NavigateOptions()
                .setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
                .setTimeout(60_000));
        page.locator("#coAcesso").waitFor(new Locator.WaitForOptions().setTimeout(30_000));
        page.locator("#coAcesso").fill(ra);
        page.locator("#coSenha").fill(senha);
        page.locator("#btn-login").click();
        page.waitForURL(
                url -> !url.contains("/Sistema/Acesso/Login"),
                new Page.WaitForURLOptions().setTimeout(loginTimeoutMs)
        );
        page.waitForLoadState();
    }

    private String mensagemErroSync(Exception e) {
        if (e instanceof TimeoutError) {
            return "Não foi possível entrar no Espaço Aluno a tempo. Confira RA e senha do portal (saia e entre de novo) ou tente mais tarde.";
        }
        String msg = e.getMessage();
        if (msg != null && msg.toLowerCase().contains("timeout")) {
            return "Não foi possível entrar no Espaço Aluno a tempo. Confira RA e senha do portal (saia e entre de novo) ou tente mais tarde.";
        }
        return "Erro na sincronização. Tente novamente em instantes.";
    }

    private Locator linkSalaOnline(Page espacoAluno) {
        Locator porAno = espacoAluno.getByText(SALA_ONLINE_LINK);
        if (porAno.count() > 0) {
            return porAno.first();
        }
        return espacoAluno.getByText(salaOnlineLabel, new Page.GetByTextOptions().setExact(true));
    }

    private Page abrirSalaOnline(Page espacoAluno) {
        Locator link = linkSalaOnline(espacoAluno);
        try {
            Page popup = espacoAluno.context().waitForPage(
                    new com.microsoft.playwright.BrowserContext.WaitForPageOptions().setTimeout(15_000),
                    link::click
            );
            popup.waitForLoadState();
            System.out.println("[Playwright] Sala Online aberta em nova aba: " + popup.url());
            return popup;
        } catch (Exception e) {
            System.out.println("[Playwright] Popup não detectado, tentando mesma aba: " + e.getMessage());
            link.click();
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

    private record DisciplinaPortal(String url, String nome, boolean administrativa) {}
}

