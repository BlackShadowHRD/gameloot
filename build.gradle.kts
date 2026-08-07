plugins {
    id("java-library")
    id("com.gradleup.shadow") version "9.6.1"
    id("xyz.jpenilla.run-paper") version "3.0.2"
}

group = "io.github.blackshadowhrd"
version = "0.1.0-SNAPSHOT"
description = "GameLoot per-player loot system for Paper 26.2"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.2.build.+")

    testImplementation("org.junit.jupiter:junit-jupiter:5.12.2")
    testCompileOnly("io.papermc.paper:paper-api:26.2.build.+")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.12.2")
    testRuntimeOnly("org.xerial:sqlite-jdbc:3.49.1.0")
    testRuntimeOnly("io.papermc.paper:paper-api:26.2.build.+")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
}

tasks {
    test {
        useJUnitPlatform()
    }

    build {
        dependsOn(shadowJar)
    }

    runServer {
        // Configure the Minecraft version for our task.
        // This is the only required configuration besides applying the plugin.
        // Your plugin's jar (or shadowJar if present) will be used automatically.
        minecraftVersion("26.2")
        jvmArgs("-Xms2G", "-Xmx2G")
    }

    processResources {
        val props = mapOf("version" to version, "description" to project.description)
        filesMatching("plugin.yml") {
            expand(props)
        }
    }
}
