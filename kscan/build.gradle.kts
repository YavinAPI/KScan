import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("kscan.kmp.library")
    alias(libs.plugins.dokka)
    alias(libs.plugins.mavenPublish)
}

kotlin {
    androidLibrary {
        namespace = "org.ncgroup.kscan"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    sourceSets {
        androidMain.dependencies {
            implementation(libs.android.mlkitBarcodeScanning)
            implementation(libs.bundles.camera)
        }
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.material.icons.extended)
        }
        jvmMain.dependencies {
            implementation(libs.compose.desktop)
            implementation(libs.javacv)
            implementation(libs.opencv.platform)
            implementation(libs.zxing.core)
            implementation(libs.zxing.javase)
        }
        commonTest.dependencies {
            implementation(libs.compose.ui.test)
            implementation(kotlin("test"))
        }
    }
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()

    pom {
        name.set(project.name)
        description.set("Compose Multiplatform Barcode Scanning Library")
        inceptionYear.set("2024")
        url.set("https://github.com/YavinAPI/KScan/")
        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                distribution.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }

        scm {
            url.set("https://github.com/YavinAPI/KScan/")
            connection.set("scm:git:git://github.com/YavinAPI/KScan.git")
            developerConnection.set("scm:git:ssh://git@github.com/YavinAPI/KScan.git")
        }
    }
}
