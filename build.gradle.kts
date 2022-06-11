plugins {
    java
    `maven-publish`
    id("io.papermc.paperweight.userdev") version "1.3.6"
}

repositories {
    mavenLocal()
    maven {
        url = uri("https://papermc.io/repo/repository/maven-public/")
    }

    maven {
        url = uri("https://repo.maven.apache.org/maven2/")
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.18.2-R0.1-SNAPSHOT")
    paperDevBundle("1.18.2-R0.1-SNAPSHOT")
}

group = "net.earthmc"
version = "0.0.1"
java.sourceCompatibility = JavaVersion.VERSION_17

tasks {
    build {
        dependsOn(reobfJar)
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

tasks.withType<JavaCompile>() {
    options.encoding = Charsets.UTF_8.name()
}
