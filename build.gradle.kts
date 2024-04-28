plugins {
    java
    `maven-publish`
    id("io.papermc.paperweight.userdev") version "1.6.0"
    id("xyz.jpenilla.run-paper") version "2.2.4"
}

repositories {
    mavenLocal()
    maven {
        url = uri("https://papermc.io/repo/repository/maven-public/")
    }
}

dependencies {
    paperweight.paperDevBundle("1.20.5-R0.1-SNAPSHOT")
}

paperweight.reobfArtifactConfiguration = io.papermc.paperweight.userdev.ReobfArtifactConfiguration.MOJANG_PRODUCTION

group = "dev.warriorrr"
version = "1.0.2"
java.sourceCompatibility = JavaVersion.VERSION_21

tasks {
    runServer {
        minecraftVersion("1.20.5")
    }

    compileJava {
        options.encoding = Charsets.UTF_8.name()
        options.release.set(21)
    }

    javadoc {
        options.encoding = Charsets.UTF_8.name()
    }

    processResources {
        filteringCharset = Charsets.UTF_8.name()

        expand("version" to project.version)
    }
}
