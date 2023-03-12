package com.giyeok.bibix.plugins.jar

import com.giyeok.bibix.base.*
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.*
import java.util.zip.ZipException
import kotlin.io.path.*

class Jar {
  fun jar(context: BuildContext): BibixValue {
    val jarFileName = (context.arguments.getValue("jarFileName") as StringValue).value
    val destFile = context.destDirectory.resolve(jarFileName)

    if (!context.hashChanged) {
      return FileValue(destFile)
    }

    val deps = (context.arguments.getValue("deps") as SetValue).values
      .map { ClassPkg.fromBibix(it) }

    val manifest = Manifest()
    manifest.mainAttributes[Attributes.Name.MANIFEST_VERSION] = "1.0"

    JarOutputStream(destFile.outputStream().buffered()).use { jos ->
      deps.forEach { classPkg ->
        addClasspathToJar(classPkg.cpinfo, jos) { false }
      }
    }

    return FileValue(destFile)
  }

  // 아래 코드 참고해서 kotlin_module 파일들 merge 기능 추가
  // https://github.com/JetBrains/kotlin/blob/a3f0e429e38e0ad5708b526a24eb0fae87939c43/libraries/reflect/build.gradle.kts#L98

  // TODO filter를 parameter로 사용자에게 노출
  private val skipFiles = { path: String ->
    // reference: https://github.com/johnrengelman/shadow/blob/master/src/main/groovy/com/github/jengelman/gradle/plugins/shadow/ShadowJavaPlugin.groovy
    // 'META-INF/INDEX.LIST', 'META-INF/*.SF', 'META-INF/*.DSA', 'META-INF/*.RSA', 'module-info.class'
    path.substringAfterLast('/') == "module-info.class" ||
      path == "META-INF/MANIFEST.MF" || path == "META-INF/INDEX.LIST" ||
      (path.startsWith("META-INF/") && (
        path.endsWith(".SF") || path.endsWith(".DSA") || path.endsWith(".RSA")))
  }

  fun uberJar(context: BuildContext): BuildRuleReturn {
    val jarFileName = (context.arguments.getValue("jarFileName") as StringValue).value
    val destFile = context.destDirectory.resolve(jarFileName)

    if (!context.hashChanged) {
      return BuildRuleReturn.value(FileValue(destFile))
    }
    val deps = (context.arguments.getValue("deps") as SetValue)
    return BuildRuleReturn.evalAndThen(
      "resolveClassPkgs",
      mapOf("classPkgs" to deps)
    ) { classPaths ->
      val cps = ((classPaths as ClassInstanceValue)["cps"] as SetValue).values
        .map { (it as PathValue).path }

      val manifest = Manifest()
      manifest.mainAttributes[Attributes.Name.MANIFEST_VERSION] = "1.0"

      JarOutputStream(destFile.outputStream().buffered(), manifest).use { jos ->
        cps.forEach { cp ->
          if (cp.isRegularFile()) {
            // TODO dep is jar file
            addAnotherJarToJar(cp, jos, skipFiles)
          } else {
            check(cp.isDirectory())
            addDirectoryToJar(cp, jos, skipFiles)
          }
        }
      }
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
      "resolveClassPkgs",
      mapOf("classPkgs" to deps)
    ) { classPaths ->
      val cps = ((classPaths as ClassInstanceValue)["cps"] as SetValue).values
        .map { (it as PathValue).path }

      val manifest = Manifest()
      manifest.mainAttributes[Attributes.Name.MANIFEST_VERSION] = "1.0"
      manifest.mainAttributes[Attributes.Name.MAIN_CLASS] = mainClass

      JarOutputStream(destFile.outputStream().buffered(), manifest).use { jos ->
        cps.forEach { cp ->
          if (cp.isRegularFile()) {
            addAnotherJarToJar(cp, jos, skipFiles)
          } else {
            check(cp.isDirectory())
            addDirectoryToJar(cp, jos, skipFiles)
          }
        }
      }
      BuildRuleReturn.value(FileValue(destFile))
    }
  }

  fun addClasspathToJar(cp: CpInfo, dest: JarOutputStream, skipEntry: (String) -> Boolean) {
    when (cp) {
      is ClassesInfo -> {
        cp.classDirs.forEach { classDir ->
          addDirectoryToJar(classDir, dest, skipEntry)
        }
        cp.resDirs.forEach { resDir ->
          addDirectoryToJar(resDir, dest, skipEntry)
        }
      }

      is JarInfo -> {
        addAnotherJarToJar(cp.jar, dest, skipEntry)
      }
    }
  }

  fun addAnotherJarToJar(inputJar: Path, dest: JarOutputStream, skipEntry: (String) -> Boolean) {
    JarInputStream(inputJar.inputStream().buffered()).use { jis ->
      var entry = jis.nextEntry
      while (entry != null) {
        if (!entry.isDirectory && !skipEntry(entry.name)) {
          try {
            dest.putNextEntry(JarEntry(entry.name))
            transferFromStreamToStream(jis, dest)
          } catch (e: ZipException) {
            // TODO 일단 무시하고 진행하도록
            println(e.message)
          }
        }
        entry = jis.nextEntry
      }
      jis.closeEntry()
    }
  }

  fun addDirectoryToJar(inputDir: Path, dest: JarOutputStream, skipEntry: (String) -> Boolean) {
    Files.walk(inputDir).toList().forEach { path ->
      if (path.isRegularFile()) {
        val filePath = path.absolute().relativeTo(inputDir.absolute()).pathString
        if (!skipEntry(filePath)) {
          try {
            dest.putNextEntry(JarEntry(filePath))
            transferFromStreamToStream(path.toFile().inputStream().buffered(), dest)
          } catch (e: ZipException) {
            // TODO 일단 무시하고 진행하도록
            println(e.message)
          }
        }
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
}

