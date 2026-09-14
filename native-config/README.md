# GraalVM native image build

The server can be compiled into a standalone native binary with GraalVM
`native-image`. This eliminates JVM warmup and significantly reduces memory
usage compared to running on the JVM.

## Requirements

- A GraalVM JDK (tested with Oracle GraalVM 25) with the `native-image` tool.
  The build task looks up a Java 25 toolchain via Gradle toolchains, so either
  install GraalVM where Gradle auto-detects it (e.g. `/usr/lib/jvm/`) or point
  `JAVA_HOME`/`org.gradle.java.installations.paths` at it.

## Building

```bash
./gradlew nativeCompile          # produces build/native/nativeCompile/confido-server
./gradlew nativeCompile --rerun  # force rebuild (config changes are not tracked as inputs)
```

Notes:

- The Kotlin 2.x multiplatform plugin forbids applying the `java`/`application`
  plugin, so `build.gradle.kts` registers the `nativeCompile` task manually
  against the `graalvmNative` binary config instead of relying on the GraalVM
  buildtools plugin's automatic java-project integration. A side effect is that
  changes to `native-config/` do not invalidate the task — use `--rerun` after
  editing the config.
- PDFBox (presentation slide rendering) pulls in AWT. The build therefore
  emits a set of `lib*.so` files (libawt, libfontmanager, ...) next to the
  binary. **These must be deployed alongside `confido-server`** (same
  directory); slide rendering also needs fontconfig + fonts installed on the
  host.
- The MongoDB driver ships its own native-image metadata inside the jar and is
  picked up automatically.

## Reflection configuration — two sources

GraalVM needs to know up front which classes are accessed reflectively. The
build feeds it two configs:

1. **Generated: kotlinx.serialization lookup surface** (automatic, no
   maintenance). The `generateSerializationReflectConfig` task in
   `build.gradle.kts` bytecode-scans the whole jvm runtime classpath for
   `@Serializable` classes and registers each class plus its `Companion` and
   `$serializer` nested classes. These are exactly the members that
   `serializer(KType)` — used by Ktor content negotiation and the driver's kotlinx codec —
   accesses reflectively. Serialization itself runs through compiled
   serializers and needs no reflection, only the lookup does; that is why no
   instances ever need to be constructed. This regenerates on every build, so
   **new `@Serializable` classes are covered automatically**.

2. **Traced: everything else** (`reachability-metadata.json` in this
   directory). Non-serialization reflection: Ktor CIO selector internals,
   Mongo driver internals, kotlin.reflect metadata, JNI, resources. This part
   is library-level and stable — it only needs regenerating when upgrading or
   adding libraries, not when adding app features.

The traced config was generated with the native-image **tracing agent**: run
the server on a normal JVM with the agent attached, exercise as much
functionality as possible, and the agent records every reflective access:

```bash
CP=$(./gradlew -q printJvmRuntimeClasspath | tail -1)
java -agentlib:native-image-agent=config-merge-dir=native-config,config-write-period-secs=5 \
     -cp "$CP" tools.confido.application.ServerKt
# ... log in, create rooms/questions of all types, predict, comment,
# open the web UI (websocket state), export CSV, upload presentation slides,
# use enabled extensions, then stop the server ...
```

**Staleness**: new `@Serializable` classes are picked up automatically by the
scanner task, which removes the most common way for the config to go stale.
The traced config can still go stale when *libraries* are added or upgraded —
in that case re-run the agent, exercise the affected feature, and rebuild.
A missed entry shows up as `MissingReflectionRegistrationError` at runtime
(with instructions naming the exact entry to add), so smoke-test the binary
(e.g. with `exercise.py`) before deploying.

A convenient way to exercise most of the API in one go is the driver script in
this directory (needs `pip install websockets`; server must run with
`CONFIDO_DEVMODE=1`, `CONFIDO_MAIL_DEBUG=1` on port 8123 against an empty DB,
then `GET /init/` once):

```bash
python3 native-config/exercise.py
```

Known manual additions (lost by the agent due to a trace-processing quirk,
re-add if regenerating from scratch):

- `io.ktor.network.selector.InterestSuspensionsMap` with all four
  `*HandlerReference` fields listed explicitly — Ktor CIO creates
  `AtomicReferenceFieldUpdater`s in a class initializer at runtime; without
  this the server crashes on the first socket bind. (The unified
  `reachability-metadata.json` schema has no `allDeclaredFields` wildcard, the
  fields must be listed one by one.)

## Runtime

The binary behaves like the JVM server (same env vars, same MongoDB replica
set requirement). Rough comparison measured on demo data (2026-07, GraalVM 25,
this branch): see `docs/graalvm-native.md`.
