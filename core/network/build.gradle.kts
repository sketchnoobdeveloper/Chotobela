import java.util.Properties

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.chotobela.core.network"
    compileSdk = 35

    defaultConfig {
        minSdk = 26

        // Supabase credentials are injected from local.properties (never committed).
        // When absent the app runs in DEMO MODE with a seeded local catalog.
        val localProps = Properties().apply {
            rootProject.file("local.properties")
                .takeIf { it.exists() }
                ?.inputStream()
                ?.use { stream -> load(stream) }
        }

        buildConfigField(
            "String",
            "SUPABASE_URL",
            "\"${localProps?.getProperty("supabase.url") ?: ""}\""
        )
        buildConfigField(
            "String",
            "SUPABASE_ANON_KEY",
            "\"${localProps?.getProperty("supabase.key") ?: ""}\""
        )
    }

    buildFeatures {
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(project(":core:common"))

    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.gotrue)
    implementation(libs.supabase.postgrest)
    implementation(libs.supabase.storage)
    implementation(libs.ktor.client.android)

    api(libs.serialization.json)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.coroutines.core)
    implementation(libs.timber)

    testImplementation(libs.junit)
}
