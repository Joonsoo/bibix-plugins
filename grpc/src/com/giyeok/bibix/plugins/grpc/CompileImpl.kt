package com.giyeok.bibix.plugins.grpc

import com.giyeok.bibix.base.*
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission
import kotlin.io.path.*

class CompileImpl : CompileInterface {
  private fun callCompiler(context: BuildContext, pluginArgs: List<String>) {
    // TODO Skip compiling if !hashChanged
    val os = context.buildEnv.os
    val protocPath = (context.arguments.getValue("protocPath") as DirectoryValue).directory

    val schema = ProtoSchema.fromBibix(context.arguments.getValue("schema"))
    val srcs: List<Path> = schema.schemaFiles
    val includes: List<Path> = schema.includes

    val srcArgs = mutableListOf<String>()
    srcs.forEach { srcArgs.add(it.absolutePathString()) }

    val protoPaths = srcs.map { it.parent }.toSet() + includes
    protoPaths.forEach { srcArgs.add("-I${it.absolutePathString()}") }

    val executableName = if (os is OS.Windows) "protoc.exe" else "protoc"
    val executableFile = protocPath.resolve("bin").resolve(executableName)

    val prevPermissions = executableFile.getPosixFilePermissions()
    executableFile.setPosixFilePermissions(prevPermissions + PosixFilePermission.OWNER_EXECUTE)

    val runArgs = listOf(executableFile.absolutePathString()) + srcArgs + pluginArgs
    val process = Runtime.getRuntime()
      .exec(runArgs.toTypedArray(), arrayOf(), protocPath.resolve("bin").absolute().toFile())

    context.progressLogger.logInfo(String(process.inputStream.readAllBytes()))
    context.progressLogger.logError(String(process.errorStream.readAllBytes()))
    process.waitFor()

    check(process.exitValue() == 0)
  }

  private fun getFiles(directory: Path): List<Path> {
    return Files.walk(directory, 1000).toList()
      .filter { it.isRegularFile() }
      .distinct()
  }

  override fun java(
    context: BuildContext,
    schema: ProtoSchema,
    protocPath: Path,
    pluginPath: Path,
  ): BuildRuleReturn {
    val destDirectory = context.clearDestDirectory()
    if (context.hashChanged) {
      callCompiler(
        context, listOf(
          "--plugin=${pluginPath.absolutePathString()}",
          "--grpc-java_out=${destDirectory.absolutePathString()}"
        )
      )
    }
    return BuildRuleReturn.value(GeneratedSrcsSet(destDirectory, getFiles(destDirectory)).toBibix())
  }

  override fun kotlin(
    context: BuildContext,
    schema: ProtoSchema,
    protocPath: Path,
    pluginPath: Path,
  ): BuildRuleReturn {
    val destDirectory = context.clearDestDirectory()
    if (context.hashChanged) {
      callCompiler(
        context, listOf(
          "--plugin=${pluginPath.absolutePathString()}",
          "--grpc-kotlin_out=${destDirectory.absolutePathString()}"
        )
      )
    }
    return BuildRuleReturn.value(GeneratedSrcsSet(destDirectory, getFiles(destDirectory)).toBibix())
  }

  override fun web(
    context: BuildContext,
    schema: ProtoSchema,
    protocPath: Path,
    pluginPath: Path,
  ): BuildRuleReturn {
    TODO()
  }
}
