plugins {
  alias(libs.plugins.ignition.module)
  id("eclipse") // Added for better IDE support with Eclipse & VS Code
  id("com.diffplug.spotless") version "6.25.0"
}

// Determine the Ignition target version (8.1 or 8.3). Default is 8.3.
val ignitionTarget = rootProject.findProperty("ignitionTarget")?.toString() ?: "8.3"

// Auto-append a build-id (YYMMddHH) so every build artifact carries a distinct version
// component. Tagged builds are computed from `git describe --tags --long --dirty`; if
// HEAD is exactly on a tag the tag value is used as-is (no -SNAPSHOT), otherwise the
// nearest tag + "-SNAPSHOT" is used. Either way, ".YYMMddHH" is appended to the value
// used in `moduleVersion`. Canonical reference: drivers/ignition.modules.redis-driver
// in the showcase. Format fits comfortably in int32 (max 99123123).
val now = java.time.LocalDateTime.now()
val buildId = now.format(java.time.format.DateTimeFormatter.ofPattern("YYMMddHH")).toInt()

fun getVersionFromGit(): String {
  return try {
    val process =
        ProcessBuilder("git", "describe", "--tags", "--long", "--dirty")
            .redirectErrorStream(true)
            .start()
    process.waitFor()
    if (process.exitValue() == 0) {
      val describe = process.inputStream.bufferedReader().readText().trim()
      val parts = describe.split("-")
      val tag = parts[0].removePrefix("v")
      val distance = parts.getOrNull(1)?.toIntOrNull() ?: 0
      val isDirty = describe.endsWith("-dirty")
      when {
        distance == 0 && !isDirty -> tag
        else -> "$tag-SNAPSHOT"
      }
    } else {
      findProperty("version")?.toString() ?: "0.0.0-SNAPSHOT"
    }
  } catch (e: Exception) {
    findProperty("version")?.toString() ?: "0.0.0-SNAPSHOT"
  }
}

val baseVersion = getVersionFromGit()
val cleanVersion = baseVersion.replace("-SNAPSHOT", "")
val versionWithBuildId =
    if (baseVersion.endsWith("-SNAPSHOT")) {
      "$cleanVersion.$buildId-SNAPSHOT"
    } else {
      "$cleanVersion.$buildId"
    }

version = versionWithBuildId

println(
    "Prometheus Exporter Module Version: $versionWithBuildId (base: $baseVersion, build: $buildId)")

allprojects {
  apply(plugin = "com.diffplug.spotless")

  spotless {
    java {
      target("src/*/java/**/*.java")
      googleJavaFormat("1.15.0").aosp()
      removeUnusedImports()
      trimTrailingWhitespace()
      endWithNewline()
    }
    kotlinGradle {
      target("*.gradle.kts")
      ktfmt()
      trimTrailingWhitespace()
      endWithNewline()
    }
  }
  // Set the version for all projects. Used in artifact naming and module version
  version = rootProject.version

  // Apply the eclipse plugin to all projects for consistent IDE support
  apply(plugin = "eclipse")
}

subprojects {
  apply(plugin = "jacoco")

  tasks.withType<Test> {
    useJUnitPlatform()
    finalizedBy(tasks.withType<JacocoReport>())
  }

  tasks.withType<JacocoReport> {
    dependsOn(tasks.withType<Test>())
    reports {
      xml.required.set(true)
      html.required.set(true)
    }
  }
}

ignitionModule {
  name.set("Prometheus Metrics Exporter")
  fileName.set("Prometheus-Exporter.modl")
  id.set("dev.bwdesigngroup.prometheus.PrometheusExporter")
  moduleVersion.set(versionWithBuildId)
  license.set("LICENSE.txt")
  moduleDescription.set("Adds Prometheus metrics exporting to Ignition")
  requiredIgnitionVersion.set(if (ignitionTarget == "8.1") "8.1.44" else "8.3.1")

  projectScopes.putAll(
      mapOf(":common" to "GCD", ":gateway" to "G", ":designer" to "D", ":client" to "C"))

  hooks.putAll(
      mapOf(
          "dev.bwdesigngroup.prometheus.gateway.PrometheusExporterGatewayHook" to "G",
          "dev.bwdesigngroup.prometheus.designer.PrometheusDesignerHook" to "D",
          "dev.bwdesigngroup.prometheus.client.PrometheusClientHook" to "C"))

  applyInductiveArtifactRepo.set(true)
  skipModlSigning.set(!findProperty("signModule").toString().toBoolean())
}

tasks.withType<io.ia.sdk.gradle.modl.task.Deploy>().configureEach {
  hostGateway.set(project.findProperty("hostGateway")?.toString() ?: "")
}

val deepClean by
    tasks.registering {
      dependsOn(allprojects.map { "${it.path}:clean" })
      description = "Executes clean tasks and remove node plugin caches."
      doLast { delete(file(".gradle")) }
    }
