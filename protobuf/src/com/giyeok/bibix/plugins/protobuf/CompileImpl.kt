package com.giyeok.bibix.plugins.protobuf

import com.giyeok.bibix.base.*
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission
import kotlin.io.path.*
import com.giyeok.bibix.base.OS

class CompileImpl: CompileInterface {
  override fun schema(
    context: BuildContext,
    srcs: List<Path>,
    deps: List<ProtoSchema>,
    protocPath: Path,
  ): BuildRuleReturn {
    val mergedIncludes = listOf(protocPath.resolve("include")) +
      deps.flatMap { dep ->
        // schema 파일의 parent를 사용함. protoc가 include는 디렉토리를 받기 때문에..
        // 파일 이름이 충돌하면 문제가 생길텐데 일단은 그냥 이렇게 해둬야지..
        dep.schemaFiles.map { it.parent } + dep.includes
      }

    return BuildRuleReturn.value(ProtoSchema(srcs, mergedIncludes).toBibix())
  }

  private fun callCompiler(
    context: BuildContext,
    outArgs: List<String>,
    newPathEnv: List<String> = listOf(),
  ) {
    // TODO Skip compiling if !hashChanged
    val protocPath = (context.arguments.getValue("protocPath") as DirectoryValue).directory

    val schema = ProtoSchema.fromBibix(context.arguments.getValue("schema"))
    val srcs: List<Path> = schema.schemaFiles.map { it.absolute() }
    val includes: List<Path> = schema.includes.map { it.absolute() }

    val srcArgs = mutableListOf<String>()
    srcs.forEach { srcArgs.add(it.absolutePathString()) }

    val protoPaths = srcs.map { it.parent }.toSet() + includes
    protoPaths.forEach { srcArgs.add("-I${it.absolutePathString()}") }

    val executableName = if (context.buildEnv.os is OS.Windows) "protoc.exe" else "protoc"
    val executableFile = protocPath.resolve("bin").resolve(executableName)

    val prevPermissions = executableFile.getPosixFilePermissions()
    executableFile.setPosixFilePermissions(prevPermissions + PosixFilePermission.OWNER_EXECUTE)

    val runArgs = listOf(executableFile.absolutePathString()) + srcArgs + outArgs
    println(runArgs)

    val processBuilder = ProcessBuilder(runArgs)
    if (newPathEnv.isNotEmpty()) {
      println(processBuilder.environment())
      val existingPath = (processBuilder.environment()["PATH"] ?: "").split(':')
      processBuilder.environment()["PATH"] = (existingPath + newPathEnv).joinToString(":")
    }
    processBuilder.directory(protocPath.resolve("bin").absolute().toFile())

    val process = processBuilder.start()

    val errorMessage = String(process.errorStream.readAllBytes())
    context.progressLogger.logInfo(String(process.inputStream.readAllBytes()))
    context.progressLogger.logError(errorMessage)
    process.waitFor()

    println(errorMessage)

    check(process.exitValue() == 0)
  }

  override fun protoset(
    context: BuildContext,
    schema: ProtoSchema,
    protocPath: Path,
    outputFileName: String?
  ): BuildRuleReturn {
    val destFile = context.destDirectory.resolve(outputFileName ?: "protoset.protoset")

    if (context.hashChanged) {
      context.clearDestDirectory()
      callCompiler(context, listOf("--descriptor_set_out=${destFile.absolutePathString()}"))
    }

    return BuildRuleReturn.value(FileValue(destFile))
  }

  override fun cpp(
    context: BuildContext,
    schema: ProtoSchema,
    protocPath: Path
  ): BuildRuleReturn {
    val destDirectory = context.destDirectory
    if (context.hashChanged) {
      context.clearDestDirectory()
      callCompiler(context, listOf("--cpp_out=${destDirectory.absolutePathString()}"))
    }
    return BuildRuleReturn.value(GeneratedSrcsSet(destDirectory, getFiles(destDirectory)).toBibix())
  }

