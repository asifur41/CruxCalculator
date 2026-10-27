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

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "CruxCalculator"

include(
    ":app",
    ":core-precision",
    ":core-math",
    ":core-data",
    ":core-ui",
    ":feature-basic",
    ":feature-scientific",
    ":feature-converter",
    ":feature-history"
)
