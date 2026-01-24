plugins {
    id("java")
    id("org.jetbrains.intellij.platform") version "2.10.5"
}

group = "net.orekyuu"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()

    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    // IntelliJ Platform
    intellijPlatform {
        intellijIdeaCommunity("2024.3")
        bundledPlugin("com.intellij.java")

        instrumentationTools()
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)
    }

    // MCP SDK
    implementation(platform("io.modelcontextprotocol.sdk:mcp-bom:0.17.2"))
    implementation("io.modelcontextprotocol.sdk:mcp")
    implementation("io.modelcontextprotocol.sdk:mcp-json-jackson2")


    // リアクティブストリーム
    implementation("io.projectreactor:reactor-core:3.6.12")

    // ロギング
    implementation("org.slf4j:slf4j-api:2.0.12")

    // HTTP Server (Jetty)
    implementation("org.eclipse.jetty:jetty-server:11.0.24")
    implementation("org.eclipse.jetty:jetty-servlet:11.0.24")

    // テスト
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.mockito:mockito-core:5.11.0")
    testImplementation("org.mockito:mockito-junit-jupiter:5.11.0")
    // JUnit 4 for IntelliJ Platform test framework
    testImplementation("junit:junit:4.13.2")
    testRuntimeOnly("org.junit.vintage:junit-vintage-engine:5.10.0")
    // AssertJ
    testImplementation("org.assertj:assertj-core:3.26.3")
}

intellijPlatform {
    projectName = "mcp-ide-gateway"

    pluginConfiguration {
        version = "1.0-SNAPSHOT"

        ideaVersion {
            sinceBuild = "243"
            untilBuild = "252.*"
        }
    }
}

tasks {
    withType<JavaCompile> {
        sourceCompatibility = "21"
        targetCompatibility = "21"
        options.encoding = "UTF-8"
    }

    test {
        useJUnitPlatform()
    }
}