import org.gradle.api.credentials.HttpHeaderCredentials
import org.gradle.authentication.http.HttpHeaderAuthentication

fun readConfig(
    propertyName: String,
    envName: String,
    defaultValue: String = "",
): String {
    val envValue = System.getenv(envName)
    return providers.gradleProperty(propertyName).orElse(envValue ?: defaultValue).get()
}

val zettleGitHubToken = readConfig(
    propertyName = "LCX_ZETTLE_GITHUB_TOKEN",
    envName = "LCX_ZETTLE_GITHUB_TOKEN",
)

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
        if (zettleGitHubToken.isNotBlank()) {
            maven {
                url = uri("https://maven.pkg.github.com/iZettle/sdk-android")
                credentials(HttpHeaderCredentials::class) {
                    name = "Authorization"
                    value = "Bearer $zettleGitHubToken"
                }
                authentication {
                    create<HttpHeaderAuthentication>("header")
                }
                content {
                    includeGroup("com.zettle.sdk")
                    includeGroupByRegex("com\\.zettle\\.sdk(\\..+)?")
                }
            }
        }
    }
}

rootProject.name = "LCX"
include(
    ":app",
    ":core",
    ":feature:auth",
    ":feature:tickets",
    ":feature:sales",
    ":feature:loyalty",
    ":feature:payments",
    ":feature:printing",
    ":feature:water",
    ":feature:checklist",
    ":feature:cash",
)
