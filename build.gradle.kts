import net.minecrell.pluginyml.bukkit.BukkitPluginDescription

plugins {
    java
    id("net.minecrell.plugin-yml.bukkit") version "0.6.0"
    id("com.gradleup.shadow") version "9.4.0"
}

group       = "dev.mzcy"
version     = project.property("version") as String   // reads from gradle.properties
description = "Core plugin framework"

// ── Dev-build detection ───────────────────────────────────────────────────────
// When GitHub Actions runs on the dev branch, GITHUB_REF_NAME = "dev".
// Locally (no CI env), isDev is always false so your local JAR stays clean.
val isCI    = System.getenv("GITHUB_ACTIONS") == "true"
val branch  = System.getenv("GITHUB_REF_NAME") ?: ""
val isDev   = isCI && branch == "dev"
val shortSha = System.getenv("GITHUB_SHA")?.take(7) ?: ""

// Full version string embedded in the JAR name and plugin.yml:
//   main branch  →  1.0.0
//   dev  branch  →  1.0.0-dev+abc123
val fullVersion = if (isDev) "${version}-dev+${shortSha}" else version.toString()

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.extendedclip.com/releases/")
    maven("https://maven.enginehub.org/repo/")
    maven("https://jitpack.io/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")

    compileOnly("me.clip:placeholderapi:2.12.2")
    compileOnly("com.sk89q.worldedit:worldedit-bukkit:7.3.0")

    compileOnly("org.projectlombok:lombok:1.18.34")
    annotationProcessor("org.projectlombok:lombok:1.18.34")

    compileOnly("org.yaml:snakeyaml:2.2")

    compileOnly("net.luckperms:api:5.4")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7.1")

    implementation("com.zaxxer:HikariCP:5.1.0")
    implementation("org.xerial:sqlite-jdbc:3.46.1.3")
    compileOnly("com.mysql:mysql-connector-j:9.0.0")

    implementation("org.mongodb:mongodb-driver-sync:5.2.0")
    implementation("org.redisson:redisson:3.37.0")

    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.2")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.17.2")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.17.2")

    compileOnly("com.google.guava:guava:33.2.1-jre")

    implementation("org.ow2.asm:asm:9.7")
    implementation("org.ow2.asm:asm-commons:9.7")
}

tasks {

    compileJava {
        options.encoding = "UTF-8"
        options.release.set(21)
        options.compilerArgs.addAll(
            listOf(
                "-Xlint:all",
                "-Xlint:-processing",
                "-parameters"
            )
        )
    }

    shadowJar {
        // ── JAR name ─────────────────────────────────────────────────────────
        // main  →  Core-1.0.0.jar
        // dev   →  Core-1.0.0-dev.jar
        archiveBaseName.set("Core")
        archiveVersion.set(version.toString())
        archiveClassifier.set(if (isDev) "dev-${shortSha}" else "")

        // ── Relocation ───────────────────────────────────────────────────────
        relocate("com.fasterxml.jackson", "dev.mzcy.core.libs.jackson")
        relocate("org.objectweb.asm",     "dev.mzcy.core.libs.asm")
        relocate("com.zaxxer.hikari",     "dev.mzcy.core.libs.hikari")
        relocate("org.sqlite",            "dev.mzcy.core.libs.sqlite")
        relocate("org.mongodb",           "dev.mzcy.core.libs.mongodb")
        relocate("org.redisson",          "dev.mzcy.core.libs.redisson")

        mergeServiceFiles()
        minimize {
            exclude(dependency("com.fasterxml.jackson.*:.*"))
        }
    }

    processResources {
        filteringCharset = "UTF-8"
    }
}

bukkit {
    main        = "dev.mzcy.core.CorePlugin"
    apiVersion  = "1.21"
    version     = fullVersion              // plugin.yml gets the full version
    description = project.description
    authors     = listOf("mzcy")
    load        = BukkitPluginDescription.PluginLoadOrder.STARTUP
    website     = "https://mzcy.dev"

    permissions {
        register("core.admin") {
            description = "Access to all core administrative commands"
            default     = BukkitPluginDescription.Permission.Default.OP
        }
    }
}