  override fun csharp(
    context: BuildContext,
    schema: ProtoSchema,
    protocPath: Path
  ): BuildRuleReturn {
    val destDirectory = context.destDirectory
    if (context.hashChanged) {
      context.clearDestDirectory()
      callCompiler(context, listOf("--csharp_out=${destDirectory.absolutePathString()}"))
    }
    return BuildRuleReturn.value(GeneratedSrcsSet(destDirectory, getFiles(destDirectory)).toBibix())
  }

  private fun getFiles(directory: Path): List<Path> {
    val files = Files.walk(directory, 1000).toList()
      .filter { it.isRegularFile() }
    return files
  }

  override fun java(
    context: BuildContext,
    schema: ProtoSchema,
    protocPath: Path
  ): BuildRuleReturn {
    val destDirectory = context.destDirectory
    if (context.hashChanged) {
      context.clearDestDirectory()
      callCompiler(context, listOf("--java_out=${destDirectory.absolutePathString()}"))
    }
    return BuildRuleReturn.value(GeneratedSrcsSet(destDirectory, getFiles(destDirectory)).toBibix())
  }

  override fun javaLite(
    context: BuildContext,
    schema: ProtoSchema,
    protocPath: Path
  ): BuildRuleReturn {
    val destDirectory = context.destDirectory
    if (context.hashChanged) {
      context.clearDestDirectory()
      callCompiler(context, listOf("--java_out=lite:${destDirectory.absolutePathString()}"))
    }
    return BuildRuleReturn.value(GeneratedSrcsSet(destDirectory, getFiles(destDirectory)).toBibix())
  }

  override fun javascript(
    context: BuildContext,
    schema: ProtoSchema,
    protocPath: Path
  ): BuildRuleReturn {
    val destDirectory = context.destDirectory
    if (context.hashChanged) {
      context.clearDestDirectory()
      callCompiler(context, listOf("--js_out=${destDirectory.absolutePathString()}"))
    }
    return BuildRuleReturn.value(GeneratedSrcsSet(destDirectory, getFiles(destDirectory)).toBibix())
  }

  override fun kotlin(
    context: BuildContext,
    schema: ProtoSchema,
    protocPath: Path
  ): BuildRuleReturn {
    val destDirectory = context.destDirectory
    if (context.hashChanged) {
      context.clearDestDirectory()
      callCompiler(context, listOf("--kotlin_out=${destDirectory.absolutePathString()}"))
    }
    return BuildRuleReturn.value(GeneratedSrcsSet(destDirectory, getFiles(destDirectory)).toBibix())
  }

  override fun objc(
    context: BuildContext,
    schema: ProtoSchema,
    protocPath: Path
  ): BuildRuleReturn {
    TODO("Not yet implemented")
  }

  override fun php(
    context: BuildContext,
    schema: ProtoSchema,
    protocPath: Path
  ): BuildRuleReturn {
    TODO("Not yet implemented")
  }

  override fun python(
    context: BuildContext,
    schema: ProtoSchema,
    protocPath: Path
  ): BuildRuleReturn {
    TODO("Not yet implemented")
  }

  override fun ruby(
    context: BuildContext,
    schema: ProtoSchema,
    protocPath: Path
  ): BuildRuleReturn {
    TODO("Not yet implemented")
  }

  override fun dart(
    context: BuildContext,
    schema: ProtoSchema,
    protocPath: Path,
    dartPath: Path,
    pluginPath: Path,
    // TODO with grpc?
  ): BuildRuleReturn {
    val destDirectory = context.destDirectory
    if (context.hashChanged) {
      context.clearDestDirectory()
      callCompiler(
        context,
        listOf(
          "--plugin=protoc-gen-dart=${pluginPath.absolutePathString()}",
          "--dart_out=grpc:${destDirectory.absolutePathString()}",
        ),
        newPathEnv = listOf(dartPath.parent.absolutePathString())
      )
    }
    return BuildRuleReturn.value(GeneratedSrcsSet(destDirectory, getFiles(destDirectory)).toBibix())
  }
}
