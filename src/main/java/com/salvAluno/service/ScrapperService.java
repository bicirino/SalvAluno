package com.salvAluno.service; 


// Este código será responsável por fazer o scrapping de dados da página da faculdade e extrair os dados 

// Importações necessárias para o funcionamento do serviço de scrapping
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
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
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

// A tag @Service indica que esta classe é um serviço do Spring, permitindo que seja injetada em outras partes da aplicação.
// O Spring vai instanciar essa classe como um "Bean" que é um objeto especial que é mantido e gerido pelo próprio Spring
@Service 
public class ScrapperService { 

    // Versão do scrapper para fins de controle de versão 
    private static final String SCRAPER_VERSION = "3.5.0";

    /** Percorre a sala inteira e separa atividade de material de apoio. */
    private static final String SCRIPT_ATIVIDADES = """
            () => {
              const limpar = (el) => {
                if (!el) return '';
                const clone = el.cloneNode(true);
                clone.querySelectorAll('.accesshide, .sr-only').forEach((n) => n.remove());
                return (clone.innerText || '').replace(/\\s+/g, ' ').trim();
              };
              const APOIO = new Set(['resource', 'url', 'folder', 'page', 'book', 'label', 'imscp']);
              const raizAtividade = (el) => !(el.parentElement && el.parentElement.closest('li.activity, div.activity, .activity-item'));
              const moduloDe = (el, href) => {
                const classe = typeof el.className === 'string' ? el.className : '';
                const pelaClasse = classe.match(/modtype_([a-z0-9]+)/);
                if (pelaClasse) return pelaClasse[1];
                const tipo = el.getAttribute('data-type') || el.getAttribute('data-modname');
                if (tipo) return tipo;
                const pelaUrl = (href || '').match(/\\/mod\\/([a-z0-9]+)\\//);
                return pelaUrl ? pelaUrl[1] : '';
              };
              const tituloDe = (el) => {
                const nome = el.getAttribute('data-activityname');
                if (nome && nome.trim()) return nome.trim();
                const rotulo = el.querySelector('.instancename, .activityname, .courseindex-link') || el.querySelector('a[href*="/mod/"]');
                return limpar(rotulo);
              };

              const ROTULO_PRAZO = '(aberto|aberta|abre|fechado|fechada|fecha|encerra|encerrado|data de entrega|vence em)';
              const PRAZO_EXTENSO = new RegExp(
                ROTULO_PRAZO + '\\\\s*:\\\\s*(?:\\\\p{L}+(?:-\\\\p{L}+)?,\\\\s*)?\\\\d{1,2}\\\\s+(?:de\\\\s+)?\\\\p{L}+\\\\.?(?:\\\\s+de)?\\\\s+\\\\d{4}(?:\\\\s*,\\\\s*\\\\d{1,2}\\\\s*:\\\\s*\\\\d{2})?',
                'giu'
              );
              const PRAZO_NUMERICO = new RegExp(
                ROTULO_PRAZO + '\\\\s*:\\\\s*\\\\d{1,2}/\\\\d{1,2}/\\\\d{2,4}(?:\\\\s*,?\\\\s*\\\\d{1,2}\\\\s*:\\\\s*\\\\d{2})?',
                'gi'
              );
              const normalizarRotulo = (trecho) => trecho
                .replace(/^(aberta|abre)\\b/i, 'Aberto')
                .replace(/^(fechada|fecha|encerra|encerrado|data de entrega|vence em)\\b/i, 'Fechado');
              const extrairPrazo = (texto) => {
                if (!texto) return '';
                const achados = [];
                for (const regra of [PRAZO_EXTENSO, PRAZO_NUMERICO]) {
                  regra.lastIndex = 0;
                  const encontrados = texto.match(regra);
                  if (encontrados) achados.push(...encontrados);
                }
                return [...new Set(achados.map(normalizarRotulo))].join('\\n');
              };
              const textoSemAtividades = (el) => {
                if (!el) return '';
                const clone = el.cloneNode(true);
                clone.querySelectorAll('li.activity, div.activity, .activity-item, .courseindex, nav').forEach((n) => n.remove());
                return limpar(clone);
              };

              const raiz = document.querySelector('#region-main')
                || document.querySelector('[role="main"]')
                || document.body;
              const eventos = [];
              const atividades = [...raiz.querySelectorAll('li.activity, div.activity, .activity-item')]
                .filter((el) => raizAtividade(el) && !el.closest('.courseindex, nav'));
              const blocosDePrazo = [...raiz.querySelectorAll('p, div, li, span, h3, h4')].filter((el) => {
                if (el.closest('li.activity, div.activity, .activity-item, .courseindex, nav')) return false;
                const texto = limpar(el);
                if (!extrairPrazo(texto) || texto.length > 700) return false;
                return ![...el.children].some((filho) => extrairPrazo(limpar(filho)));
              });
              const ordenados = [...atividades, ...blocosDePrazo].sort((a, b) => {
                if (a === b) return 0;
                return a.compareDocumentPosition(b) & Node.DOCUMENT_POSITION_FOLLOWING ? -1 : 1;
              });

              let secaoAtual = null;
              for (const no of ordenados) {
                const secao = no.closest('li.section, div.course-section');
                if (secao !== secaoAtual) {
                  secaoAtual = secao;
                  eventos.push({ tipo: 'periodo', origem: 'secao', texto: extrairPrazo(textoSemAtividades(secao)), href: '' });
                }
                if (blocosDePrazo.includes(no)) {
                  eventos.push({ tipo: 'periodo', origem: 'bloco', texto: extrairPrazo(limpar(no)), href: '' });
                  continue;
                }
                if (no.matches('.modtype_label')) {
                  const prazo = extrairPrazo(limpar(no));
                  if (prazo) eventos.push({ tipo: 'periodo', origem: 'bloco', texto: prazo, href: '' });
                  continue;
                }
                const link = no.querySelector('a[href*="/mod/"]');
                const href = link ? link.href : '';
                const modulo = moduloDe(no, href);
                if (!modulo || APOIO.has(modulo)) continue;
                const titulo = tituloDe(no);
                if (!titulo) continue;
                const prazo = extrairPrazo(limpar(no.querySelector('.activity-dates, .activity-information, .availabilityinfo')))
                  || extrairPrazo(limpar(no));
                if (prazo) eventos.push({ tipo: 'periodo', origem: 'bloco', texto: prazo, href: '' });
                eventos.push({ tipo: 'atividade', origem: modulo, texto: titulo, href });
              }

              const jaVistas = new Set(eventos.filter((e) => e.tipo === 'atividade' && e.href).map((e) => e.href));
              eventos.push({ tipo: 'periodo', origem: 'secao', texto: '', href: '' });
              document.querySelectorAll('.courseindex a[href*="/mod/"], #course-index a[href*="/mod/"]').forEach((link) => {
                if (jaVistas.has(link.href)) return;
                const item = link.closest('li, .courseindex-item') || link;
                const modulo = moduloDe(item, link.href);
                if (!modulo || APOIO.has(modulo)) return;
                const titulo = limpar(link);
                if (!titulo) return;
                jaVistas.add(link.href);
                eventos.push({ tipo: 'atividade', origem: modulo, texto: titulo, href: link.href });
              });
              return eventos;
            }
            """;
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
                revelarCardsDaSala(salaOnline);

