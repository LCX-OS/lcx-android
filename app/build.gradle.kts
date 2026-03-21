import org.gradle.api.GradleException
import java.net.URI
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

fun String.toBuildConfigString(): String =
    "\"${replace("\\", "\\\\").replace("\"", "\\\"")}\""

fun readOptionalConfig(
    propertyName: String,
    envName: String,
): String? {
    val envValue = System.getenv(envName)
    return providers.gradleProperty(propertyName).orNull
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?: envValue?.trim()?.takeIf { it.isNotEmpty() }
}

fun readConfig(
    propertyName: String,
    envName: String,
    defaultValue: String,
): String {
    return readOptionalConfig(propertyName, envName) ?: defaultValue
}

fun readBooleanConfig(
    propertyName: String,
    envName: String,
    defaultValue: Boolean,
): Boolean {
    val raw = readConfig(propertyName, envName, defaultValue.toString()).trim().lowercase()
    return when (raw) {
        "1", "true", "yes", "y", "on" -> true
        "0", "false", "no", "n", "off" -> false
        else -> defaultValue
    }
}

val localKeyPropertiesFile = rootProject.file("key.properties")
val localKeyProperties = Properties().apply {
    if (localKeyPropertiesFile.isFile) {
        localKeyPropertiesFile.inputStream().use(::load)
    }
}

fun readReleaseProperty(
    propertyName: String,
    vararg localFallbackNames: String,
): String? {
    providers.gradleProperty(propertyName).orNull
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.let { return it }

    buildList {
        add(propertyName)
        addAll(localFallbackNames)
    }.forEach { localPropertyName ->
        localKeyProperties.getProperty(localPropertyName)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { return it }
    }

    return System.getenv(propertyName)
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
}

data class ReleaseSigningConfig(
    val storeFilePath: String,
    val storePassword: String,
    val keyAlias: String,
    val keyPassword: String,
)

data class FlavorEnvironmentConfig(
    val flavorName: String,
    val apiBaseUrl: String,
    val notificationsBaseUrl: String,
    val supabaseUrl: String,
    val supabaseAnonKey: String,
    val useRealZettle: Boolean,
    val useRealBrother: Boolean,
    val applicationId: String,
    val requireRealZettle: Boolean = false,
    val requireRealBrother: Boolean = false,
)

fun resolveApplicationId(
    baseApplicationId: String,
    applicationIdSuffix: String,
): String = if (applicationIdSuffix.isBlank()) baseApplicationId else "$baseApplicationId$applicationIdSuffix"

fun String.isPlaceholderConfigValue(): Boolean {
    val normalized = trim().lowercase()
    return normalized.isBlank() ||
        normalized.contains("example.com") ||
        normalized.contains("placeholder") ||
        normalized == "prod-anon-key" ||
        normalized == "change-me" ||
        normalized == "https://prod.supabase.co"
}

val releaseSigningProperties = linkedMapOf(
    "LCX_RELEASE_STORE_FILE" to readReleaseProperty("LCX_RELEASE_STORE_FILE", "storeFile"),
    "LCX_RELEASE_STORE_PASSWORD" to readReleaseProperty("LCX_RELEASE_STORE_PASSWORD", "storePassword"),
    "LCX_RELEASE_KEY_ALIAS" to readReleaseProperty("LCX_RELEASE_KEY_ALIAS", "keyAlias"),
    "LCX_RELEASE_KEY_PASSWORD" to readReleaseProperty("LCX_RELEASE_KEY_PASSWORD", "keyPassword"),
)
val missingReleaseSigningProperties = releaseSigningProperties
    .filterValues { it.isNullOrBlank() }
    .keys
    .toList()
val releaseStoreFile = releaseSigningProperties["LCX_RELEASE_STORE_FILE"]
    ?.let { configuredPath ->
        val candidate = file(configuredPath)
        if (candidate.isAbsolute) {
            candidate
        } else {
            rootProject.file(configuredPath)
        }
    }
val releaseSigningValidationError = when {
    missingReleaseSigningProperties.isNotEmpty() -> buildString {
        appendLine("Android release signing is not configured.")
        appendLine("Provide these properties outside git via ~/.gradle/gradle.properties or ${localKeyPropertiesFile.absolutePath}:")
        missingReleaseSigningProperties.forEach { propertyName ->
            appendLine("- $propertyName")
        }
        appendLine("See ${rootProject.file("docs/android-release.md").absolutePath}")
    }.trimEnd()

    releaseStoreFile == null || !releaseStoreFile.isFile -> buildString {
        appendLine("Android release signing is incomplete.")
        appendLine("LCX_RELEASE_STORE_FILE points to a missing keystore file.")
        appendLine("Expected file: ${releaseStoreFile?.absolutePath ?: "<missing>"}")
        appendLine("See ${rootProject.file("docs/android-release.md").absolutePath}")
    }.trimEnd()

    else -> null
}
val releaseSigningConfig = if (releaseSigningValidationError == null && releaseStoreFile != null) {
    ReleaseSigningConfig(
        storeFilePath = releaseStoreFile.absolutePath,
        storePassword = releaseSigningProperties.getValue("LCX_RELEASE_STORE_PASSWORD").orEmpty(),
        keyAlias = releaseSigningProperties.getValue("LCX_RELEASE_KEY_ALIAS").orEmpty(),
        keyPassword = releaseSigningProperties.getValue("LCX_RELEASE_KEY_PASSWORD").orEmpty(),
    )
} else {
    null
}

