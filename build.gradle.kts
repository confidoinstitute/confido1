
val ktorVersion = "2.1.3"
val serializationVersion = "1.4.1"
val kmongoVersion = "4.7.2"
val kotlinWrappersVersion = "1.0.0-pre.444"

plugins {
    kotlin("multiplatform") version "1.7.21"
    kotlin("plugin.serialization") version "1.7.21"
    application
}

group = "tools.confido"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/kotlinx-html/maven")
    maven("https://repo.kotlin.link")
}

fun kotlinw(target: String): String = "org.jetbrains.kotlin-wrappers:kotlin-$target"

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
                devServer?.open = false
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
                implementation("org.jetbrains.kotlinx:kotlinx-html-jvm:0.8.0")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$serializationVersion")
                implementation("org.slf4j:slf4j-simple:2.0.4")
                implementation("org.litote.kmongo:kmongo-coroutine:$kmongoVersion")
                implementation("org.litote.kmongo:kmongo-coroutine-serialization:$kmongoVersion")
                implementation("com.password4j:password4j:1.6.1")
                implementation("org.simplejavamail:simple-java-mail:7.5.0")
                implementation("com.github.jnr:jnr-unixsocket:0.38.19") // for mongodb unix socket connection (faster)
                implementation("commons-codec:commons-codec:1.15")
            }
        }
        val jsMain by getting {
            dependencies {
                implementation(kotlinw("react"))
                implementation(kotlinw("react-dom"))
                implementation(kotlinw("emotion"))
                implementation(kotlinw("react-router-dom"))
                implementation(kotlinw("mui"))
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$serializationVersion")
                implementation("io.ktor:ktor-client-core:$ktorVersion")
                implementation("io.ktor:ktor-client-serialization:$ktorVersion")
                implementation("io.ktor:ktor-client-websockets:$ktorVersion")
                implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
                implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
                // Plotly
                implementation("space.kscience:plotlykt-core:0.5.0")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")
                implementation(npm("@dnd-kit/core", "6.0.5"))
                implementation(npm("@dnd-kit/modifiers", "6.0.0"))
                implementation(npm("@dnd-kit/sortable", "7.0.1"))
                implementation(npm("qrcode.react", "3.1.0"))
                // Transitions
                implementation(npm("react-transition-group","4.4.5"))
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

dependencies {
    // This specifies the use of the kotlin wrapper BOM to find compatible dependencies. We cannot define
    // enforcedPlatform directly within jsMain dependencies in multiplatform projects, so we do it here instead.
    // See also: https://github.com/JetBrains/kotlin-wrappers/issues/1519
    "jsMainImplementation"(enforcedPlatform(kotlinw("wrappers-bom:$kotlinWrappersVersion")))
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
