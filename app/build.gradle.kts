plugins {
    id("com.android.application")
    id("com.google.gms.google-services")
}

android {
    namespace = "com.example.oztrip"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.oztrip"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        externalNativeBuild {
            cmake {
                cppFlags += ""
            }
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
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    packaging {
        resources {
            excludes += "/META-INF/LICENSE.md"
            excludes += "/META-INF/NOTICE.md"
            excludes += "/META-INF/NOTICE"
            excludes += "/META-INF/LICENSE"
            excludes += "/META-INF/DEPENDENCIES"
        }
    }
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
}
// В файле app/build.gradle.kts
repositories {
    google()
    mavenCentral()
    maven {
        url = uri("https://api.maptiler.com/download/maven/releases/")
        authentication {
            // Явно указываем тип аутентификации
            create<BasicAuthentication>("basic")
        }
        credentials {
            username = "maptiler"
            password = "TnoZocIJI8qZ53VLWXPV"
        }
    }
}
dependencies {
    // Android UI & Core
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation(libs.preference)
    //Gson
    implementation("com.google.code.gson:gson:2.10.1")
    // Firebase (BoM)
    implementation(platform("com.google.firebase:firebase-bom:33.1.0"))
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-storage")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")

// Нативный SDK от MapTiler (включает встроенные функции поиска)
    // Используем открытый MapLibre (он скачается 100% без паролей)
// НАШ МАПЛАЙТ (MapTiler SDK)
// ЗАМЕНИ НА ЭТО (скачается без паролей):

    implementation("org.maplibre.gl:android-sdk:11.0.0")
    implementation("org.maplibre.gl:android-plugin-annotation-v9:1.0.0")


    implementation("androidx.core:core-ktx:1.12.0") // или последняя версия

    // Изображения (Glide)
    implementation("com.github.bumptech.glide:glide:4.16.0")
    annotationProcessor("com.github.bumptech.glide:compiler:4.16.0")
    implementation("com.cloudinary:cloudinary-android:2.5.0")

    // Местоположение (Play Services)
    implementation("com.google.android.gms:play-services-location:21.2.0")

    // --- ROOM DATABASE ---
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    annotationProcessor("androidx.room:room-compiler:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")

    // Gmail & Utils
    implementation("com.sun.mail:android-mail:1.6.6")
    implementation("com.sun.mail:android-activation:1.6.6")

    // Тесты
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}