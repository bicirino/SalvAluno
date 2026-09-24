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
                    String nomeMateria = "Disciplina Desconhecida"; 
                    ElementHandle tituloH1 = page.querySelector("h1"); 

                    if (tituloH1 != null ){ 
                        nomeMateria = tituloH1.innerText().trim(); 
                    }

                    // Procura o link do cronograma na página da matéria atual 
                    ElementHandle linkCronograma = page.querySelector("a[title='Cronograma']"); 

                    if (linkCronograma == null){ 
                        System.out.println("[Playwright] Nenhum cronograma encontrado para a matéria: " + nomeMateria);
                        
                        continue; 
                    }

                    linkCronograma.click(); 
                    page.waitForLoadState(); 

                    // Procura a lista de atividades dentro do cronograma da matéria atual
                    List<ElementHandle> linhasCronograma = page.querySelectorAll("ul.content_cronogramadv"); 

                    for (ElementHandle linha : linhasCronograma){ 

                        List <ElementHandle> colunas = linha.querySelectorAll("li");
                       
                        if (colunas.size() >= 3){ 
                            
                            // Pega o título da atividade e a data de prazo da atividade 
                            String tituloAtividade = colunas.get(0).innerText().trim(); 
                            String dataPrazoStr = colunas.get(2).innerText().trim(); 
                            
                            String tituloLower = tituloAtividade.toLowerCase();
                            if (tituloLower.contains("atividade") || 
                                tituloLower.contains("tarefa") || 
                                tituloLower.contains("desafio") ||
                                tituloLower.contains("trabalho") || 
                                tituloLower.contains("prova") || 
                                tituloLower.contains("fórum") || 
                                tituloLower.contains("oficina") || 
                                tituloLower.contains("laboratório")){ 

                                LocalDateTime prazoFinal = converterDataPrazo(dataPrazoStr);

                                if (prazoFinal != null) {
                                    // Como o cronograma não tem o link direto da tarefa, salvamos a URL do próprio cronograma
                                    String urlCronograma = page.url();
                                
                                    Task task = new Task(tituloAtividade, nomeMateria, prazoFinal, urlCronograma);
                                    tarefasEncontradas.add(task);
                                    
                                    System.out.println(" [Playwright] Atividade Encontrada: " + tituloAtividade + " | Prazo: " + dataFimStr);
                                }
                               
                            }
                        }

                    }

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


    // Converte datas do formato "DD/MM/YY" para LocalDateTime  
    private LocalDateTime converterDataPrazo(String dataStr){ 

        try{ 
            // Define o formato esperado da data que virá do site do CEUB 
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yy");
            
            // O "parse" serve para converter dados em formato de texto em outro tipo de dados  
            LocalDate data = LocalDate.parse(dataStr, formattter); 

            // Retorna a data convertida para LocalDateTime, com hora definida como 23:59 (fim do dia)
            return data.atTime(23,59); 
        
        } catch (Exception e){ 
    
            System.err.println("[Playwright] Erro ao converter a data: " + dataStr); 
            return null;
        }
    }
}

