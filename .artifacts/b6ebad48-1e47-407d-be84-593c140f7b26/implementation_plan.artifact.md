# Fix 'kotlinOptions' Unresolved Reference in AGP 9.0

The project is using Android Gradle Plugin (AGP) 9.0.1, which introduces **built-in Kotlin support**. In this version, the `kotlin-android` plugin is no longer required, and the `android.kotlinOptions {}` DSL has been replaced by the `kotlin.compilerOptions {}` DSL or is automatically handled by `android.compileOptions`.

The error `Unresolved reference 'kotlinOptions'` occurs because the `kotlin-android` plugin is not applied, and even if it were, AGP 9.0 prefers the new built-in Kotlin configuration.

## Proposed Changes

### Build Configuration

#### [MODIFY] [app/build.gradle.kts](file:///C:/PassFlow/passenger_flow_app/app/build.gradle.kts)
- Remove the `kotlinOptions {}` block from the `android {}` section.
- Since `jvmTarget = "17"` is already covered by `targetCompatibility = JavaVersion.VERSION_17` in AGP 9.0's built-in Kotlin, no explicit `jvmTarget` setting is needed in the Kotlin block.

#### [MODIFY] [build.gradle.kts](file:///C:/PassFlow/passenger_flow_app/build.gradle.kts) (Root)
- Remove the `org.jetbrains.kotlin.android` plugin declaration, as it is no longer required with AGP 9.0 built-in Kotlin support.

## Verification Plan

### Automated Tests
- Run `./gradlew build` to ensure the project syncs and compiles correctly.
- Verify that Kotlin source files are compiled without errors.

### Manual Verification
- Trigger a Gradle Sync in Android Studio and ensure the 'Unresolved reference' error is resolved.
