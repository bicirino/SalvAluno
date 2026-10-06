# 🎓 SalvAluno 

Um organizador automático de tarefas acadêmicas que utiliza automação para extrair prazos e avisos diretamente do portal da faculdade e exibi-los em uma interface centralizada.

## 📌 Sobre o Projeto 

Este projeto nasceu da necessidade de centralizar e automatizar a gestão de tarefas e compromissos acadêmicos. Através de visualização rápida e notificações claras, o objetivo é evitar atrasos na entrega de trabalhos e perda de prazos importantes. 

### 🔄 Como Funciona o Fluxo de Dados

1. **Acesso**: O aluno abre `http://localhost:8080`, faz **cadastro** ou **login** (nome, RA e senha do Espaço Aluno) em `login.html`.
2. **Sessão**: O servidor cria a sessão (`SALVALUNO_SESSION`) e o dashboard exibe **Olá, [nome]!**.
3. **Gatilho (API REST)**: Com sessão ativa, o usuário aciona `POST /api/tasks/sync`.
4. **Automação (Playwright)**: O `ScrapperService` usa o **RA e a senha da conta logada** (não variáveis de ambiente) para login no portal em *headless mode*.
5. **Extração das salas**: O robô abre cada disciplina e percorre as atividades. Só questionários viram tarefa; o prazo vem do bloco Aberto/Fechado da seção.
6. **Tratamento e Parsing**: Essas datas viram `LocalDateTime`.
7. **Persistência**: Tarefas são salvas com `ownerRa` do aluno; sync substitui as tarefas daquele RA via `TaskStore`.
8. **Consulta**: `GET /api/tasks` devolve apenas as tarefas do aluno autenticado.

Documentação detalhada da autenticação: [docs/AUTENTICACAO-E-LOGIN.md](docs/AUTENTICACAO-E-LOGIN.md).

## ✨ Principais Funcionalidades 
- **Cadastro e login** : Tela alinhada ao dashboard; credenciais do portal guardadas com segurança.
- **Web Scraping / Automação** : Varredura do portal acadêmico via Playwright com RA/senha do aluno logado.
- **Gestão de Tarefas** : Listagem por aluno, ordenada por prazo.
- **Interface do Usuário** : Painel com saudação personalizada, sync e logout.

## 🛠️ Stacks 
- **Linguagem**: Java 17+
- **Framework Principal**: Spring Boot 3.x
  - **Spring Web**: Endpoints REST e páginas estáticas.
  - **Spring Data JPA**: Persistência (alunos + tarefas).
  - **spring-security-crypto**: BCrypt (apenas hash; sem Spring Security completo).
- **Automação**: Playwright for Java
- **Base de Dados**: H2 em arquivo local (`data/salvaluno`)
- **Build Tool**: Maven

## 📂 Estrutura do Projeto

```text
salvAluno/
├── docs/
│   └── AUTENTICACAO-E-LOGIN.md     # Revisão e doc da tela de login
├── src/
│   ├── main/
│   │   ├── java/com/salvAluno/
│   │   │   ├── Application.java
│   │   │   ├── controller/
│   │   │   │   ├── AuthController.java
│   │   │   │   ├── TaskController.java
│   │   │   │   └── ...
│   │   │   ├── domain/
│   │   │   │   ├── Student.java
│   │   │   │   └── Task.java
│   │   │   ├── repository/
│   │   │   ├── security/
│   │   │   │   ├── CredentialCipher.java
│   │   │   │   └── SessionAuthFilter.java
│   │   │   └── service/
│   │   │       ├── AuthService.java
│   │   │       ├── ScrapperService.java
│   │   │       └── TaskStore.java
│   │   └── resources/
│   │       ├── application.yml
│   │       ├── application.properties.example
│   │       └── static/
│   │           ├── index.html
│   │           ├── login.html
│   │           └── style.css
│   └── test/
│       └── java/com/salvAluno/AuthFlowTest.java
├── data/                             # gitignored: banco H2 + crypto.key
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

O RA e a senha **não** ficam em variáveis de ambiente nem no `application.properties`. Com a aplicação no ar, abra `http://localhost:8080` e crie uma conta com **nome**, **RA** e **senha** do Espaço Aluno. Depois, entre só com RA e senha.

- Senha do app: hash **BCrypt**
- Senha do portal (robô): **AES-256-GCM**; chave em `data/crypto.key`
- Pasta `data/` não deve ser versionada

URL do portal (sem credenciais), em `application.yml` ou `application.properties`:

```properties
portal.url=https://ea.uniceub.br/Sistema/Acesso/Login
```

### 3.) Instalar os navegadores do Playwright

```bash
mvn exec:java -Dexec.mainClass=com.microsoft.playwright.CLI -Dexec.args="install chromium"
```

### 4.) Compilar e executar

```bash
mvn clean package
java -jar target/salv-aluno-0.0.1-SNAPSHOT.jar
```

Acesse `http://localhost:8080`.

### 5.) API rápida (com sessão após login)

| Ação | Método | Rota |
|------|--------|------|
| Cadastro | POST | `/api/auth/register` |
| Login | POST | `/api/auth/login` |
| Perfil | GET | `/api/auth/me` |
| Logout | POST | `/api/auth/logout` |
| Listar tarefas | GET | `/api/tasks` |
| Sincronizar | POST | `/api/tasks/sync` |
| Status sync | GET | `/api/tasks/sync/status` |

Sem sessão, `/api/tasks` retorna **401**.

### 6.) Testes

```bash
mvn test
```

## 🐳 Execução com Docker

Os arquivos `Dockerfile` e `docker-compose.yaml` estão reservados para execução conteinerizada. Antes de usar Docker, confirme imagem Java, Maven, Playwright/Chromium e persistência de `data/` (banco + chave de criptografia).
