plugins {
    // 允许 Gradle 自动下载 Java toolchain（编译平台 jar 需要 JDK 25）
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"
}

rootProject.name = "maven-search"
