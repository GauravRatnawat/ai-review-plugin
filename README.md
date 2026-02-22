# AI Code Review on Save — IntelliJ Plugin

An IntelliJ plugin that reviews your code changes using AI every time you save a file. It sends your git diff to **GitHub Models** or **Claude** and renders findings directly in the editor — Error Lens style — with colored inline text, underlines, and tooltips.

![IntelliJ IDEA](https://img.shields.io/badge/IntelliJ_IDEA-2025.3+-blue?logo=intellijidea&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-2.1.0-7F52FF?logo=kotlin&logoColor=white)
![Java](https://img.shields.io/badge/JDK-21-orange?logo=openjdk&logoColor=white)
![License](https://img.shields.io/badge/License-MIT-green)

---

## Features

- **Inline findings** — Error Lens-style colored text at the end of each flagged line
- **Underline annotations** — Red (error), yellow (warning), blue (info) underlines with hover tooltips
- **Multiple AI providers** — GitHub Models (GPT-4.1, Llama, DeepSeek, Grok, etc.) or Claude (Anthropic)
- **Auto-review on save** — Triggers on every Cmd+S / Ctrl+S
- **Content-hash caching** — SHA-256 hash per file; unchanged files skip the API call entirely
- **Light/dark theme support** — Colors adapt to your IDE theme

---

## How It Works

```
Save File (Cmd+S)
    |
    v
BulkFileListener --> DaemonCodeAnalyzer.restart()
                          |
            +---------------------------+
            |    ExternalAnnotator API   |
            +---------------------------+
            | 1. collectInformation (EDT)|
            |    - file content          |
            |    - SHA-256 content hash  |
            |    - git diff (unstaged)   |
            +---------------------------+
            | 2. doAnnotate (background) |
            |    - cache hit? return     |
            |    - cache miss? call AI:  |
            |      [GitHub Models]       |
            |      [Claude API]          |
            |    - cache findings        |
            +---------------------------+
            | 3. apply (EDT)             |
            |    - underline annotations |
            |    - inline inlay text     |
            |    - hover tooltips        |
            +---------------------------+
```

---

## Quick Start

### 1. Clone and build

```bash
git clone https://github.com/GauravRatnawat/ai-review-plugin.git
cd ai-review-plugin
./gradlew buildPlugin
```

The plugin ZIP will be at `build/distributions/ai-review-plugin-1.0.0.zip`.

### 2. Install in IntelliJ

1. Open IntelliJ IDEA
2. Go to **Settings** (Cmd+, / Ctrl+Alt+S) -> **Plugins** -> gear icon -> **Install Plugin from Disk...**
3. Select the ZIP file from step 1
4. Restart IntelliJ

### 3. Configure your API key

1. Go to **Settings** -> **Tools** -> **AI Code Review**
2. Choose your **AI Provider** (GitHub Models is the default)
3. Paste your **API key / token**
4. Click **OK**

### 4. Start coding

Open any file with git changes, make an edit, and press **Cmd+S**. Findings will appear inline within a few seconds.

---

## Getting an API Key

### GitHub Models (default provider)

GitHub Models uses the GitHub Models inference API with your GitHub Personal Access Token.

1. Go to [github.com/settings/tokens](https://github.com/settings/tokens?type=beta)
2. Click **Generate new token** -> **Fine-grained token**
3. Name it (e.g., `ai-review-plugin`)
4. Under **Permissions** -> **Account permissions**, set **Models** to **Read**
5. Click **Generate token**
6. Copy the token (starts with `github_pat_...`)

> You need an active [GitHub Copilot](https://github.com/features/copilot) subscription for the Models API to work.

### Claude (Anthropic)

1. Go to [console.anthropic.com/settings/keys](https://console.anthropic.com/settings/keys)
2. Create a new API key
3. Copy the key (starts with `sk-ant-...`)

---

## Configuration

**Settings** -> **Tools** -> **AI Code Review**

| Setting | Default | Description |
|---------|---------|-------------|
| **Enable AI Code Review** | On | Master toggle |
| **Review on file save** | On | Automatically review when you save |
| **AI Provider** | GitHub Models (GPT-4.1) | Choose provider and model tier |
| **Token / API Key** | *(empty)* | Your GitHub PAT or Anthropic API key |
| **Model** | `openai/gpt-4.1` | Model to use (dropdown + custom input) |
| **Max diff lines** | `500` | Truncate large diffs to control cost |

When you switch providers, the model dropdown updates automatically with available models for that provider.

### Available Models

| Provider | Models |
|----------|--------|
| **GitHub Models** | `openai/gpt-4.1`, `openai/gpt-4o`, `openai/o3`, `meta/meta-llama-3.1-405b-instruct`, `deepseek/deepseek-r1-0528`, `xai/grok-3`, and more |
| **GitHub Models (Mini)** | `openai/gpt-4.1-mini`, `openai/gpt-4.1-nano`, `openai/o3-mini`, `openai/o4-mini`, `xai/grok-3-mini`, and more |
| **Claude** | `claude-sonnet-4-5-20250514`, `claude-opus-4-5`, `claude-3-7-sonnet-20250219` |

You can also type any custom model name in the dropdown.

---

## Severity Levels

| Severity | Inline Style | Underline | What It Catches |
|----------|-------------|-----------|-----------------|
| **ERROR** | Red text with cross icon | Red underline | Bugs, logic errors, null safety issues, resource leaks |
| **WARNING** | Amber text with warning icon | Yellow underline | Code smells, missing error handling, performance issues |
| **INFO** | Blue text with info icon | Blue underline | Style improvements, naming suggestions, better idioms |

Hover over any underlined code to see the full finding and suggested fix in a tooltip.

---

## Supported File Types

The plugin reviews files with these extensions:

**Code:** `.kt`, `.java`, `.py`, `.js`, `.ts`, `.tsx`, `.jsx`, `.go`, `.rs`, `.scala`, `.groovy`, `.kts`

**Config:** `.yaml`, `.yml`, `.json`, `.xml`, `.html`, `.css`, `.sql`, `.properties`, `.toml`, `.gradle`

**Scripts:** `.sh`, `.bash`, `.zsh`

**Docs:** `.md`

Files in `build/`, `out/`, `.gradle/`, `.idea/`, `node_modules/`, `.git/`, and `target/` are automatically skipped.

---

## Requirements

- **IntelliJ IDEA** 2025.3+ (build 253+)
- **JDK 21**
- **Git** installed and available on `PATH`
- One of:
  - GitHub Personal Access Token (with Models:Read permission)
  - Anthropic API key

---

## Project Structure

```
ai-review-plugin/
├── build.gradle.kts                            # Gradle build config
├── settings.gradle.kts                         # Project name
├── gradle.properties                           # Gradle settings
└── src/main/
    ├── kotlin/com/aireview/plugin/
    │   ├── annotator/
    │   │   ├── AiReviewExternalAnnotator.kt    # Core 3-phase annotator lifecycle
    │   │   └── InlineFindingRenderer.kt        # Error Lens-style inline renderer
    │   ├── cache/
    │   │   └── ReviewCacheService.kt           # Thread-safe SHA-256 content cache
    │   ├── claude/
    │   │   ├── ClaudeApiClient.kt              # Anthropic Messages API client
    │   │   └── CopilotApiClient.kt             # GitHub Models API client
    │   ├── git/
    │   │   └── GitDiffService.kt               # Git diff retrieval (unstaged + HEAD)
    │   ├── listener/
    │   │   └── FileSaveListener.kt             # File save listener -> triggers review
    │   ├── model/
    │   │   ├── AiReviewInfo.kt                 # Annotator phase data transfer object
    │   │   └── ReviewFinding.kt                # Finding model + Severity enum
    │   └── settings/
    │       ├── AiReviewConfigurable.kt         # Settings UI panel
    │       └── AiReviewSettings.kt             # Persistent settings + AiProvider enum
    └── resources/META-INF/
        └── plugin.xml                          # Plugin descriptor
```

---

## Development

### Run in sandbox

```bash
./gradlew runIde
```

This launches a sandboxed IntelliJ instance with the plugin loaded. Changes are isolated from your main IDE.

### Build distributable

```bash
./gradlew buildPlugin
```

Output: `build/distributions/ai-review-plugin-1.0.0.zip`

---

## Troubleshooting

| Problem | Solution |
|---------|----------|
| No annotations appearing | Check **Settings -> Tools -> AI Code Review** is enabled |
| "Token not configured" in logs | Add your GitHub token or Anthropic API key in settings |
| No diff detected | The file must have unstaged or uncommitted git changes |
| Annotations stale after edit | Save the file (Cmd+S) to trigger a new review |
| Slow response | Reduce **Max diff lines** or switch to a smaller/faster model |
| Wrong model after switching provider | The model auto-updates; if you customized it, select a new one from the dropdown |

Check the IntelliJ log (**Help -> Show Log in Finder/Explorer**) for diagnostics. The plugin logs under `com.aireview.plugin`.

---

## Tech Stack

| Component | Technology |
|-----------|------------|
| Language | Kotlin 2.1.0 |
| JDK | 21 |
| Build | Gradle + IntelliJ Platform Gradle Plugin 2.11.0 |
| Target IDE | IntelliJ IDEA 2025.3+ |
| AI (default) | GitHub Models (OpenAI-compatible chat completions) |
| AI (alternative) | Claude (Anthropic Messages API v2023-06-01) |
| JSON | Gson 2.11.0 |
| Caching | SHA-256 content hash (in-memory, bounded to 200 entries) |

---

## License

MIT License. See [LICENSE](LICENSE) for details.
