plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

fun git(vararg args: String): String =
    providers.exec {
        commandLine("git", "-C", rootProject.projectDir.absolutePath, *args)
        isIgnoreExitValue = true
    }.standardOutput.asText.get().trim()

// versionCode = cantidad de commits: sube solo con cada commit y es lo que compara la app para actualizarse.
val commitCount = git("rev-list", "--count", "HEAD").toIntOrNull() ?: 1

// La versión visible cuenta los APKs publicados: apk=n en version.properties → v<n/100>.<n%100 con dos cifras>.0
// (1 → 0.01.0, 105 → 1.05.0). Se sube en 1 con cada versión nueva, junto con su entrada en news.json.
val apkCount: Int = Regex("""(?m)^apk=(\d+)""").find(rootProject.file("version.properties").readText())?.groupValues?.get(1)?.toInt() ?: 0
val appVersion = "${apkCount / 100}.${"%02d".format(apkCount % 100)}.0"
val apkName = "gappribaldi-v$appVersion.apk"

android {
    namespace = "ar.garibaldi.obd"
    compileSdk = 36

    defaultConfig {
        applicationId = "ar.garibaldi.obd"
        minSdk = 26
        targetSdk = 36
        versionCode = commitCount
        versionName = appVersion
        // Repo de GitHub del que se bajan las actualizaciones (releases) y las novedades (news.json)
        buildConfigField("String", "UPDATE_REPO", "\"nmanuelides/gappribaldi\"")
        // Solo teléfonos reales (ARM): sin las librerías nativas de emuladores x86 el APK pesa la mitad
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
    }
    signingConfigs {
        create("release") {
            // CI y la PC usan la misma clave de release (las actualizaciones tienen que venir firmadas
            // siempre con la misma); sin ella se usa la de debug, que sirve solo para probar.
            val keystore = System.getenv("RELEASE_KEYSTORE")?.let(::file)
            if (keystore != null && keystore.exists()) {
                storeFile = keystore
                storePassword = System.getenv("RELEASE_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("RELEASE_KEY_ALIAS")
                keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
            } else {
                initWith(getByName("debug"))
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += setOf(
                "META-INF/{AL2.0,LGPL2.1}", "META-INF/DEPENDENCIES", "META-INF/LICENSE*",
                "META-INF/NOTICE*", "META-INF/INDEX.LIST", "META-INF/*.kotlin_module",
            )
        }
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.12.00")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.12.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    // API key cifrada (EncryptedSharedPreferences)
    implementation("androidx.security:security-crypto:1.1.0")
    // Traducción en el teléfono de los textos que solo existen en inglés
    implementation("com.google.mlkit:translate:17.0.3")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    testImplementation("org.json:json:20250517")
}

// Cada build de release queda también en dist/ como gappribaldi-v<versión>.apk (lo publica CI).
val distRelease by tasks.registering(Copy::class) {
    from(layout.buildDirectory.file("outputs/apk/release/app-release.apk"))
    into(rootProject.layout.projectDirectory.dir("dist"))
    rename { apkName }
}
tasks.matching { it.name == "assembleRelease" }.configureEach { finalizedBy(distRelease) }