val devApiBaseUrl = readConfig(
    propertyName = "LCX_DEV_API_BASE_URL",
    envName = "LCX_DEV_API_BASE_URL",
    defaultValue = "http://10.0.2.2:3000",
)
val devNotificationsBaseUrl = readConfig(
    propertyName = "LCX_DEV_NOTIFICATIONS_BASE_URL",
    envName = "LCX_DEV_NOTIFICATIONS_BASE_URL",
    defaultValue = "http://10.0.2.2:8080",
)
val devSupabaseUrl = readConfig(
    propertyName = "LCX_DEV_SUPABASE_URL",
    envName = "LCX_DEV_SUPABASE_URL",
    defaultValue = "http://10.0.2.2:54321",
)
val devSupabaseAnonKey = readConfig(
    propertyName = "LCX_DEV_SUPABASE_ANON_KEY",
    envName = "LCX_DEV_SUPABASE_ANON_KEY",
    defaultValue = readConfig(
        propertyName = "NEXT_PUBLIC_SUPABASE_ANON_KEY",
        envName = "NEXT_PUBLIC_SUPABASE_ANON_KEY",
        defaultValue = "dev-anon-key",
    ),
)
val devUseRealBrother = readBooleanConfig(
    propertyName = "LCX_DEV_USE_REAL_BROTHER",
    envName = "LCX_DEV_USE_REAL_BROTHER",
    defaultValue = false,
)
val devUseRealZettle = readBooleanConfig(
    propertyName = "LCX_DEV_USE_REAL_ZETTLE",
    envName = "LCX_DEV_USE_REAL_ZETTLE",
    defaultValue = false,
)
val androidApplicationId = readConfig(
    propertyName = "LCX_ANDROID_APPLICATION_ID",
    envName = "LCX_ANDROID_APPLICATION_ID",
    defaultValue = "com.cleanx.lcx",
)
val devApplicationIdSuffix = readConfig(
    propertyName = "LCX_DEV_APPLICATION_ID_SUFFIX",
    envName = "LCX_DEV_APPLICATION_ID_SUFFIX",
    defaultValue = ".dev",
)
val prodApiBaseUrl = readConfig(
    propertyName = "LCX_PROD_API_BASE_URL",
    envName = "LCX_PROD_API_BASE_URL",
    defaultValue = "",
)
val prodNotificationsBaseUrl = readConfig(
    propertyName = "LCX_PROD_NOTIFICATIONS_BASE_URL",
    envName = "LCX_PROD_NOTIFICATIONS_BASE_URL",
    defaultValue = prodApiBaseUrl,
)
val prodSupabaseUrl = readConfig(
    propertyName = "LCX_PROD_SUPABASE_URL",
    envName = "LCX_PROD_SUPABASE_URL",
    defaultValue = "",
)
val prodSupabaseAnonKey = readConfig(
    propertyName = "LCX_PROD_SUPABASE_ANON_KEY",
    envName = "LCX_PROD_SUPABASE_ANON_KEY",
    defaultValue = "",
)
val prodUseRealZettle = readBooleanConfig(
    propertyName = "LCX_PROD_USE_REAL_ZETTLE",
    envName = "LCX_PROD_USE_REAL_ZETTLE",
    defaultValue = true,
)
val prodUseRealBrother = readBooleanConfig(
    propertyName = "LCX_PROD_USE_REAL_BROTHER",
    envName = "LCX_PROD_USE_REAL_BROTHER",
    defaultValue = true,
)
val zettleClientId = readConfig(
    propertyName = "LCX_ZETTLE_CLIENT_ID",
    envName = "LCX_ZETTLE_CLIENT_ID",
    defaultValue = "ac819c6c-482d-411b-bada-43fccd2010bb",
)
val zettleRedirectUrl = readConfig(
    propertyName = "LCX_ZETTLE_REDIRECT_URL",
    envName = "LCX_ZETTLE_REDIRECT_URL",
    defaultValue = "com.cleanx.app://oauth/callback",
)
val zettleApprovedApplicationId = readConfig(
    propertyName = "LCX_ZETTLE_APPROVED_APPLICATION_ID",
    envName = "LCX_ZETTLE_APPROVED_APPLICATION_ID",
    defaultValue = "com.cleanx.app",
)
val brotherAarFile = rootProject.file("feature/printing/libs/BrotherPrintLibrary.aar")
val docsReleasePath = rootProject.file("docs/android-release.md").absolutePath
val prodEnvironmentConfig = FlavorEnvironmentConfig(
    flavorName = "prod",
    apiBaseUrl = prodApiBaseUrl,
    notificationsBaseUrl = prodNotificationsBaseUrl,
    supabaseUrl = prodSupabaseUrl,
    supabaseAnonKey = prodSupabaseAnonKey,
    useRealZettle = prodUseRealZettle,
    useRealBrother = prodUseRealBrother,
    applicationId = resolveApplicationId(androidApplicationId, ""),
    requireRealZettle = true,
    requireRealBrother = true,
)
val zettleRedirectUri = runCatching { URI.create(zettleRedirectUrl) }.getOrNull()
val zettleRedirectScheme = zettleRedirectUri?.scheme ?: ""
val zettleRedirectHost = zettleRedirectUri?.host ?: ""

