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
import java.time.LocalDateTime; 
import java.time.LocalDate; 
import java.time.format.DateTimeFormatter; 
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

// A tag @Service indica que esta classe é um serviço do Spring, permitindo que seja injetada em outras partes da aplicação.
// O Spring vai instanciar essa classe como um "Bean" que é um objeto especial que é mantido e gerido pelo próprio Spring
@Service 
public class ScrapperService { 

    private static final String SCRAPER_VERSION = "2-salaonline-popup";

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

    // Método principal para o Scrapping 
    public void scrapData(){ 
        System.out.println("Iniciando o scrapping de dados... [scraper=" + SCRAPER_VERSION + "]");

        RA = RA.trim();
        password = password.trim();

        if (RA.isEmpty() || password.isEmpty() || "seu_RA".equals(RA) || "sua_senha".equals(password)) {
            System.err.println("[Playwright] Credenciais do portal não configuradas. "
                    + "Preencha portal.RA e portal.password em src/main/resources/application.properties "
                    + "e gere o JAR novamente (mvn clean package), ou coloque application.properties na pasta do JAR.");
            return;
        }

        try (Playwright playwright = Playwright.create()) { 

            // Cria instância Chromium do Playwright   
            Browser browser = playwright.chromium().launch(
                // Lança em modo headless (sem interface gráfica) para que o scrapping seja feito em segundo plano 
                new BrowserType.LaunchOptions().setHeadless(true)
            );
            
            // Cria uma nova aba no navegador para fazer o scrapping 
            Page page = browser.newPage(); 

            try { 

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

                Page salaOnline = abrirSalaOnline(page);
                salaOnline.waitForLoadState();
                salaOnline.locator("div.card-course[data-course-id]").first().waitFor(
                        new Locator.WaitForOptions().setTimeout(60_000)
                );

                List<Task> tarefasEncontradas = new ArrayList<>();
                String paginaCursosUrl = salaOnline.url();
                List<String> urlsDisciplinas = listarUrlsDisciplinas(salaOnline);
                int quantidadeMaterias = urlsDisciplinas.size();

                System.out.println("[Playwright] Matérias encontradas: " + quantidadeMaterias);

                for (String urlDisciplina : urlsDisciplinas) {
                    salaOnline.navigate(urlDisciplina);
                    salaOnline.waitForLoadState();

                    String nomeMateria = "Disciplina Desconhecida";
                    ElementHandle tituloH1 = salaOnline.querySelector("h1");
                    if (tituloH1 != null) {
                        nomeMateria = tituloH1.innerText().trim();
                    }

                    ElementHandle linkCronograma = salaOnline.querySelector("h3.overviewCard-title a[title='Cronograma']");
                    if (linkCronograma == null) {
                        System.out.println("[Playwright] Nenhum cronograma encontrado para a matéria: " + nomeMateria);
                        continue;
                    }

                    linkCronograma.click();
                    salaOnline.waitForLoadState();

                    List<ElementHandle> linhasCronograma = salaOnline.querySelectorAll("ul.content_cronogramadv");

                    for (ElementHandle linha : linhasCronograma) {
                        List<ElementHandle> colunas = linha.querySelectorAll("li");

                        if (colunas.size() >= 3) {
                            String tituloAtividade = colunas.get(0).innerText().trim();
                            String dataPrazoStr = colunas.get(2).innerText().trim();
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

                // Persistência no Banco de Dados 
                if (tarefasEncontradas.isEmpty()){ 
                    System.out.println("[Playwright] Nenhuma tarefa encontrada"); 
                } else { 
                    taskRepository.saveAll(tarefasEncontradas);

                    System.out.println("[Playwright] Tarefas armazenadas: " + tarefasEncontradas.size());
                }


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

    private List<String> listarUrlsDisciplinas(Page salaOnline) {
        Set<String> urls = new LinkedHashSet<>();
        Locator links = salaOnline.locator("div.card-course[data-course-id] a[href*='course/view.php']");
        int total = links.count();
        for (int i = 0; i < total; i++) {
            String href = links.nth(i).getAttribute("href");
            if (href != null && !href.isBlank()) {
                urls.add(href);
            }
        }
        return new ArrayList<>(urls);
    }

    // Converte datas do formato "DD/MM/YY" para LocalDateTime  
    private LocalDateTime converterDataPrazo(String dataStr){ 

        try{ 
            // Define o formato esperado da data que virá do site do CEUB 
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yy");
            String dataEncontrada = dataStr.replaceAll(".*?(\\d{2}/\\d{2}/\\d{2}).*", "$1");
            LocalDate data = LocalDate.parse(dataEncontrada, formatter);

            // Retorna a data convertida para LocalDateTime, com hora definida como 23:59 (fim do dia)
            return data.atTime(23,59); 
        
        } catch (Exception e){ 
    
            System.err.println("[Playwright] Erro ao converter a data: " + dataStr); 
            return null;
        }
    }
}

