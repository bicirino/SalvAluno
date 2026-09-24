package main.java.com.salvAluno.controller;


// Importa outros pacotes e classes necessárias para o funcionamento do controlador
import com.salvAluno.domain.Task;
import com.salvAluno.repository.TaskRepository;
import com.salvAluno.service.ScrapperService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

// Importa outras bibliotecas necessárias para o Java 
import java.util.List; 

@RestController 
@RequestMapping("api/tasks") 
@CrossOrigin(origins = "*") // Permite requisição do frontend em qualquer origem (domínio) 
public class TaskController { 

    
    @Autowired 
    private ScrapperService scrapperService; 

    
    @Autowired 
    private TaskRepository taskRepository; 

    // Endpoint para acionar o scrapping em segundo plano 
    @PostMapping("/sync")
    public ResponseEntity<String> sincronizarTarefas(){ 

        // Executa o scraper numa thread separada para não causar Timeout na requisição HTTP 
        new Thread(() -> scrapperService.scrapData()).start(); 

        return ResponseEntity.ok("Scrapping iniciado em segundo plano. As tarefas serão sincronizadas em breve."); 
    }

    // Endpoint para listar todas as tarefas do banco de dados 
    @GetMapping 
    public ResponseEntity<List<Task>> listarTodas(){ 
        
        // Busca todas as tarefas no banco de dados usando o repositório
        List<Task> tarefas = taskRepository.findAll(); 
        
        
        return ResponseEntity.ok(tarefas); 
    }

}
