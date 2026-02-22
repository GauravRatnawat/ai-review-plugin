plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.1.0"
    id("org.jetbrains.intellij.platform") version "2.11.0"
}

group = "com.aireview"
version = "1.0.0"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}


dependencies {
    intellijPlatform {
        local(file("${System.getProperty("user.home")}/Applications/IntelliJ IDEA.app"))
        bundledPlugin("com.intellij.java")
    }

    implementation("com.google.code.gson:gson:2.11.0")
}

kotlin {
    jvmToolchain(21)
}

tasks {
    patchPluginXml {
        sinceBuild.set("253")
    }

    buildSearchableOptions {
        enabled = false
    }

    // Disable incompatible host plugins in the sandbox to prevent NoClassDefFoundError
    prepareSandbox {
        doLast {
            val configDir = intellijPlatform.sandboxContainer.get().asFile
                .resolve("config")
            configDir.mkdirs()
            val disabledPlugins = configDir.resolve("disabled_plugins.txt")
            val existing = if (disabledPlugins.exists()) disabledPlugins.readText() else ""
            val toDisable = listOf("com.github.copilot")
            val missing = toDisable.filter { it !in existing }
            if (missing.isNotEmpty()) {
                disabledPlugins.appendText(missing.joinToString("\n", prefix = "\n"))
            }
        }
    }
}