fun buildEnvironmentValidationError(config: FlavorEnvironmentConfig): String? {
    val propertyPrefix = "LCX_${config.flavorName.uppercase()}"
    val invalidProperties = buildList {
        if (config.apiBaseUrl.isPlaceholderConfigValue()) add("${propertyPrefix}_API_BASE_URL")
        if (config.notificationsBaseUrl.isPlaceholderConfigValue()) add("${propertyPrefix}_NOTIFICATIONS_BASE_URL")
        if (config.supabaseUrl.isPlaceholderConfigValue()) add("${propertyPrefix}_SUPABASE_URL")
        if (config.supabaseAnonKey.isPlaceholderConfigValue()) add("${propertyPrefix}_SUPABASE_ANON_KEY")
    }
    val details = mutableListOf<String>()

    if (invalidProperties.isNotEmpty()) {
        details += buildString {
            appendLine("Provide real values outside git for:")
            invalidProperties.forEach { propertyName ->
                appendLine("- $propertyName")
            }
        }.trimEnd()
    }

    if (config.requireRealZettle && !config.useRealZettle) {
        details += "${propertyPrefix}_USE_REAL_ZETTLE must stay true for the prod flavor."
    }

    if (config.requireRealBrother && !config.useRealBrother) {
        details += "${propertyPrefix}_USE_REAL_BROTHER must stay true for the prod flavor."
    }

    if (config.useRealZettle && config.applicationId != zettleApprovedApplicationId) {
        details += buildString {
            appendLine("Zettle real is enabled but the application id is not approved.")
            appendLine("Resolved applicationId: ${config.applicationId}")
            appendLine("Approved applicationId: $zettleApprovedApplicationId")
            append("Align LCX_ANDROID_APPLICATION_ID / LCX_ZETTLE_APPROVED_APPLICATION_ID.")
        }.trimEnd()
    }

    if (config.useRealBrother && !brotherAarFile.isFile) {
        details += buildString {
            appendLine("Brother real is enabled but the Brother SDK AAR is missing.")
            appendLine("Expected file: ${brotherAarFile.absolutePath}")
            append("Either disable ${propertyPrefix}_USE_REAL_BROTHER or restore the AAR.")
        }.trimEnd()
    }

    return if (details.isEmpty()) {
        null
    } else {
        buildString {
            appendLine("Android ${config.flavorName} environment is not release-ready.")
            details.forEachIndexed { index, detail ->
                if (index > 0) appendLine()
                appendLine(detail)
            }
            appendLine("See $docsReleasePath")
        }.trimEnd()
    }
}

val prodEnvironmentValidationError = buildEnvironmentValidationError(config = prodEnvironmentConfig)

