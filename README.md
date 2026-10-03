# Retroguide

Assistant conversationnel RAG (Retrieval-Augmented Generation) pour Vintage Store.
Les documents PDF sont découpés, vectorisés avec `all-MiniLM-L6-v2`, puis indexés dans
Qdrant. À chaque question, l'assistant récupère le contexte pertinent avant de générer
une réponse avec GPT-4.1. Il peut aussi appeler des outils métier pour connaître la date
de mise à jour de documents spécifiques.

## Prérequis

- Java 21
- Docker et Docker Compose
- Une clé API OpenAI dans la variable `OPENAI_API_KEY`

## Démarrage

1. Démarrer Qdrant :

   ```bash
   docker compose up -d qdrant
   ```

2. Ajouter les PDF à indexer dans le projet, puis lancer l'ingestion :

   ```bash
   mvn exec:java -Dexec.mainClass=me.akkhalef.document.DocumentIngestor
   ```

3. Configurer la clé OpenAI et démarrer le chat :

   ```bash
   export OPENAI_API_KEY="..."
   mvn exec:java
   ```

   Saisir `quit` pour arrêter l'application.

> L'ingestion parcourt récursivement les fichiers `.pdf` du répertoire du projet et
> stocke leurs vecteurs dans la collection Qdrant `VintageStoreIndex`.

## Séquence RAG avec outil

```mermaid
sequenceDiagram
    autonumber
    actor Utilisateur
    participant Chat as ChatService
    participant RAG as LangChain4j<br/>ContentRetriever
    participant Embed as all-MiniLM-L6-v2
    participant Qdrant as Qdrant<br/>VintageStoreIndex
    participant LLM as OpenAI GPT-4.1
    participant Outil as ChatTools

    Utilisateur->>Chat: Pose une question
    Chat->>RAG: assistant.chat(question)
    RAG->>Embed: Vectorise la question
    Embed-->>RAG: Vecteur de requête
    RAG->>Qdrant: Recherche de segments similaires (gRPC)
    Qdrant-->>RAG: Contexte documentaire pertinent
    RAG->>LLM: Question + contexte + message système

    alt Une date de mise à jour est demandée
        LLM->>Outil: Appelle l'outil approprié
        Outil-->>LLM: Date de dernière mise à jour
        LLM->>LLM: Compose la réponse avec le résultat de l'outil
    else Aucune information d'outil nécessaire
        LLM->>LLM: Compose la réponse avec le contexte RAG
    end

    LLM-->>Chat: Réponse finale
    Chat-->>Utilisateur: Affiche la réponse
```

## Composants

| Composant | Rôle |
| --- | --- |
| `DocumentIngestor` | Parse les PDF, les découpe en segments de 2 000 caractères avec un chevauchement de 200, puis indexe leurs embeddings. |
| `ChatService` | Initialise l'assistant et fournit l'interface de chat en ligne de commande. |
| Qdrant | Stocke les embeddings et retrouve les segments les plus proches. |
| `ChatTools` | Expose les dates de mise à jour des documents de politique et conditions. |
