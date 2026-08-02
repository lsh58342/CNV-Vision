pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Kabeja DXF reader (not on Maven Central)
        maven {
            url = uri("https://logicaldoc.sourceforge.net/maven")
            content {
                includeGroup("org.kabeja")
            }
        }
    }
}

rootProject.name = "CNV"
include(":app")
 