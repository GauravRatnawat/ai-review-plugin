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
        intellijIdea("2025.3.3")
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

    signPlugin {
        certificateChain.set(providers.environmentVariable("CERTIFICATE_CHAIN"))
        privateKey.set(providers.environmentVariable("PRIVATE_KEY"))
        password.set(providers.environmentVariable("PRIVATE_KEY_PASSWORD"))
    }

    publishPlugin {
        token.set(providers.environmentVariable("PUBLISH_TOKEN"))
    }
}
