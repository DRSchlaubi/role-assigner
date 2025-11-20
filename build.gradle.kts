plugins {
    kotlin("jvm") version "2.2.21"
    id("org.graalvm.buildtools.native") version "0.11.1"
    application
}

group = "dev.schlaubi"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}
dependencies {
    implementation("dev.kord", "kord-core", "0.17.0")
    implementation("com.github.ajalt.clikt", "clikt", "5.0.3")

    implementation("org.fusesource.jansi", "jansi", "2.4.2")
    implementation("ch.qos.logback", "logback-classic", "1.5.20")
    implementation("io.github.oshai", "kotlin-logging", "7.0.13")
}

application {
    mainClass = "dev.schlaubi.role_assigner.LauncherKt"
    applicationDefaultJvmArgs = listOf("--enable-native-access=ALL-UNNAMED")
}

graalvmNative {
    toolchainDetection = true

    binaries {
        named("main") {
            mainClass = "dev.schlaubi.role_assigner.LauncherKt"
            jvmArgs.add("--enable-native-access=ALL-UNNAMED")

            javaLauncher = javaToolchains.launcherFor {
                vendor = JvmVendorSpec.matching("Oracle Corporation")
            }

            resources {
                includedPatterns.add("logback.xml")
            }
        }
    }
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)

    compilerOptions {
        optIn.addAll("dev.kord.common.annotation.KordExperimental", "kotlin.io.path.ExperimentalPathApi")
    }
}
