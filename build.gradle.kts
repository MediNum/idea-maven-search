plugins {
    id("java")
    id("org.jetbrains.intellij") version "1.17.4"
}

group = "com.dsh"
version = "2.1.0"

repositories {
    mavenCentral()
}

java {
    // 平台 jar 为 Java 25（class v69）编译，编译插件需要同版本 JDK
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

// 源码与资源目录沿用本地布局（src/ 与 META-INF/），而非 Gradle 默认的 src/main/*
sourceSets {
    main {
        java.setSrcDirs(listOf("src"))
        resources {
            // 从仓库根取 META-INF/**，确保 plugin.xml 落在 jar 的 META-INF/plugin.xml
            srcDir(".")
            include("META-INF/**")
        }
    }
}

intellij {
    version.set("2026.1")
    type.set("IC") // IntelliJ IDEA Community
}

tasks {
    patchPluginXml {
        sinceBuild.set("261.0")
        version.set("2.1.0")
    }
    buildSearchableOptions {
        enabled = false
    }
    // JetBrains Marketplace 发布：gradle publishPlugin（token 从环境变量读取，勿提交）
    publishPlugin {
        token.set(System.getenv("JB_MARKETPLACE_TOKEN") ?: "")
        channels.set(listOf("default"))
    }
}
