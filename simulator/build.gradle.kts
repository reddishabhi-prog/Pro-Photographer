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

// Draws the onboarding showcase and runs each through the real enhancer.
tasks.register<JavaExec>("generateShowcase") {
    group = "showcase"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.aiphotographer.simulator.ShowcaseGeneratorKt")
    systemProperty("java.awt.headless", "true")
    systemProperty("showcase.out", rootProject.file("app/src/main/res/drawable-nodpi").absolutePath)
    systemProperty("showcase.preview", layout.buildDirectory.file("showcase-preview.png").get().asFile.absolutePath)
}
