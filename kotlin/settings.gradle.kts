// Kotlin Multiplatform 实现，独立于根目录的 Java 工程：../gradlew -p kotlin jvmTest
rootProject.name = "pwscore-kotlin"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}
