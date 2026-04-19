plugins {
    java
    id("org.springframework.boot") version "3.4.4"
    id("io.spring.dependency-management") version "1.1.7"
    jacoco
}

group = "ia.mahi"
version = "0.1.3-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.ai:spring-ai-bom:1.1.4")
    }
}

dependencies {
    implementation("org.springframework.ai:spring-ai-starter-mcp-server")
    implementation("io.projectreactor:reactor-core")
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.assertj:assertj-core")
    testImplementation("org.mockito:mockito-core")
}

tasks.bootJar {
    archiveFileName = "mahi-mcp-server.jar"
    mainClass = "ia.mahi.MahiMcpApplication"
    doLast {
        copy {
            from(archiveFile)
            into("${projectDir}/../mahi-plugins/mahi")
        }
    }
}

tasks.jar {
    enabled = false
}

tasks.test {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required = true
        html.required = true
    }
}
