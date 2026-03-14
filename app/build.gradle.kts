import java.net.URI

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

fun readConfig(
    propertyName: String,
    envName: String,
    defaultValue: String,
): String {
    val envValue = System.getenv(envName)
    return providers.gradleProperty(propertyName).orElse(envValue ?: defaultValue).get()
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
val stagingApplicationIdSuffix = readConfig(
    propertyName = "LCX_STAGING_APPLICATION_ID_SUFFIX",
    envName = "LCX_STAGING_APPLICATION_ID_SUFFIX",
    defaultValue = ".staging",
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
val zettleRedirectUri = runCatching { URI.create(zettleRedirectUrl) }.getOrNull()
val zettleRedirectScheme = zettleRedirectUri?.scheme ?: ""
val zettleRedirectHost = zettleRedirectUri?.host ?: ""

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
        create("staging") {
            dimension = "environment"
            applicationIdSuffix = stagingApplicationIdSuffix
            versionNameSuffix = "-staging"
            buildConfigField("String", "API_BASE_URL", "\"https://staging.lcx.example.com\"")
            buildConfigField("String", "NOTIFICATIONS_BASE_URL", "\"https://staging.lcx.example.com\"")
            buildConfigField("String", "SUPABASE_URL", "\"https://staging.supabase.co\"")
            buildConfigField("String", "SUPABASE_ANON_KEY", "\"staging-anon-key\"")
            buildConfigField("Boolean", "USE_REAL_ZETTLE", "true")
            buildConfigField("Boolean", "USE_REAL_BROTHER", "false")
        }
        create("prod") {
            dimension = "environment"
            buildConfigField("String", "API_BASE_URL", "\"https://lcx.example.com\"")
            buildConfigField("String", "NOTIFICATIONS_BASE_URL", "\"https://lcx.example.com\"")
            buildConfigField("String", "SUPABASE_URL", "\"https://prod.supabase.co\"")
            buildConfigField("String", "SUPABASE_ANON_KEY", "\"prod-anon-key\"")
            buildConfigField("Boolean", "USE_REAL_ZETTLE", "true")
            buildConfigField("Boolean", "USE_REAL_BROTHER", "true")
        }
    }

    buildTypes {
        release {
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
