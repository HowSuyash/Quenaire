// Resolves and downloads the toolchain JDK declared in build.gradle.kts when the machine
// does not have it. Without this, a contributor with a different JDK silently builds against
// that one instead -- which is how local builds ended up running on JDK 26 while the project
// and CI both target 21.
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

rootProject.name = "complyai"