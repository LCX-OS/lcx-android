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

rootProject.name = "LCX"
include(
    ":app",
    ":core",
    ":feature:auth",
    ":feature:tickets",
    ":feature:payments",
    ":feature:printing",
    ":feature:water",
    ":feature:checklist",
    ":feature:cash",
)
