import java.util.Properties

// Release signing comes from keystore.properties in the project root, which is git-ignored and
// holds storeFile, storePassword, keyAlias and keyPassword. Without it, release builds are simply
// left unsigned.
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "io.github.parthibdebnath.openplayer"
    compileSdk = 36

    defaultConfig {
        // The Play listing is registered under this id. The Kotlin package (namespace) stays
        // io.github.parthibdebnath.openplayer; moving it would touch every source file for nothing.
        applicationId = "com.hyperperformant.openplayer"
        minSdk = 26
        targetSdk = 36
        versionCode = 12
        versionName = "0.1.0-beta.12"
    }

    signingConfigs {
        if (!keystoreProperties.isEmpty) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    // JVM tests touch Media3 metadata classes, which implement Parcelable; android.jar's stubs
    // throw on any call unless told to return defaults.
    testOptions {
        unitTests.isReturnDefaultValues = true
        // The theme render harness (ThemeRenderTest) draws the real UI under Robolectric, which
        // needs the app's resources - fonts above all.
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            it.maxHeapSize = "4g"
            it.systemProperty("robolectric.graphicsMode", "NATIVE")
            // Hardware rendering for captures: Compose draws through RenderNodes, which a software canvas can't.
            it.systemProperty("robolectric.pixelCopyRenderMode", "hardware")
            it.systemProperty("roborazzi.test.record", "true")
            // Where rendered frames go; set with -PthemeRenderOut=/some/dir.
            it.systemProperty("themeRender.out", project.findProperty("themeRenderOut")?.toString() ?: "")
            it.systemProperty("themeRender.only", project.findProperty("themeRenderOnly")?.toString() ?: "")
            it.systemProperty("themeRender.video", project.findProperty("themeRenderVideo")?.toString() ?: "")
            it.systemProperty("themeRender.variants", project.findProperty("themeRenderVariants")?.toString() ?: "")
            // Robolectric downloads its Android runtime from Maven Central; a mirror can stand in.
            project.findProperty("robolectricRepo")?.let { url -> it.systemProperty("robolectric.dependency.repo.url", url.toString()) }
        }
    }

    // The app is English-only, but every library ships its strings in eighty-odd languages, all of
    // which ended up in resources.arsc. Only the system's own dialogs would ever show them.
    androidResources {
        localeFilters += listOf("en")
    }

    // Play reads the dependency list from the bundle (it's encrypted with Play's key, for its SDK
    // warnings). In an APK it's only unreadable bytes - and F-Droid and other stores flag it.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            // Kotlin reflection metadata and coroutine debug-agent data. Nothing in the app uses
            // kotlin-reflect or the debug agent, so these were dead weight in every install.
            excludes += listOf("/kotlin/**", "/META-INF/*.version", "/DebugProbesKt.bin", "/kotlin-tooling-metadata.json")
        }
    }
}

// Room writes each database version's schema here. Kept in version control, so a future schema
// change can be given a migration (and tested against the old schema) instead of losing the
// library on update.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.core.ktx)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.activity.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)
    implementation(libs.media3.common)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.datastore.preferences)
    implementation(libs.documentfile)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
