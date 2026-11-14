// Desk simulator: drives the real :intelligence engine through scripted
// viewfinder situations on a plain JVM. No phone, emulator or camera needed.
plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":intelligence"))
}

application {
    mainClass.set("com.aiphotographer.simulator.SimulatorKt")
    applicationDefaultJvmArgs = listOf("-Dstdout.encoding=UTF-8")
}
