pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven {
            url = uri("https://repository.zmkn.com/repository/maven-public/")
            content {
                includeGroup("com.zmkn")
                includeGroup("com.ailingqi")
                includeGroupByRegex("com\\.zmkn\\..+")
                includeGroupByRegex("com\\.ailingqi\\..+")
            }
        }
        google()
        mavenCentral()
    }
}
rootProject.name = "srcc-kotlin"
include(":nacos-kotlin")
project(":nacos-kotlin").name = "nacos-kotlin"
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
