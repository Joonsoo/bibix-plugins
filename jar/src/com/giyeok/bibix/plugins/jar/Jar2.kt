package com.giyeok.bibix.plugins.jar

import com.giyeok.bibix.base.*
import kotlinx.metadata.jvm.JvmMetadataVersion
import kotlinx.metadata.jvm.KmModule
import kotlinx.metadata.jvm.KotlinModuleMetadata
import kotlinx.metadata.jvm.UnstableMetadataApi
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.*
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import kotlin.io.path.*

class Jar2 {
  private val entries = mutableMapOf<String, MutableList<Path>>()

  private fun traverseJar(jarPath: Path) {
    JarInputStream(jarPath.inputStream().buffered()).use { jis ->
      var entry = jis.nextEntry
      while (entry != null) {
        if (!entry.isDirectory) {
          entries.getOrPut(entry.name, { mutableListOf() }).add(jarPath)
        }
        entry = jis.nextEntry
      }
      jis.closeEntry()
    }
  }

  private fun traverseDirectory(dirPath: Path) {
    Files.walk(dirPath).forEach { subPath ->
      if (subPath.isRegularFile()) {
        val entryName = subPath.relativeTo(dirPath).invariantSeparatorsPathString
        entries.getOrPut(entryName, { mutableListOf() }).add(dirPath)
      }
    }
  }

  private fun collectFileEntries(paths: List<Path>) {
    paths.forEach { path ->
      if (path.isRegularFile()) {
        traverseJar(path)
      } else {
        traverseDirectory(path)
      }
    }
  }

  private val zipFilesMap = mutableMapOf<Path, ZipFile>()

  private fun inputStreamOf(source: Path, entryName: String): InputStream {
    return if (source.isRegularFile()) {
      val zipFile = zipFilesMap.getOrPut(source) { ZipFile(source.toFile()) }
      zipFile.getInputStream(ZipEntry(entryName))
    } else {
      source.resolve(entryName).inputStream().buffered()
    }
  }

  private fun closeJarInputStreams() {
    zipFilesMap.values.forEach { it.close() }
  }

  private fun createJar(
    cps: List<Path>,
    destFile: Path,
    manifest: Manifest,
    entryTransformer: (String, List<Pair<Path, InputStream>>, JarOutputStream) -> Unit
  ) {
    collectFileEntries(cps)

    // reference: https://github.com/johnrengelman/shadow/blob/master/src/main/groovy/com/github/jengelman/gradle/plugins/shadow/ShadowJavaPlugin.groovy
    // 'META-INF/INDEX.LIST', 'META-INF/*.SF', 'META-INF/*.DSA', 'META-INF/*.RSA', 'module-info.class'
    entries.keys.removeIf { entryName ->
      entryName.substringAfterLast('/') == "module-info.class" ||
        entryName == "META-INF/MANIFEST.MF" || entryName == "META-INF/INDEX.LIST" ||
        (entryName.startsWith("META-INF/") &&
          (entryName.endsWith(".SF") ||
            entryName.endsWith(".DSA") ||
            entryName.endsWith(".RSA")))
    }

    JarOutputStream(destFile.outputStream().buffered(), manifest).use { jos ->
      entries.forEach { (entryName, sources) ->
        jos.putNextEntry(JarEntry(entryName))
        val sourcePairs = sources.map { source -> Pair(source, inputStreamOf(source, entryName)) }
        try {
          entryTransformer(entryName, sourcePairs, jos)
        } finally {
          sourcePairs.forEach { it.second.close() }
        }
      }
    }
    closeJarInputStreams()
  }

  @OptIn(UnstableMetadataApi::class)
  private fun writeJarEntry(
    entryName: String,
    sources: List<Pair<Path, InputStream>>,
    outputStream: JarOutputStream
  ) {
    if (sources.size == 1) {
      val (_, inputStream) = sources.first()
      transferFromStreamToStream(inputStream, outputStream)
    } else {
      if (entryName.endsWith(".kotlin_module")) {
        println("kotlin_module: $entryName ${sources.map { it.first }}")
        val kmModules = sources.map { (path, inputStream) ->
          KotlinModuleMetadata.read(inputStream.readAllBytes())
          // ?: throw IllegalStateException("Not a .kotlin_module file: $entryName from ${path.absolutePathString()}")
        }.map { it.kmModule }
        val mergedModule = KmModule()
        kmModules.forEach { kmModule ->
          mergedModule.optionalAnnotationClasses.addAll(kmModule.optionalAnnotationClasses)
          mergedModule.packageParts.putAll(kmModule.packageParts)
          // mergedModule.apply(kmModule::accept)
        }
        outputStream.write(
          KotlinModuleMetadata(
            mergedModule,
            JvmMetadataVersion.LATEST_STABLE_SUPPORTED
          ).write()
        )
      } else {
        println("Duplicate entry: $entryName, ${sources.map { it.first }}")
        val (_, inputStream) = sources.first()
        transferFromStreamToStream(inputStream, outputStream)
      }
    }
  }

  private fun transferFromStreamToStream(input: InputStream, output: OutputStream) {
    val buffer = ByteArray(1000)
    var count: Int
    while (input.read(buffer, 0, 1000).also { count = it } >= 0) {
      output.write(buffer, 0, count)
    }
  }

  fun uberJar(context: BuildContext): BuildRuleReturn {
    val jarFileName = (context.arguments.getValue("jarFileName") as StringValue).value
    val destFile = context.destDirectory.resolve(jarFileName)

    if (!context.hashChanged) {
      return BuildRuleReturn.value(FileValue(destFile))
    }

    val deps = (context.arguments.getValue("deps") as SetValue)
    return BuildRuleReturn.evalAndThen(
      "jvm.resolveClassPkgs",
      mapOf("classPkgs" to deps)
    ) { classPaths ->
      classPaths as ClassInstanceValue
      val cps = (classPaths["cps"] as SetValue).values
        .map { (it as PathValue).path }
      val runtimeCps = (classPaths["runtimeCps"] as SetValue).values
        .map { (it as PathValue).path }

      val manifest = Manifest()
      manifest.mainAttributes[Attributes.Name.MANIFEST_VERSION] = "1.0"

      createJar(cps + runtimeCps, destFile, manifest, ::writeJarEntry)
      BuildRuleReturn.value(FileValue(destFile))
    }
  }

  fun executableUberJar(context: BuildContext): BuildRuleReturn {
    val jarFileName = (context.arguments.getValue("jarFileName") as StringValue).value
    val destFile = context.destDirectory.resolve(jarFileName)

    if (!context.hashChanged) {
      return BuildRuleReturn.value(FileValue(destFile))
    }

    val mainClass = (context.arguments.getValue("mainClass") as StringValue).value
    val deps = (context.arguments.getValue("deps") as SetValue)
    return BuildRuleReturn.evalAndThen(
      "jvm.resolveClassPkgs",
      mapOf("classPkgs" to deps)
    ) { classPaths ->
      classPaths as ClassInstanceValue
      val cps = (classPaths["cps"] as SetValue).values
        .map { (it as PathValue).path }
      val runtimeCps = (classPaths["runtimeCps"] as SetValue).values
        .map { (it as PathValue).path }

      val manifest = Manifest()
      manifest.mainAttributes[Attributes.Name.MANIFEST_VERSION] = "1.0"
      manifest.mainAttributes[Attributes.Name.MAIN_CLASS] = mainClass

      createJar(cps + runtimeCps, destFile, manifest, ::writeJarEntry)
      BuildRuleReturn.value(FileValue(destFile))
    }
  }
}
