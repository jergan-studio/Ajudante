# Ajudante

Ajudante is a Java desktop AI coding assistant for Jergan Studio.

## Features

- Java 17 desktop application
- No Ajudante account or sign-in
- Open a local project folder
- AI chat for coding tasks
- Agent-style project tools:
  - list files
  - read files
  - create folders
  - create files
  - replace file contents
  - delete files
- Built-in project file tree
- Built-in editor
- Command/terminal output panel
- Supports OpenAI-compatible APIs and local Ollama
- AI receives explicit tool definitions and can make multi-step project changes

## Run

Install Java 17+.

On Windows:

```powershell
javac -d out src/com/jerganstudio/ajudante/Main.java
java -cp out com.jerganstudio.ajudante.Main
```

Or use the included Maven project:

```powershell
mvn package
java -jar target/ajudante-1.0.0.jar
```

## AI setup

Ajudante does not have its own sign-in system.

Open **Settings** in the app and choose either:

### Ollama

Run a local model with Ollama and set:

- Provider: Ollama
- Base URL: `http://localhost:11434/v1`
- Model: your installed model

### OpenAI-compatible API

Set the base URL, model, and API key for the provider you use.

The API key is stored only in the local application preferences and is never sent to Jergan Studio.

## Agent safety

The agent only operates inside the project folder selected by the user. It rejects paths that escape the project directory.

