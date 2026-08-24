plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKmpLibrary)
    alias(libs.plugins.androidLint)
    alias(libs.plugins.kotlinSerialization)
    id("uangku.maven-publish")
}

kotlin {

    // Target declarations - add or remove as needed below. These define
    // which platforms this KMP module supports.
    // See: https://kotlinlang.org/docs/multiplatform-discover-project.html#targets
    androidLibrary {
        namespace = "com.oratakashi.uangku.core.libs.core_crypto"
        compileSdk = 36
        minSdk = 24

        withHostTestBuilder {
        }

        withDeviceTestBuilder {
            sourceSetTreeName = "test"
        }.configure {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
    }

    // For iOS targets, this is also where you should
    // configure native binary output. For more information, see:
    // https://kotlinlang.org/docs/multiplatform-build-native-binaries.html#build-xcframeworks

    // A step-by-step guide on how to include this library in an XCode
    // project can be found here:
    // https://developer.android.com/kotlin/multiplatform/migrate
    val xcfName = "libs:core_cryptoKit"

    iosX64 {
        binaries.framework {
            baseName = xcfName
        }
    }

    iosArm64 {
        binaries.framework {
            baseName = xcfName
        }
    }

    iosSimulatorArm64 {
        binaries.framework {
            baseName = xcfName
        }
    }

    // Source set declarations.
    // Declaring a target automatically creates a source set with the same name. By default, the
    // Kotlin Gradle Plugin creates additional source sets that depend on each other, since it is
    // common to share sources between related targets.
    // See: https://kotlinlang.org/docs/multiplatform-hierarchy.html
    sourceSets {
        commonMain {
            dependencies {
                implementation(libs.kotlin.stdlib)
                api(project.dependencies.platform(libs.koin.bom))
                api(libs.bundles.koin)
                implementation(libs.kotlinx.coroutines.core)
                // api (not implementation): guards the cryptography-kotlin provider
                // @EagerInitialization registration hook against Kotlin/Native DCE.
                api(libs.bundles.cryptography)
                implementation(libs.kotlinx.serialization.json)
            }
        }

        commonTest {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.coroutines.test)
            }
        }

        androidMain {
            dependencies {
                implementation(libs.kotlinx.coroutines.android)
            }
        }

        getByName("androidDeviceTest") {
            dependencies {
                implementation(libs.androidx.testExt.junit)
                implementation(libs.androidx.runner)
            }
        }

        iosMain {
            dependencies {
                // iOS dependencies are provided by the native SDK
            }
        }
    }

}

// Gradle doesn't stream a test task's captured stdout/stderr to the CI console by default
// (Kotlin/Native's iosSimulatorArm64Test included, since KotlinNativeTest extends AbstractTestTask
// and follows the same testLogging defaults as the JVM Test task). Without this, a test that
// diagnoses itself via println is invisible in CI.
tasks.withType<org.gradle.api.tasks.testing.AbstractTestTask>().configureEach {
    testLogging {
        showStandardStreams = true
    }
}

// The simulator test task defaults to `standalone = true`, which runs the binary as
// `xcrun simctl spawn --standalone <device> test.kexe` — never bootstrapped into the simulator's
// launchd, so the process cannot reach system services at all. Opting out is the correct
// configuration for any test that talks to the simulator; it requires a booted device, which CI
// boots before invoking this task. Note this does not by itself grant Keychain access: the test
// binary still carries no entitlement — see the KDoc on KeychainSecureStorage.
tasks.withType<org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeSimulatorTest>()
    .configureEach {
        standalone.set(false)
        device.set("booted")
    }