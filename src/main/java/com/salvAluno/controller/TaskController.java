package com.salvAluno.controller;


// Importa outros pacotes e classes necessárias para o funcionamento do controlador
import com.salvAluno.domain.Student;
import com.salvAluno.domain.Task;
import com.salvAluno.repository.TaskRepository;
import com.salvAluno.service.AuthService;
import com.salvAluno.service.ScrapperService;
import jakarta.servlet.http.HttpSession;
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

    @Autowired
    private AuthService authService;

    // Endpoint para acionar o scrapping em segundo plano
    @PostMapping("/sync")
    public ResponseEntity<String> sincronizarTarefas(HttpSession session) {
        if (scrapperService.isSyncEmAndamento()) {
            return ResponseEntity.status(409).body("Já existe uma sincronização em andamento.");
        }

        Student aluno = authService.alunoDaSessao(session);
        String ra = aluno.getRa();
        String senha = authService.senhaDoPortal(aluno);
        new Thread(() -> scrapperService.scrapData(ra, senha), "portal-sync").start();

        return ResponseEntity.ok("Sincronização iniciada.");
    }

    @GetMapping("/sync/status")
    public ResponseEntity<SyncStatusResponse> statusSincronizacao(HttpSession session) {
        Student aluno = authService.alunoDaSessao(session);
        return ResponseEntity.ok(new SyncStatusResponse(
                scrapperService.isSyncEmAndamento(),
                scrapperService.getSyncMensagem(),
                taskRepository.countByOwnerRa(aluno.getRa())
        ));
    }

    // Endpoint para listar as tarefas do aluno logado
    @GetMapping
    public ResponseEntity<List<Task>> listarTodas(HttpSession session) {
        Student aluno = authService.alunoDaSessao(session);
        List<Task> tarefas = taskRepository.findByOwnerRaOrderByDueDateAsc(aluno.getRa());
        return ResponseEntity.ok(tarefas);
    }

}
