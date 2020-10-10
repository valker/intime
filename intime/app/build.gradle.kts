plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("android.extensions")
}

android {
    compileSdkVersion(30)
    buildToolsVersion("30.0.2")

    defaultConfig {
        applicationId = "com.vpe_soft.intime.intime"
        minSdkVersion(23)
        targetSdkVersion(30)
        versionCode = 13
        versionName = "1.1.0"
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android.txt"), "proguard-rules.pro")
        }
    }
}

dependencies {
    google("material", "1.2.1")
    androidx("appcompat", "1.2.0")
    androidx("recyclerview", "1.1.0")
    androidx("coordinatorlayout", "1.1.0")
    androidx("cardview")
    androidx("constraintlayout", "2.0.1")
    ktx("core", "1.3.2")
}

fun DependencyHandlerScope.ktx(name: String, version: String) =
    implementation("androidx.$name:$name-ktx:$version")

fun DependencyHandlerScope.google(name: String, version: String) =
    implementation("com.google.android.$name:$name:$version")

fun DependencyHandlerScope.androidx(name: String, version: String = "1.0.0") =
    implementation("androidx.$name:$name:$version")