import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import kotlin.text.set

val ktorVersion = "3.3.1"
val serializationVersion = "1.9.0"
val kmongoVersion = "4.7.2"
val kotlinWrappersVersion = "2025.10.8"

plugins {
    kotlin("multiplatform") version "2.2.20"
    kotlin("plugin.serialization") version "2.2.20"
}

group = "tools.confido"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/kotlinx-html/maven")
    maven("https://repo.kotlin.link")
}

fun kotlinw(target: String): String = "org.jetbrains.kotlin-wrappers:kotlin-$target"

// Extension system configuration
val extensionsDir = file("extensions")
val extensionDirs = extensionsDir.listFiles()?.filter { it.isDirectory }?.sortedBy { it.name } ?: emptyList()

fun String.toPascalCase() = split("_").joinToString("") { it.replaceFirstChar { c -> c.uppercase() } }

val generateExtensionRegistries by tasks.registering {
    val jsOutDir = file("build/generated-src/jsMain/kotlin/tools/confido/extensions")
    val jvmOutDir = file("build/generated-src/jvmMain/kotlin/tools/confido/extensions")

    inputs.dir(extensionsDir).optional()
    outputs.dir(jsOutDir)
    outputs.dir(jvmOutDir)

    doLast {
        val currentExtDirs = extensionsDir.listFiles()?.filter { it.isDirectory }?.sortedBy { it.name } ?: emptyList()

        val jsExtensions = currentExtDirs.filter { file("${it.path}/js").isDirectory }
            .map { "extensions.${it.name}.${it.name.toPascalCase()}CE" }

        val jvmExtensions = currentExtDirs.filter { file("${it.path}/jvm").isDirectory }
            .map { "extensions.${it.name}.${it.name.toPascalCase()}SE" }

        jsOutDir.mkdirs()
        file("$jsOutDir/registry.kt").writeText("""
            |package tools.confido.extensions
            |
            |// AUTO-GENERATED FILE - DO NOT EDIT
            |// Generated from extensions/ directory structure
            |
            |actual val registeredExtensions: List<Extension> = listOf(
            |${jsExtensions.joinToString(",\n") { "    $it" }}
            |)
        """.trimMargin())

        jvmOutDir.mkdirs()
        file("$jvmOutDir/registry.kt").writeText("""
            |package tools.confido.extensions
            |
            |// AUTO-GENERATED FILE - DO NOT EDIT
            |// Generated from extensions/ directory structure
            |
            |actual val registeredExtensions: List<Extension> = listOf(
            |${jvmExtensions.joinToString(",\n") { "    $it" }}
            |)
        """.trimMargin())
    }
}

// https://kotlinlang.org/docs/multiplatform-dsl-reference.html
// https://docs.gradle.org/current/userguide/kotlin_dsl.html
kotlin {
    jvm {
        compilations.all {
            //kotlinOptions.jvmTarget = "17"
        }
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        binaries {
            executable {
                mainClass.set("tools.confido.application.ServerKt")
            }
        }
        //withJava()
    }
    js(IR) {
        binaries.executable()
        compilerOptions {
            target.set("es2015")
        }
        browser {
            commonWebpackConfig {
                configDirectory = projectDir.resolve("webpack-config")
                cssSupport {
                    enabled.set(true)
                }
                devServer?.open = false
                devServer?.port = 8081
            }
        }
    }
    sourceSets {
        val commonMain by getting {
            // Add extension common sources
            kotlin.srcDirs(extensionDirs.mapNotNull { ext ->
                file("extensions/${ext.name}/common").takeIf { it.isDirectory }
            })
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$serializationVersion")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-cbor:$serializationVersion")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.4.0")
                implementation("org.jetbrains:markdown:0.7.3")
            }
        }
        val jvmMain by getting {
            // Add extension JVM sources and generated registry
            kotlin.srcDirs(extensionDirs.mapNotNull { ext ->
                file("extensions/${ext.name}/jvm").takeIf { it.isDirectory }
            })
            kotlin.srcDir("build/generated-src/jvmMain/kotlin")
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
            // Add extension JS sources and generated registry
            kotlin.srcDirs(extensionDirs.mapNotNull { ext ->
                file("extensions/${ext.name}/js").takeIf { it.isDirectory }
            })
            kotlin.srcDir("build/generated-src/jsMain/kotlin")
            dependencies {
                implementation(kotlinw("js"))
                implementation(kotlinw("css"))
                implementation(kotlinw("csstype"))
                implementation(kotlinw("browser"))
                implementation(kotlinw("react"))
                implementation(kotlinw("react-dom"))
                implementation(kotlinw("emotion-css"))
                implementation(kotlinw("emotion-styled"))
                implementation(kotlinw("emotion-react"))
                implementation(kotlinw("react-router"))
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$serializationVersion")
                implementation("io.ktor:ktor-client-core:$ktorVersion")
                implementation("io.ktor:ktor-client-serialization:$ktorVersion")
                implementation("io.ktor:ktor-client-websockets:$ktorVersion")
                implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
                implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
                // Plotly
                implementation("space.kscience:plotlykt-core:0.7.1.1")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
                // DND Kit
                implementation(npm("@dnd-kit/core", "6.0.5"))
                implementation(npm("@dnd-kit/modifiers", "6.0.0"))
                implementation(npm("@dnd-kit/sortable", "7.0.1"))
                // QR codes
                implementation(npm("qrcode.react", "3.1.0"))
                // Transitions
                implementation(npm("react-transition-group","4.4.5"))
                // Helmet
                implementation(npm("react-helmet", "6.1.0"))
                // Milkdown markdown editor
                implementation(npm("@milkdown/crepe", "7.15.3"))
                implementation(npm("@milkdown/kit", "7.15.3"))
                implementation(npm("@milkdown/react", "7.15.3"))
            }
        }
    }
    sourceSets.all {
        languageSettings.apply {
            // needed (at least) for OpenEndRange support
            languageVersion = "2.2"
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

//tasks.named<JavaExec>("run") {
//    dependsOn(tasks.named<Jar>("jvmJar"))
//    classpath(tasks.named<Jar>("jvmJar"))
//}

//tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> { kotlinOptions.languageVersion = "1.8" }

// Ensure extension registries are generated before compilation
tasks.matching { it.name.startsWith("compileKotlin") }.configureEach {
    dependsOn(generateExtensionRegistries)
}
