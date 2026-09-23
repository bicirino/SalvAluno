// Este código será responsável por fazer o scrapping de dados da página da faculdade e extrair os dados 

package com.salvAluno.service; 

// Importações necessárias para o funcionamento do serviço de scrapping
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.salvAluno.domain.Task;
import com.salvAluno.repository.TaskRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

// Outras importações necessárias para o Java 
import java.time.LocalDateTime; 
import java.util.ArrayList; 
import java.util.List; 

// A tag @Service indica que esta classe é um serviço do Spring, permitindo que seja injetada em outras partes da aplicação.
// O Spring vai instanciar essa classe como um "Bean" que é um objeto especial que é mantido e gerido pelo próprio Spring
@Service 
public class ScrapperService { 

    // Injeta o repositório para podermos guardar as tarefas diretamente no banco de dados 
    @Autowired 
    private TaskRepository taskRepository; 

    // Injeta a URL do portal da faculdade, que será usada para fazer o scrapping 
    @Value("${portal.url:https://ea.uniceub.br/Sistema/Acesso/Login}")
    private String portalUrl; 

    // Injeta o RA do usuário que será usado no login do portal da faculdade 
    @Value("${portal.RA: seu_RA}") 
    private String RA; 

    // Injeta a senha do usuário que será usada no login do portal da faculdade 
    @Value("${portal.password: sua_senha}")
    private String password; 

    // Método principal para o Scrapping 
    public void scrapData(){ 
        System.out.println("Iniciando o scrapping de dados...");

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

                page.fill("RA ou Email ou CPF", RA); 
                page.fill("Senha", password); 

                page.click ("button[type='submit']");
                page.waitForLoadState(); 

                System.out.println("[Playwright] Login realizado com sucesso");

                page.navigate("https://salaonline.ceub.br/my/"); 
                
                List<Task> tarefasEncontradas = new ArrayList<>(); 

                var elementos = page.querySelectorAll(".item-tarefa"); 

                for (var element : elementos) {
                    String title = element.querySelector(".titulo").innerText();
                    String subject = element.querySelector(".disciplina").innerText();

                    Task task = new Task(title, subject, LocalDataTime.now().plusDays(7), "http://salaonline.ceunb.br/my/"); 
                    tarefasEncontradas.add(task); 
                }

                if (tarefasEncontradas.isEmpty()) { 
                    System.out.println("[Playwright] Nenhuma tarefa encontrada."); 
                } else { 
                    System.out.println("[Playwright] Tarefas encontradas: " + tarefasEncontradas.size()); 
                    taskRepository.saveAll(tarefasEncontradas); 
                }

            }catch (Exception e) { 
                System.err.println("Erro ao preencher os campos de login: " + e.getMessage()); 
            }finally{ 
                browser.close(); 
                System.out.println("[Playwright] Varredura finalizada.");
            }

        } catch (Exception e) { 
            System.err.println("Erro ao iniciar o Playwright: " + e.getMessage()); 
        }
    }
}

