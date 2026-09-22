package com.salvAluno; 

import org.springframework.boot.SpringApplication; 
import org.springframework.boot.autoconfigure.SpringBootApplication; 
import org.springframework.scheduling.annotation.EnableScheduling; 

// Classe principal de inicialização do projeto 
@SpringBootApplication 

// Ativa o relógio do Spring para rotinas automáticas do Playwright 
@EnableScheduling 

public class Application { 

    public static void main(String[] args) {
        
        // Sobe o servidor interno (TOMCAT) e conecta dependencias 
        SpringApplication.run(Application.class, args); 

        // Mensagem para confirmar se a aplicação subiu sem erros 
        System.out.println("-----------------------------");
        System.out.println("🚀 SalvAluno iniciado com sucesso!");
        System.out.println("🌐 Acesse no navegador : http://localhost:8080");
        System.out.println("-----------------------------");

        
    }
}
