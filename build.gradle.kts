plugins {
    id("java")
    id("org.jetbrains.intellij") version "1.17.4"
}

group = "com.dsh"
version = "1.5.6"

repositories {
    mavenCentral()
}

java {
    // 平台 jar 为 Java 25（class v69）编译，编译插件需要同版本 JDK
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

intellij {
    version.set("2026.1")
    type.set("IC") // IntelliJ IDEA Community
}

tasks {
    patchPluginXml {
        sinceBuild.set("261.0")
        version.set("1.5.6")
    }
    buildSearchableOptions {
        enabled = false
    }
}
