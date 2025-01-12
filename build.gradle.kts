plugins {
    java
    `maven-publish`
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.13"
    id("xyz.jpenilla.run-paper") version "2.3.0"
}

repositories {
    mavenLocal()
    maven {
        url = uri("https://papermc.io/repo/repository/maven-public/")
    }
}

dependencies {
    paperweight.paperDevBundle("1.21.4-R0.1-SNAPSHOT")
}

paperweight.reobfArtifactConfiguration = io.papermc.paperweight.userdev.ReobfArtifactConfiguration.MOJANG_PRODUCTION

java.sourceCompatibility = JavaVersion.VERSION_21

tasks {
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
