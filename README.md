# AI Code Review on Save — IntelliJ Plugin

An IntelliJ plugin that triggers an AI review of unstaged git diffs on every file save. Supports **GitHub Copilot** (default) and **Claude** as providers. Findings appear inline in the editor: red underlines for bugs, yellow for warnings, blue for info. Content-hash caching ensures unchanged files don't waste API calls.

---

## Why This Exists

File watchers lose output. macOS notifications vanish in 5 seconds. MCP doesn't work in pipe mode. The right abstraction was IntelliJ's `ExternalAnnotator` API — a three-phase lifecycle designed for exactly this: collect info on the EDT, run an external tool on a background thread, apply annotations back on the EDT. Clean, non-blocking, native.

---

## How It Works

```
File Save → BulkFileListener → DaemonCodeAnalyzer.restart()
                                        │
                          ┌─────────────────────────────────┐
                          │      ExternalAnnotator API      │
                          ├─────────────────────────────────┤
                          │ 1. collectInformation (EDT)     │
                          │    → file content                │
                          │    → SHA-256 content hash        │
                          │    → git diff (unstaged)         │
                          ├─────────────────────────────────┤
                          │ 2. doAnnotate (background)      │
                          │    → check cache (hash hit?)     │
                          │    → if miss: call AI provider   │
                          │      ┌─────────┐ ┌────────────┐ │
                          │      │ Copilot │ │   Claude   │ │
                          │      │(default)│ │ (optional) │ │
                          │      └─────────┘ └────────────┘ │
                          │    → cache findings              │
                          ├─────────────────────────────────┤
                          │ 3. apply (EDT)                  │
                          │    → red underline (ERROR)       │
                          │    → yellow underline (WARNING)  │
                          │    → blue underline (INFO)       │
                          │    → hover tooltip               │
                          └─────────────────────────────────┘
```

---

## Project Structure

```
ai-review-plugin/
├── build.gradle.kts                          # Gradle build with IntelliJ Platform plugin
├── settings.gradle.kts
└── src/main/
    ├── kotlin/com/aireview/plugin/
    │   ├── annotator/
    │   │   └── AiReviewExternalAnnotator.kt  # Core: 3-phase ExternalAnnotator lifecycle
    │   ├── cache/
    │   │   └── ReviewCacheService.kt         # Content-hash cache (SHA-256 → findings)
    │   ├── copilot/
    │   │   └── CopilotApiClient.kt           # GitHub Copilot chat completions client
    │   ├── claude/
    │   │   └── ClaudeApiClient.kt            # Anthropic Claude Messages API client
    │   ├── git/
    │   │   └── GitDiffService.kt             # git diff execution (unstaged + HEAD)
    │   ├── listener/
    │   │   └── FileSaveListener.kt           # BulkFileListener → triggers re-analysis
    │   ├── model/
    │   │   ├── AiReviewInfo.kt               # Data passed between annotator phases
    │   │   └── ReviewFinding.kt              # Finding model (line, severity, message)
    │   └── settings/
    │       ├── AiReviewConfigurable.kt       # Settings UI panel (provider dropdown)
    │       └── AiReviewSettings.kt           # Persistent settings + AiProvider enum
    └── resources/META-INF/
        └── plugin.xml                        # Plugin descriptor
```

---

## Supported AI Providers

| Provider               | Endpoint                                                    | Auth                             | Default Model              |
|------------------------|-------------------------------------------------------------|----------------------------------|----------------------------|
| **GitHub Copilot** ⭐   | `https://models.inference.ai.azure.com/chat/completions`    | GitHub PAT (Bearer)              | `gpt-4o`                   |
| **Claude (Anthropic)** | `https://api.anthropic.com/v1/messages`                     | Anthropic API key (`x-api-key`)  | `claude-sonnet-4-20250514` |

GitHub Copilot is the **default provider**. It uses the GitHub Models inference API — available to all GitHub Copilot subscribers. Switch between providers at any time in settings — the model name auto-updates when you change providers.

---

## Requirements

