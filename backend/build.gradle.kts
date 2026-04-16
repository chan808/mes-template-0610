import org.gradle.kotlin.dsl.implementation
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.gradle.api.tasks.testing.Test

plugins {
    kotlin("jvm") version "2.2.21"
    kotlin("plugin.spring") version "2.2.21"
    kotlin("plugin.jpa") version "2.2.21"
    id("org.springframework.boot") version "4.0.3"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "io.github.chan808"
version = "0.0.1-SNAPSHOT"
description = "backend"

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
        mavenBom("org.springframework.modulith:spring-modulith-bom:2.0.3")
    }
}

val jjwtVersion = "0.12.6"
val archUnitVersion = "1.4.0"

dependencies {
    // Modulith
    implementation("org.springframework.modulith:spring-modulith-starter-core")

    // Web & Security
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // Data
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")

    // Kotlin / Jackson 3 (Boot 4.x 기본)
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("tools.jackson.module:jackson-module-kotlin")
    implementation("io.micrometer:micrometer-registry-prometheus")

    // JWT: jjwt-impl/jackson은 구현 교체 가능성을 위해 runtimeOnly 격리
    implementation("io.jsonwebtoken:jjwt-api:$jjwtVersion")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:$jjwtVersion")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:$jjwtVersion")

    // Database
    runtimeOnly("com.mysql:mysql-connector-j")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.flywaydb:flyway-mysql")

    // API Docs (Boot 4.x 대응 버전)
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.1")

    // Mail
    implementation("org.springframework.boot:spring-boot-starter-mail")

    // OAuth2
    implementation("org.springframework.boot:spring-boot-starter-oauth2-client")

    // Dev
    developmentOnly("org.springframework.boot:spring-boot-devtools")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    // Test
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
    testImplementation("org.springframework.boot:spring-boot-starter-security-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    // mockk: Kotlin null-safety 보장 + 코루틴 지원 (Mockito 대비 이점)
    testImplementation("io.mockk:mockk:1.14.2")
    // springmockk: Spring 컨텍스트 내에서 @MockkBean 사용 가능하게 해주는 브릿지
    testImplementation("com.ninja-squad:springmockk:4.0.2")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-mysql")
    testImplementation("org.testcontainers:testcontainers") // GenericContainer (Redis 등)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // Modulith test
    testImplementation("org.springframework.modulith:spring-modulith-starter-test")

    // ArchUnit
    testImplementation("com.tngtech.archunit:archunit-junit5:$archUnitVersion")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
        jvmTarget = JvmTarget.JVM_21
    }
}

// JPA Entity는 Kotlin final class 기본값으로 인해 프록시 생성 불가 → allOpen 적용 필수
allOpen {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("jakarta.persistence.Embeddable")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

tasks.named<Test>("test") {
    useJUnitPlatform {
        excludeTags("integration")
    }
}

tasks.register<Test>("integrationTest") {
    description = "Runs integration tests that require external services such as Testcontainers."
    group = "verification"
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    shouldRunAfter(tasks.named("test"))
    useJUnitPlatform {
        includeTags("integration")
    }
}
