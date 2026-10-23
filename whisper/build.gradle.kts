plugins {
    id("com.android.library")
}

// whisper.cpp (MIT, vendored in src/main/cpp/whisper.cpp) for offline voice typing. Own module because the app module already uses ndk-build and
// Gradle allows one native build system per module. Nothing here is loaded unless the user picks a Whisper model in Settings.
android {
    namespace = "app.lafz.whisper"
    compileSdk = 37
    ndkVersion = "28.0.13004108"

    defaultConfig {
        minSdk = 21
        ndk {
            abiFilters.clear()
            abiFilters.addAll(listOf("arm64-v8a", "x86_64")) // other ABIs: voice typing with Whisper is reported as unsupported
        }
        externalNativeBuild {
            cmake {
                arguments("-DANDROID_STL=c++_shared", "-DCMAKE_BUILD_TYPE=Release") // optimised even in debug builds: Whisper is unusably slow otherwise
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