                // Lista de tarefas encontradas 
                List<Task> tarefasEncontradas = new ArrayList<>();
                String paginaCursosUrl = salaOnline.url();
                
                // Lista de disciplinas encontradas 
                List<DisciplinaPortal> disciplinas = listarDisciplinas(salaOnline);
                int quantidadeMaterias = disciplinas.size();

                System.out.println("[Playwright] Matérias encontradas: " + quantidadeMaterias);
                atualizarSyncMensagem("Encontradas " + quantidadeMaterias + " disciplinas. Lendo atividades…");

                // Variável para contar o índice da disciplina atual 
                int indice = 0;
                // Loop para percorrer todas disciplinas encontradas
                for (DisciplinaPortal disciplina : disciplinas) {
                    indice++;
                    atualizarSyncMensagem("Disciplina " + indice + " de " + quantidadeMaterias + ": " + disciplina.nome());
                    System.out.println("[Playwright] Processando curso: " + disciplina.nome());
                    
                    // Navega para a página da disciplina 
                    salaOnline.navigate(disciplina.url());
                    // Espera o carregamento da página  
                    salaOnline.waitForLoadState();
                    // Espera 800ms para garantir que a página foi carregada 
                    salaOnline.waitForTimeout(800);
                    aguardarConteudoDaDisciplina(salaOnline);

                    // Pega o nome da disciplina 
                    String nomeMateria = resolverNomeDisciplina(salaOnline, disciplina);

                    // Extrai os questionários da disciplina 
                    tarefasEncontradas.addAll(extrairQuestionarios(salaOnline, nomeMateria, ra));

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

    private void revelarCardsDaSala(Page salaOnline) {
        int anterior = -1;
        for (int tentativa = 0; tentativa < 6; tentativa++) {
            int atual = salaOnline.locator("div.card-course[data-course-id]").count();
            salaOnline.evaluate("() => window.scrollTo(0, document.body.scrollHeight)");
            salaOnline.waitForTimeout(400);
            if (atual > 0 && atual == anterior) {
                break;
            }
            anterior = atual;
        }
        salaOnline.evaluate("() => window.scrollTo(0, 0)");
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
            // Adiciona a disciplina à lista de disciplinas 
            disciplinas.add(new DisciplinaPortal(href, nome));
        }
        return disciplinas;
    }

