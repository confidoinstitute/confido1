import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import kotlin.text.set

val ktorVersion = "3.3.1"
val serializationVersion = "1.9.0"
val mongoDriverVersion = "5.11.1"
val kotlinWrappersVersion = "2025.10.8"
val ktomlVersion = "0.7.1"

buildscript {
    repositories { mavenCentral() }
    dependencies {
        // Bytecode scanner used by generateSerializationReflectConfig
        classpath("io.github.classgraph:classgraph:4.8.181")
    }
}

plugins {
    kotlin("multiplatform") version "2.2.20"
    kotlin("plugin.serialization") version "2.2.20"
    id("org.graalvm.buildtools.native") version "1.1.3"
    id("com.gradleup.shadow") version "9.2.2"
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
            .map { "tools.confido.extensions.${it.name}.${it.name.toPascalCase()}CE" }

        val jvmExtensions = currentExtDirs.filter { file("${it.path}/jvm").isDirectory }
            .map { "tools.confido.extensions.${it.name}.${it.name.toPascalCase()}SE" }

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
            // Sources live directly under src/<sourceSet>/ (no kotlin/ level)
            kotlin.setSrcDirs(listOf("src/commonMain"))
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
            kotlin.setSrcDirs(listOf("src/jvmMain"))
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
                implementation("org.mongodb:mongodb-driver-kotlin-coroutine:$mongoDriverVersion")
                implementation("org.mongodb:bson-kotlinx:$mongoDriverVersion")
                implementation("com.password4j:password4j:1.6.1")
                implementation("org.simplejavamail:simple-java-mail:7.5.0")
                implementation("com.github.jnr:jnr-unixsocket:0.38.19") // for mongodb unix socket connection (faster)
                implementation("commons-codec:commons-codec:1.15")
                implementation("com.akuleshov7:ktoml-core:$ktomlVersion")
                implementation("com.akuleshov7:ktoml-file:$ktomlVersion")
                implementation("org.apache.commons:commons-csv:1.12.0")
                implementation("com.github.albfernandez:juniversalchardet:2.5.0")
                implementation("io.ktor:ktor-client-core:$ktorVersion")
                implementation("io.ktor:ktor-client-cio:$ktorVersion")
                // PDF to image conversion for presentation slides
                implementation("org.apache.pdfbox:pdfbox:3.0.4")
                // Persistent (immutable) maps for store-layer state snapshots
                implementation("org.jetbrains.kotlinx:kotlinx-collections-immutable:0.3.8")
            }
        }
        val jsMain by getting {
            kotlin.setSrcDirs(listOf("src/jsMain"))
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
                implementation(kotlinw("react-select"))
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

// Prints the jvm target's runtime classpath; used to run the server under plain
// `java`, e.g. with the native-image tracing agent attached.
val printJvmRuntimeClasspath by tasks.registering {
    dependsOn(tasks.named("jvmJar"))
    doLast {
        println(files(tasks.named("jvmJar")).asPath + ":" + configurations["jvmRuntimeClasspath"].asPath)
    }
}

// Systematically derives native-image reflection config for kotlinx.serialization:
// scans the whole jvm runtime classpath for @Serializable classes (the annotation
// has BINARY retention, so this needs a bytecode scanner, not runtime reflection)
// and registers each class plus its Companion / $serializer nested classes.
// These are exactly the members kotlinx.serialization's serializer(KType) lookup
// (used by Ktor content negotiation and the Mongo driver's kotlinx codec) accesses reflectively.
// This makes the config independent of which endpoints the tracing agent happened
// to exercise; the agent-generated config in native-config/ then only needs to
// cover non-serialization reflection (Ktor internals, JNI, resources, ...).
val generateSerializationReflectConfig by tasks.registering {
    dependsOn(tasks.named("jvmJar"))
    val outDir = layout.buildDirectory.dir("generated/native-serialization-config")
    inputs.files(tasks.named("jvmJar"))
    outputs.dir(outDir)
    doLast {
        val cpFiles = files(tasks.named("jvmJar")).files + configurations["jvmRuntimeClasspath"].resolve()
        val entries = mutableListOf<Map<String, Any>>()
        fun entry(name: String) = mapOf(
            "name" to name,
            "allDeclaredConstructors" to true,
            "allDeclaredMethods" to true,
            "allDeclaredFields" to true,
        )
        io.github.classgraph.ClassGraph()
            .overrideClasspath(cpFiles)
            .enableClassInfo()
            .enableAnnotationInfo()
            .scan().use { scan ->
                for (ci in scan.getClassesWithAnnotation("kotlinx.serialization.Serializable")) {
                    entries += entry(ci.name)
                    for (suffix in listOf("\$Companion", "\$\$serializer")) {
                        if (scan.getClassInfo(ci.name + suffix) != null) entries += entry(ci.name + suffix)
                    }
                }
            }
        val out = outDir.get().file("reflect-config.json").asFile
        out.parentFile.mkdirs()
        out.writeText(groovy.json.JsonOutput.prettyPrint(groovy.json.JsonOutput.toJson(entries)))
        println("Registered ${entries.size} reflection entries for kotlinx.serialization")
    }
}

graalvmNative {
    toolchainDetection.set(false)
    binaries {
        maybeCreate("main").apply {
            imageName.set("confido-server")
            mainClass.set("tools.confido.application.ServerKt")
            fallback.set(false)
            verbose.set(true)
            // The KMP plugin does not expose a java-style main source set, so wire
            // the jvm target's jar + runtime classpath explicitly.
            classpath(tasks.named("jvmJar"), configurations.named("jvmRuntimeClasspath"))
            configurationFileDirectories.from(file("native-config"))
            configurationFileDirectories.from(generateSerializationReflectConfig)
            buildArgs.add("-H:+ReportExceptionStackTraces")
            buildArgs.add("--enable-url-protocols=http,https")
            // Charset detection (question import) and PDFBox may request arbitrary
            // charsets at runtime; only a few are bundled by default.
            buildArgs.add("-H:+AddAllCharsets")
            // Must point at a GraalVM JDK (needs the native-image tool).
            javaLauncher.set(extensions.getByType<JavaToolchainService>().launcherFor {
                languageVersion.set(JavaLanguageVersion.of(25))
            })
        }
    }
    metadataRepository {
        enabled.set(true)
    }
}

// The GraalVM buildtools plugin only auto-registers native tasks for java-plugin
// projects, and Kotlin 2.x multiplatform forbids applying the java plugin.
// Register the image build task manually against the binary configured above.
tasks.register("nativeCompile", org.graalvm.buildtools.gradle.tasks.BuildNativeImageTask::class) {
    options.set(graalvmNative.binaries.named("main"))
    dependsOn(tasks.named("jvmJar"), generateSerializationReflectConfig)
    service.set(org.graalvm.buildtools.gradle.NativeImageService.registerOn(project))
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

// ---------------------------------------------------------------------------
// Release targets: self-contained deployment trees under build/release/.
// Each tree holds the server (fat jar or native binary) next to bundle/ and
// static/, so an unpacked release runs with no path configuration — the server
// resolves ./bundle and ./static relative to its working directory.

/** Runnable fat jar of the JVM server (KMP has no java-style main source set,
 *  so the jvm compilation output + runtime classpath are wired explicitly). */
val serverShadowJar by tasks.registering(ShadowJar::class) {
    archiveBaseName.set("confido-server")
    archiveClassifier.set("")
    archiveVersion.set("")
    from(kotlin.targets.getByName("jvm").compilations.getByName("main").output.allOutputs)
    configurations.set(listOf(project.configurations.getByName("jvmRuntimeClasspath")))
    mergeServiceFiles()
    manifest { attributes["Main-Class"] = "tools.confido.application.ServerKt" }
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
    isZip64 = true
}

fun Sync.releaseCommon() {
    group = "distribution"
    // Bundle and assets merge into one static/ dir — the server serves whatever
    // CONFIDO_STATIC_PATH lists, defaulting to ./static in a release tree.
    // FAIL on duplicates so a webpack output name colliding with a checked-in
    // asset is caught at build time instead of shadowing one of them.
    duplicatesStrategy = DuplicatesStrategy.FAIL
    from(tasks.named("jsBrowserDistribution")) { into("static") }
    from("static") { into("static") }
    from("config.example")
    // Operational tooling that must match the migrations compiled into this release.
    from("scripts/downgrade_v3.py") { into("tools") }
    from("scripts/anonymize_db.py") { into("tools") }
}

val releaseJvm by tasks.registering(Sync::class) {
    description = "Assemble a self-contained JVM release tree in build/release/jvm"
    releaseCommon()
    destinationDir = file("build/release/jvm")
    from(serverShadowJar) { rename { "confido-server.jar" } }
}

val releaseNative by tasks.registering(Sync::class) {
    description = "Assemble a self-contained native release tree in build/release/native"
    releaseCommon()
    destinationDir = file("build/release/native")
    // PDFBox pulls in AWT, so native-image emits libawt & co. next to the binary;
    // they must ship with it (see native-config/README.md).
    from(tasks.named("nativeCompile")) { include("confido-server", "*.so") }
}

// Packed releases — currently unused (internal deployments run unpacked,
// public distribution is source-only). Uncomment when needed.
//val releaseJvmTar by tasks.registering(Tar::class) {
//    from(releaseJvm) { into("confido-${project.version}-jvm") }
//    archiveFileName.set("confido-${project.version}-jvm.tar.gz")
//    destinationDirectory.set(file("build/release"))
//    compression = Compression.GZIP
//}
//val releaseNativeTar by tasks.registering(Tar::class) {
//    from(releaseNative) { into("confido-${project.version}-native") }
//    archiveFileName.set("confido-${project.version}-native.tar.gz")
//    destinationDirectory.set(file("build/release"))
//    compression = Compression.GZIP
//}

