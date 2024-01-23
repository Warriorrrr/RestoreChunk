plugins {
    java
    `maven-publish`
    id("io.papermc.paperweight.userdev") version "1.5.11"
    id("xyz.jpenilla.run-paper") version "2.2.2"
}

repositories {
    mavenLocal()
    maven {
        url = uri("https://papermc.io/repo/repository/maven-public/")
    }
}

dependencies {
    paperweight.paperDevBundle("1.20.4-R0.1-SNAPSHOT")
}

group = "dev.warriorrr"
version = "1.0.1"
java.sourceCompatibility = JavaVersion.VERSION_17

tasks {
    assemble {
        dependsOn(reobfJar)
    }

    runServer {
        minecraftVersion("1.20.4")
    }

    compileJava {
        options.encoding = Charsets.UTF_8.name()
        options.release.set(17)
    }

    javadoc {
        options.encoding = Charsets.UTF_8.name()
    }

    processResources {
        filteringCharset = Charsets.UTF_8.name()

        expand("version" to project.version)
    }
}
