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

### 2.) Cadastrar o acesso ao portal

O RA e a senha **não** ficam em variáveis de ambiente nem no `application.properties`. Com a aplicação no ar, abra `http://localhost:8080` e crie uma conta com **nome**, **RA** e **senha** do Espaço Aluno. Na próxima vez, entre só com RA e senha.

A senha da conta é armazenada com hash BCrypt. A mesma senha, usada pelo robô no portal, fica cifrada com AES-256-GCM. A chave local é criada em `data/crypto.key`. A pasta `data/` (chave e banco) não deve ser publicada.

O endereço do portal continua configurável, sem credenciais:

```properties
portal.url=https://ea.uniceub.br/Sistema/Acesso/Login
```

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

Após a compilação, inicie a aplicação executando o arquivo JAR gerado:

```bash
java -jar target/salv-aluno-0.0.1-SNAPSHOT.jar
```

O comando `mvn spring-boot:run` também pode ser usado em ambientes compatíveis:

```bash
mvn spring-boot:run
```

Neste projeto, a execução pelo JAR foi utilizada porque o `spring-boot:run` apresentou erro ao localizar a classe principal.

Quando a aplicação estiver em execução, acesse `http://localhost:8080`.

### 5.) Consultar e sincronizar tarefas

Entre na conta e use o botão **Sincronizar tarefas**. O robô autentica no portal com o RA e a senha cadastrados. As rotas `/api/tasks` exigem essa sessão: sem login, a API responde `401`.

A sincronização é executada em segundo plano. O painel mostra o nome cadastrado e, ao terminar, as tarefas daquele RA.

## 🐳 Execução com Docker

Os arquivos `Dockerfile` e `docker-compose.yaml` estão reservados para execução conteinerizada. Antes de usar Docker, confirme que eles possuem a configuração da imagem Java, das dependências Maven e do navegador Chromium do Playwright.
