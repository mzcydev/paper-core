import net.minecrell.pluginyml.bukkit.BukkitPluginDescription

plugins {
    java
    //id("io.papermc.paperweight.userdev") version "2.0.0-beta.19"
    id("net.minecrell.plugin-yml.bukkit") version "0.6.0"
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "dev.mzcy"
version = "1.0.0-SNAPSHOT"
description = "Core plugin framework"

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.extendedclip.com/releases/")
    maven("https://maven.enginehub.org/repo/")
}

dependencies {
    // Paper
    // paperweight.paperDevBundle("1.21.11-R0.1-SNAPSHOT")
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")

    // PlaceholderAPI Integration
    compileOnly("me.clip:placeholderapi:2.12.2")

    // WorldEdit Integration
    compileOnly("com.sk89q.worldedit:worldedit-bukkit:7.3.0")

    // Lombok
    compileOnly("org.projectlombok:lombok:1.18.34")
    annotationProcessor("org.projectlombok:lombok:1.18.34")

    // SnakeYAML (bundled in Paper, but explicit for IDE)
    compileOnly("org.yaml:snakeyaml:2.2")

    // Jackson for JSON config
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.2")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.17.2")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.17.2")

    // Google Guava (bundled in Paper, safe to use)
    compileOnly("com.google.guava:guava:33.2.1-jre")

    // ASM for annotation processing at runtime (for component scanning)
    implementation("org.ow2.asm:asm:9.7")
    implementation("org.ow2.asm:asm-commons:9.7")
}

tasks {
//    assemble {
//        dependsOn(reobfJar)
//    }

    compileJava {
        options.encoding = "UTF-8"
        options.release.set(21)
        options.compilerArgs.addAll(
            listOf(
                "-Xlint:all",
                "-Xlint:-processing",
                "-parameters" // Preserve parameter names for DI
            )
        )
    }

    shadowJar {
        archiveClassifier.set("")
        relocate("com.fasterxml.jackson", "dev.mzcy.core.libs.jackson")
        relocate("org.objectweb.asm", "dev.mzcy.core.libs.asm")

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
    main = "dev.mzcy.core.CorePlugin"
    apiVersion = "1.21"
    version = project.version.toString()
    description = project.description
    authors = listOf("mzcy")
    load = BukkitPluginDescription.PluginLoadOrder.STARTUP
    website = "https://mzcy.dev"

    permissions {
        register("core.admin") {
            description = "Access to all core administrative commands"
            default = BukkitPluginDescription.Permission.Default.OP
        }
    }
}