# 🎓 SalvAluno 

Um organizador automático de tarefas acadêmicas que utiliza automação para extrair prazos e avisos diretamente do portal da faculdade e exibi-los em uma interface centralizada.

## 📌 Sobre o Projeto 

Este projeto nasceu da necessidade de centralizar e automatizar a gestão de tarefas e compromissos acadêmicos. Através de visualização rápida e notificações claras, o objetivo é evitar atrasos na entrega de trabalhos e perda de prazos importantes. 

### 🔄 Como Funciona o Fluxo de Dados

1. **Gatilho (API REST)**: O utilizador ou interface faz um pedido `POST /api/tasks/sync`.
2. **Automação (Playwright)**: O `ScrapperService` lança uma instância do navegador Chromium em *headless mode* e realiza o login automatizado no portal da instituição.
3. **Extração de Cronograma**: O robô navega pelas disciplinas do aluno, acede às páginas de **Cronograma** e mapeia as linhas de atividades (`ul.content_cronogramadv`).
4. **Tratamento e Parsing**: As datas no formato `DD/MM/YY` são convertidas para objetos `LocalDateTime` do Java.
5. **Persistência**: As tarefas filtradas são salvas na base de dados através do `TaskRepository`.
6. **Disponibilização**: As tarefas ficam prontas para consulta através do endpoint `GET /api/tasks`.


## ✨ Principais Funcionalidades 
- **Web Scraping / Automação** : Varredura automática do portal acadêmico via Playwright para coleta de tarefas, prazos e avisos.
- **Gestão de Tarefas** : Exibição organizada dos compromissos por data de entrega, disciplina e prioridade.
- **Alertas e Notificações** : Avisos na interface e através de mensagens para o usuário para tarefas com prazos próximos do vencimento.
- **Interface do Usuário** : Painel simples e intuitivo para acompanhamento do progresso das atividades.

## 🛠️ Stacks 
- **Linguagem**: Java 17+
- **Framework Principal**: Spring Boot 3.x
  - **Spring Web**: Criação de endpoints RESTful.
  - **Spring Data JPA**: Persistência e abstração de base de dados.
- **Automação / Web Scraping**: Playwright for Java
- **Base de Dados**:
  - H2 Database (Memória / Desenvolvimento e Testes)
  - Compatível com PostgreSQL / MySQL em Produção
- **Build Tool**: Maven


## 📂 Estrutura do Projeto

```text
salvAluno/
├── src/
│   ├── main/
│   │   ├── java/com/salvAluno/
│   │   │   ├── SalvAlunoApplication.java    # Classe Principal Spring Boot
│   │   │   ├── controller/
│   │   │   │   └── TaskController.java      # Endpoints da API REST
│   │   │   ├── domain/
│   │   │   │   └── Task.java                # Entidade de Domínio (JPA)
│   │   │   ├── repository/
│   │   │   │   └── TaskRepository.java      # Interface de acesso ao Banco
│   │   │   └── service/
│   │   │       └── ScrapperService.java     # Lógica do Robô e Parse Web
│   │   └── resources/
│   │       ├── application.properties      # Configurações locais
│   │       └── application-example.properties # Modelo de configuração sem segredos
├── pom.xml
└── README.md
```

## ⚙️ Configuração e Instalação

### Pré-requisitos
- Java Development Kit (JDK) 17+
- Maven 3.8+
- Git

### 1.) Clonar o repositório

```bash
git clone <URL_DO_REPOSITORIO>
cd SalvAluno
```

### 2.) Configurar as credenciais do portal

Crie ou edite o arquivo `src/main/resources/application.properties` e informe os dados de acesso ao portal da instituição:

```properties
portal.url=https://ea.uniceub.br/Sistema/Acesso/Login
portal.RA=seu_RA
portal.password=sua_senha
```

Não versionar esse arquivo quando ele contiver credenciais reais. Para ambientes compartilhados, use as opções de configuração externa do Spring Boot ou um arquivo local ignorado pelo Git.

### 3.) Instalar os navegadores do Playwright

Na primeira configuração do projeto, instale o Chromium usado pelo robô:

```bash
mvn exec:java -Dexec.mainClass=com.microsoft.playwright.CLI -Dexec.args="install chromium"
```

### 4.) Compilar e executar

Para baixar as dependências e gerar o build:

```bash
mvn clean install
```

Em seguida, inicie a aplicação:

```bash
mvn spring-boot:run
```

Também é possível executar o arquivo JAR gerado:

```bash
java -jar target/*.jar
```

Quando a aplicação estiver em execução, acesse `http://localhost:8080`.

### 5.) Consultar e sincronizar tarefas

Liste as tarefas já salvas:

```bash
curl http://localhost:8080/api/tasks
```

Inicie uma nova sincronização com o portal:

```bash
curl -X POST http://localhost:8080/api/tasks/sync
```

A sincronização é executada em segundo plano. Aguarde alguns instantes e consulte novamente `GET /api/tasks` para verificar as tarefas coletadas.

## 🐳 Execução com Docker

Os arquivos `Dockerfile` e `docker-compose.yaml` estão reservados para execução conteinerizada. Antes de usar Docker, confirme que eles possuem a configuração da imagem Java, das dependências Maven e do navegador Chromium do Playwright.
