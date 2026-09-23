package com.salvAluno.service; 


// Este código será responsável por fazer o scrapping de dados da página da faculdade e extrair os dados 

// Importações necessárias para o funcionamento do serviço de scrapping
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.ElementHandle;
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

                page.fill("#coAcesso", RA); 
                page.fill("#coSenha", password); 

                page.click ("#btn-login");
                page.waitForLoadState(); 

                System.out.println("[Playwright] Login realizado com sucesso");

                page.navigate("https://salaonline.ceub.br/my/"); 
                page.waitForLoadState(); 
                
                List<Task> tarefasEncontradas = new ArrayList<>(); 

                // Encontra todos os links das matérias
                List<ElementHandle> linksMaterias = page.querySelectorAll("a[href*='course/view.php']"); 

                // Cria uma lista para armazenar as URLs das matérias encontradas
                List<String> urlsMaterias = new ArrayList<>(); 

                // Itera sobre os links encontrados e adiciona as URLs únicas à lista 
                for (ElementHandle link : linksMaterias){ 
                    String url = link.getAttribute("href");
                    
                    if (url != null && !urlsMaterias.contains(url)){ 
                        urlsMaterias.add(url);
                    }
                }

                System.out.println("[Playwright] Matérias encontradas: " + urlsMaterias.size());

                // Loop para entrar em cada matéria e varrer as atividades/tarefas 
                for (String urlMateria : urlsMaterias){ 
                    page.navigate(urlMateria); 
                    page.waitForLoadState(); 

                    // Obter o nome da disciplina na página atual 
                    String nomeMateria = "Disciplina"; 
                    if (page.querySelector("h1") != null ){ 
                        nomeMateria = page.querySelector("name-course").innerText(); 
                    }

                    // Cria uma lista para armazenar os blocos de atividades encontrados na matéria atual 
                    List <ElementHandle> blocosAtividades = page.querySelectorAll(".activity-item, li.activity");  

                    for (ElementHandle bloco : blocosAtividades){ 

                        // Pega o link e o título da atividade dentro do bloco atual
                        ElementHandle linkElement = bloco.querySelector("a.aalink");
                        
                        // Se não tem link, ignora o bloco e continua para o próximo bloco
                        if (linkElement == null ){ 
                            continue; 
                        } 

                        String titulo = linkElement.innerText().trim(); 
                        String url = linkElement.getAttribute("href"); 

                        // Procura a div exata de datas que contém a data de entrega da atividade, se existir 
                        ElementHandle divDatas = bloco.querySelector("div[data-region='activity-dates']"); 
                        String dataPrazo = null; 

                        if (divDatas != null){ 

                            // Pega o texto da div de datas e remove quebras de linha para facilitar a leitura 
                            String textoDatas = divDatas.innerText().replace("\n", " ");

                            if (textoDatas.contains("Fechado:")){ 
                                dataPrazo = extrairTextoApos(textoDatas, "Fechado:");
                            }else if (textoDatas.contains("Vencimento:")){ 
                                dataPrazo = extrairTextoApos(textoDatas, "Vencimento:");
                            }
                        }

                        // Verifica se a atividade já foi concluída 
                        boolean concluida = bloco.innerText().contains("Feito:"); 

                        System.out.println("📌 Atividade: " + titulo);
                        System.out.println("   🗓️ Prazo bruto: " + (dataPrazo != null ? dataPrazo : "Sem prazo"));
                        System.out.println("   ✅ Status: " + (concluida ? "Concluída" : "Pendente"));
                        System.out.println("   🔗 Link: " + url);
                        
                        if (!concluida){ 

                            // Se a data de prazo não foi encontrada, define um prazo provisório de 7 dias a partir da data atual
                            LocalDateTime prazoProvisorio = LocalDateTime.now().plusDays(7); 
                            
                            // Se a data de prazo foi encontrada, tenta converter para LocalDateTime, caso contrário, usa o prazo provisório 
                            Task task = new Task(titulo, nomeMateria, prazoProvisorio, url); 
                            tarefasEncontradas.add(task);
                        }

                        // Cria uma lista para armazenar todas asatividades encontradas na matéria atual 
                        List <ElementHandle> todasAtividades = page.querySelectorAll(
                            "a[href*='mod/assign/view.php']" + 
                            "a[href*='mod/quiz/view.php']" +
                            "a[href*='mod/urlweb/view.php']" +
                            "a[href*='mod/page/view.php']"  
                        );

                        for (ElementHandle atividade : todasAtividades){ 
                            String tituloAtividade = atividade.innerText().trim();
                            String urlAtividade = atividade.getAttribute("href"); 
                            
                            if (!tituloAtividade.isEmpty() && urlAtividade != null){ 

                                System.out.println("[Playwright] Atividade encontrada: " + tituloAtividade + " - " + urlAtividade);

                                Task task = new Task(tituloAtividade, nomeMateria, dataPrazo, urlAtividade);
                                tarefasEncontradas.add(task); 
                            }
                        }
                    }
                }

                if (tarefasEncontradas.isEmpty()){ 
                    System.out.println("[Playwright] Nenhuma tarefa encontrada"); 
                } else { 
                    taskRepository.saveAll(tarefasEncontradas);

                    System.out.println("[Playwright] Tarefas armazenadas: " + tarefasEncontradas.size());
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

    private String extrairTextoApos(String texto, String palavraChave){ 

        try{ 
            int indice = texto.indexOf(palavraChave); 
            if (indice != -1){ 
                String substring = texto.substring(indice + palavraChave.length()).trim();

                return substring;  
            } 
        } catch (Exception e){ 
    
            return "Erro ao extrair data";
        }

        return null; 
    }
}

