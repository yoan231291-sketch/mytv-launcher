plugins { id("com.android.application") }

android {
    namespace = "com.yoan.mytv"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.yoan.mytv"
        minSdk = 25
        targetSdk = 34
        versionCode = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
        versionName = "1.0." + (System.getenv("GITHUB_RUN_NUMBER") ?: "0")
        // URL del launcher publicado en GitHub Pages
        buildConfigField("String", "LAUNCHER_URL", "\"https://yoan231291-sketch.github.io/mytv-launcher/\"")
    }
    buildFeatures { buildConfig = true }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Firma automática de GitHub Actions. La UI vive en GitHub Pages, así que el APK casi nunca cambia;
            // si cambia, scripts/firetv-setup.sh reinstala solo.
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_1_8; targetCompatibility = JavaVersion.VERSION_1_8 }
}