android {
    namespace = "com.cleanx.lcx"
    compileSdk = 36

    defaultConfig {
        applicationId = androidApplicationId
        minSdk = 33
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "API_BASE_URL", "\"http://10.0.2.2:3000\"")
        buildConfigField("String", "NOTIFICATIONS_BASE_URL", "\"http://10.0.2.2:8080\"")
        buildConfigField("String", "SUPABASE_URL", "\"https://placeholder.supabase.co\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"placeholder-anon-key\"")
        buildConfigField("String", "ZETTLE_CLIENT_ID", zettleClientId.toBuildConfigString())
        buildConfigField("String", "ZETTLE_REDIRECT_URL", zettleRedirectUrl.toBuildConfigString())
        buildConfigField(
            "String",
            "ZETTLE_APPROVED_APPLICATION_ID",
            zettleApprovedApplicationId.toBuildConfigString(),
        )
        manifestPlaceholders["zettleRedirectScheme"] = zettleRedirectScheme
        manifestPlaceholders["zettleRedirectHost"] = zettleRedirectHost
    }

    flavorDimensions += "environment"
    productFlavors {
        create("dev") {
            dimension = "environment"
            applicationIdSuffix = devApplicationIdSuffix
            versionNameSuffix = "-dev"
            buildConfigField("String", "API_BASE_URL", devApiBaseUrl.toBuildConfigString())
            buildConfigField("String", "NOTIFICATIONS_BASE_URL", devNotificationsBaseUrl.toBuildConfigString())
            buildConfigField("String", "SUPABASE_URL", devSupabaseUrl.toBuildConfigString())
            buildConfigField("String", "SUPABASE_ANON_KEY", devSupabaseAnonKey.toBuildConfigString())
            buildConfigField("Boolean", "USE_REAL_ZETTLE", devUseRealZettle.toString())
            buildConfigField("Boolean", "USE_REAL_BROTHER", devUseRealBrother.toString())
        }
        create("prod") {
            dimension = "environment"
            buildConfigField("String", "API_BASE_URL", prodApiBaseUrl.toBuildConfigString())
            buildConfigField("String", "NOTIFICATIONS_BASE_URL", prodNotificationsBaseUrl.toBuildConfigString())
            buildConfigField("String", "SUPABASE_URL", prodSupabaseUrl.toBuildConfigString())
            buildConfigField("String", "SUPABASE_ANON_KEY", prodSupabaseAnonKey.toBuildConfigString())
            buildConfigField("Boolean", "USE_REAL_ZETTLE", prodUseRealZettle.toString())
            buildConfigField("Boolean", "USE_REAL_BROTHER", prodUseRealBrother.toString())
        }
    }

    signingConfigs {
        releaseSigningConfig?.let { signing ->
            create("release") {
                storeFile = file(signing.storeFilePath)
                storePassword = signing.storePassword
                keyAlias = signing.keyAlias
                keyPassword = signing.keyPassword
            }
        }
    }

    buildTypes {
        release {
            if (releaseSigningConfig != null) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

val verifyReleaseSigning = tasks.register("verifyReleaseSigning") {
    group = "verification"
    description = "Validates release signing inputs loaded from ~/.gradle/gradle.properties or key.properties."
    doLast {
        releaseSigningValidationError?.let { throw GradleException(it) }
    }
}

val verifyProdConfig = tasks.register("verifyProdConfig") {
    group = "verification"
    description = "Validates the prod build config loaded from Gradle properties or env vars."
    doLast {
        prodEnvironmentValidationError?.let { throw GradleException(it) }
    }
}

val verifyProdReleasePrerequisites = tasks.register("verifyProdReleasePrerequisites") {
    group = "verification"
    description = "Validates the combined prod release prerequisites before assembleProdRelease."
    doLast {
        val validationErrors = buildList {
            prodEnvironmentValidationError?.let(::add)
            releaseSigningValidationError?.let(::add)
        }
        if (validationErrors.isNotEmpty()) {
            throw GradleException(validationErrors.joinToString(separator = "\n\n"))
        }
    }
}

tasks.configureEach {
    if (name == "preProdDebugBuild") {
        dependsOn(verifyProdConfig)
    }
    if (name == "preProdReleaseBuild") {
        dependsOn(verifyProdReleasePrerequisites)
    }
    if (name == "preDevReleaseBuild") {
        dependsOn(verifyReleaseSigning)
    }
}

dependencies {
    // Project modules
    implementation(project(":core"))
    implementation(project(":feature:auth"))
    implementation(project(":feature:tickets"))
    implementation(project(":feature:sales"))
    implementation(project(":feature:loyalty"))
    implementation(project(":feature:payments"))
    implementation(project(":feature:printing"))
    implementation(project(":feature:cash"))
    implementation(project(":feature:water"))
    implementation(project(":feature:checklist"))

    // AndroidX Core
    implementation(libs.androidx.core.ktx)

    // Compose
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.activity)
    debugImplementation(libs.compose.ui.tooling)

    // Navigation
    implementation(libs.navigation.compose)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Room
    implementation(libs.room.runtime)
    ksp(libs.room.compiler)
    implementation(libs.room.ktx)

    // Serialization
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.supabase.postgrest)

    // Coroutines
    implementation(libs.kotlinx.coroutines)

    // Lifecycle
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.viewmodel.ktx)

    // Logging
    implementation(libs.timber)
    implementation(libs.okhttp)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.robolectric)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockwebserver)
    testImplementation(libs.retrofit)
    testImplementation(libs.retrofit.kotlinx.serialization)
    testImplementation(libs.okhttp)
    testImplementation(libs.room.testing)
    testImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
