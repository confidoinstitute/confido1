
val ktorVersion = "2.1.2"
val serializationVersion = "1.4.0"
val kmongoVersion = "4.7.0"

plugins {
    kotlin("multiplatform") version "1.7.20"
    kotlin("plugin.serialization") version "1.7.20"
    application
}

group = "tools.confido"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/kotlinx-html/maven")
    maven("https://repo.kotlin.link")
}

// https://kotlinlang.org/docs/multiplatform-dsl-reference.html
// https://docs.gradle.org/current/userguide/kotlin_dsl.html
kotlin {
    jvm {
        compilations.all {
            kotlinOptions.jvmTarget = "17"
        }
        withJava()
    }
    js(IR) {
        binaries.executable()
        browser {
            commonWebpackConfig {
                configDirectory = projectDir.resolve("webpack-config")
                cssSupport {
                    enabled = true
                }
                devServer?.port = 8081
                devServer?.proxy = mutableMapOf(
                    "/" to mapOf(
                        "target" to "http://localhost:8080/",
                        "secure" to false,
                    ),
                    "/api/**" to mapOf(
                        "target" to "http://localhost:8080/",
                        "secure" to false,
                    ),
                    "/state" to mapOf(
                        "target" to "http://localhost:8080/",
                        "secure" to false,
                        "ws" to true,
                    ),
                )
            }
        }
    }
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$serializationVersion")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-cbor:$serializationVersion")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.4.0")
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation("io.ktor:ktor-server-cio:$ktorVersion")
                implementation("io.ktor:ktor-server-html-builder-jvm:$ktorVersion")
                implementation("io.ktor:ktor-server-websockets:$ktorVersion")
                implementation("io.ktor:ktor-server-call-logging:$ktorVersion")
                implementation("io.ktor:ktor-serialization:$ktorVersion")
                implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
                implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
                implementation("org.jetbrains.kotlinx:kotlinx-html-jvm:0.7.2")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$serializationVersion")
                implementation("org.slf4j:slf4j-simple:2.0.0")
                implementation("org.litote.kmongo:kmongo-coroutine:$kmongoVersion")
                implementation("org.litote.kmongo:kmongo-coroutine-serialization:$kmongoVersion")
                implementation("com.password4j:password4j:1.6.1")
                implementation("org.simplejavamail:simple-java-mail:7.5.0")
                implementation("com.github.jnr:jnr-unixsocket:0.38.17") // for mongodb unix socket connection (faster)
                implementation("commons-codec:commons-codec:1.15")
            }
        }
        val jsMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlin-wrappers:kotlin-react:18.0.0-pre.332-kotlin-1.6.21")
                implementation("org.jetbrains.kotlin-wrappers:kotlin-react-dom:18.0.0-pre.332-kotlin-1.6.21")
                implementation("org.jetbrains.kotlin-wrappers:kotlin-emotion:11.9.0-pre.332-kotlin-1.6.21")
                implementation("org.jetbrains.kotlin-wrappers:kotlin-react-router-dom:6.3.0-pre.332-kotlin-1.6.21")
                implementation("org.jetbrains.kotlin-wrappers:kotlin-mui:5.6.2-pre.332-kotlin-1.6.21")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$serializationVersion")
                implementation("io.ktor:ktor-client-core:$ktorVersion")
                implementation("io.ktor:ktor-client-serialization:$ktorVersion")
                implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
                implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
                // Plotly
                implementation("space.kscience:plotlykt-core:0.5.0")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.5.1")
            }
        }
    }
    sourceSets.all {
        languageSettings.apply {
            // needed (at least) for OpenEndRange support
            languageVersion = "1.8"
            optIn("kotlin.ExperimentalStdlibApi")
        }
    }
}

application {
    mainClass.set("tools.confido.application.ServerKt")
}

// FŠ: XXX weirdly needed to fix hot restart
// Otherwise we get a warning:
//   Execution optimizations have been disabled for task ':jsBrowserDevelopmentRun'
//   to ensure correctness due to the following reasons:
//   - Gradle detected a problem with the following location:
//   '.../confido1/build/js/packages/confido1/kotlin/confido1.js'.
// Reason: Task ':jsBrowserDevelopmentRun' uses this output of task
// ':jsDevelopmentExecutableCompileSync' without declaring an explicit
// or implicit dependency. This can lead to incorrect results being
// produced, depending on what order the tasks are executed.
//   Please refer to
//      https://docs.gradle.org/7.5/userguide/validation_problems.html#implicit_dependency
//   for more details about this problem.
val jsBrowserDevelopmentRun by tasks.getting
val jsDevelopmentExecutableCompileSync by tasks.getting
jsBrowserDevelopmentRun.dependsOn(jsDevelopmentExecutableCompileSync)
val jsBrowserProductionRun by tasks.getting
val jsProductionExecutableCompileSync by tasks.getting
jsBrowserProductionRun.dependsOn(jsProductionExecutableCompileSync)

tasks.named<JavaExec>("run") {
    dependsOn(tasks.named<Jar>("jvmJar"))
    classpath(tasks.named<Jar>("jvmJar"))
}

//tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> { kotlinOptions.languageVersion = "1.8" }
