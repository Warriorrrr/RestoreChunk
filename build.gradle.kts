plugins {
    id("java")
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.22"
    id("xyz.jpenilla.run-paper") version "3.0.2"
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    paperweight.paperDevBundle("${libs.versions.minecraft.get()}.build.+")
}

paperweight.reobfArtifactConfiguration = io.papermc.paperweight.userdev.ReobfArtifactConfiguration.MOJANG_PRODUCTION

java.sourceCompatibility = JavaVersion.VERSION_25

tasks {
    runServer {
        minecraftVersion(libs.versions.minecraft.get())
    }

    jar {
        archiveClassifier = libs.versions.minecraft.get()
    }

    compileJava {
        options.encoding = Charsets.UTF_8.name()
        options.release.set(25)
    }

    processResources {
        filteringCharset = Charsets.UTF_8.name()

        expand(
            "version" to "${project.version}+${libs.versions.minecraft.get()}",
            "minecraft_version" to libs.versions.minecraft.get()
        )
    }
}