    private void aguardarConteudoDaDisciplina(Page salaOnline) {
        try {
            salaOnline.locator("li.section, .activity").first()
                    .waitFor(new Locator.WaitForOptions().setTimeout(8_000));
        } catch (TimeoutError ignored) {
            System.out.println("[Playwright] Conteúdo da disciplina não apareceu a tempo: " + salaOnline.url());
        }
    }

    private List<Task> extrairQuestionarios(Page salaOnline, String nomeMateria, String ra) {
        List<Task> tarefas = new ArrayList<>();
        for (QuestionariosSala.Extraido questionario : QuestionariosSala.interpretar(lerAtividades(salaOnline))) {
            String url = questionario.href().isBlank() ? salaOnline.url() : questionario.href();
            tarefas.add(new Task(
                    questionario.titulo(),
                    nomeMateria,
                    questionario.inicio(),
                    questionario.fim(),
                    url,
                    ra
            ));
            System.out.println(" [Playwright] Atividade encontrada: " + questionario.titulo()
                    + " | Início: " + questionario.inicio() + " | Término: " + questionario.fim());
        }
        if (tarefas.isEmpty()) {
            System.out.println("[Playwright] Nenhuma atividade encontrada em: " + nomeMateria);
        }
        return tarefas;
    }

    private List<QuestionariosSala.Evento> lerAtividades(Page salaOnline) {
        Object bruto = salaOnline.evaluate(SCRIPT_ATIVIDADES);
        if (!(bruto instanceof List<?> lista)) {
            return List.of();
        }
        List<QuestionariosSala.Evento> eventos = new ArrayList<>();
        for (Object item : lista) {
            if (!(item instanceof Map<?, ?> mapa)) {
                continue;
            }
            eventos.add(new QuestionariosSala.Evento(
                    textoEvento(mapa.get("tipo")),
                    textoEvento(mapa.get("origem")),
                    textoEvento(mapa.get("texto")),
                    textoEvento(mapa.get("href"))
            ));
        }
        return eventos;
    }

    private static String textoEvento(Object valor) {
        return valor == null ? "" : valor.toString();
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
                if (!texto.isBlank()) {
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

    private record DisciplinaPortal(String url, String nome) {}
}