- **IntelliJ IDEA** 2024.3+ (builds 243–253.*)
- **JDK 21**
- **Git** available on `PATH`
- One of:
  - **GitHub Copilot**: A GitHub Personal Access Token (see [How to Get a GitHub Token](#how-to-get-a-github-token))
  - **Claude**: API key from [Anthropic Console](https://console.anthropic.com/)

---

## How to Get a GitHub Token

The GitHub Copilot provider uses the **GitHub Models API**, which authenticates with a standard GitHub Personal Access Token (PAT). No special scopes are required — access is tied to your GitHub Copilot subscription.

### Steps

1. Go to [github.com/settings/tokens](https://github.com/settings/tokens?type=beta)
2. Click **"Generate new token"** → choose **Fine-grained token**
3. Give it a name (e.g. `ai-review-plugin`)
4. Set expiration as desired
5. Under **Permissions → Account permissions**, set **"Models"** to **Read**
6. Click **"Generate token"**
7. Copy the token (starts with `github_pat_...`)
8. In IntelliJ: **Settings → Tools → AI Code Review** → paste it into the **GitHub Token** field

> **Note**: You must have an active **GitHub Copilot** subscription (Individual, Business, or Enterprise) on the same GitHub account for the Models API to work.

---

## Installation

### From ZIP (Recommended)

1. Build the plugin:
   ```bash
   cd ai-review-plugin
   ./gradlew buildPlugin
   ```
2. The ZIP is at `build/distributions/ai-review-plugin-1.0.0.zip`
3. In IntelliJ: **Settings → Plugins → ⚙️ → Install Plugin from Disk…** → select the ZIP
4. Restart IntelliJ

### From Source (Development)

```bash
cd ai-review-plugin
./gradlew runIde
```

This launches a sandboxed IntelliJ instance with the plugin loaded.

---

## Configuration

**Settings → Tools → AI Code Review**

| Setting            | Default               | Description                                          |
|--------------------|-----------------------|------------------------------------------------------|
| **Enable**         | `true`                | Master toggle for the plugin                         |
| **Review on save** | `true`                | Trigger review when a file is saved                  |
| **AI Provider**    | `GitHub Copilot`      | Choose between GitHub Copilot and Claude             |
| **GitHub Token / API Key** | *(empty)*     | Token for the selected provider                      |
| **Model**          | `gpt-4o`              | Model name (auto-switches with provider)             |
| **Max diff lines** | `500`                 | Truncate diffs longer than this                      |

When you switch providers in the dropdown, the label and default model update automatically:
- **GitHub Copilot** → "GitHub Token:" + `gpt-4o`
- **Claude** → "Anthropic API Key:" + `claude-sonnet-4-20250514`

---

## Severity Levels

| Severity    | Editor Style       | What It Catches                                        |
|-------------|--------------------|--------------------------------------------------------|
| **ERROR**   | 🔴 Red underline   | Bugs, logic errors, null safety, resource leaks        |
| **WARNING** | 🟡 Yellow underline| Code smells, missing error handling, DDD violations    |
| **INFO**    | 🔵 Blue underline  | Style, naming, better Kotlin idioms                    |

Hover over any underlined code to see the full finding and suggested fix in a tooltip.

---

## Caching

The plugin computes a SHA-256 hash of each file's content. If the hash matches a previous review, cached findings are returned instantly — no API call. The cache is per-session (in-memory) and invalidates automatically when file content changes.

---

## Supported File Types

Kotlin, Java, Python, JavaScript, TypeScript, Go, Rust, Scala, Groovy, YAML, JSON, XML, HTML, CSS, SQL, Shell scripts, Markdown, Properties, TOML, Gradle.

Files in `build/`, `out/`, `.gradle/`, `.idea/`, `node_modules/`, `.git/`, and `target/` directories are automatically skipped.

---

## Troubleshooting

| Problem                        | Solution                                                         |
|--------------------------------|------------------------------------------------------------------|
| No annotations appearing       | Check **Settings → Tools → AI Code Review** is enabled           |
| "Token not configured"         | Add your GitHub token or Anthropic API key in settings           |
| No diff detected               | The file must have unstaged or uncommitted git changes            |
| Annotations stale after edit   | Save the file (Cmd+S) to trigger a new review                   |
| Slow response                  | Reduce **Max diff lines** or switch to a faster model            |
| Wrong model after switching    | The model auto-updates; if you customised it, clear the field    |

Check the IntelliJ log (**Help → Show Log in Finder**) for detailed diagnostics — the plugin logs under `com.aireview.plugin`.

---

## Tech Stack

| Component         | Technology                                              |
|-------------------|---------------------------------------------------------|
| Language          | Kotlin 2.1.0                                            |
| JDK               | 21                                                      |
| Build             | Gradle + IntelliJ Platform Gradle Plugin 2.2.1          |
| Target IDE        | IntelliJ IDEA 2024.3+                                   |
| AI (default)      | GitHub Copilot (OpenAI-compatible chat completions)      |
| AI (alternative)  | Claude (Anthropic Messages API v2023-06-01)              |
| JSON parsing      | Gson 2.11.0                                              |
| Hashing           | SHA-256 (java.security.MessageDigest)                    |

---

## License

Internal tool — not published to JetBrains Marketplace.
