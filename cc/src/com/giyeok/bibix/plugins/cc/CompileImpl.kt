package com.giyeok.bibix.plugins.cc

import com.giyeok.bibix.base.BuildContext
import com.giyeok.bibix.base.BuildRuleReturn
import java.nio.file.Path
import kotlin.io.path.absolute
import kotlin.io.path.absolutePathString

class CompileImpl: CompileInterface {
  fun runCompiler(context: BuildContext, args: List<String>) {
    context.progressLogger.logInfo("args: ${args.joinToString(" ")}")
    val process = Runtime.getRuntime().exec(args.toTypedArray())
    val errorMessage = String(process.errorStream.readAllBytes())
    val stdMessage = String(process.inputStream.readAllBytes())
    process.waitFor()

    if (process.exitValue() != 0) {
      context.progressLogger.logError(errorMessage)
      context.progressLogger.logInfo(stdMessage)
      throw IllegalStateException("cc compile error(args=$args)\n$errorMessage")
    }
  }

  override fun buildLibrary(
    context: BuildContext,
    srcs: List<Path>,
    hdrs: List<Path>,
    deps: List<Library>,
    compilerCommand: String,
    outputName: String?,
  ): BuildRuleReturn {
//    if (!context.hashChanged && context.prevResult != null) {
//      return BuildRuleReturn.value(context.prevResult!!)
//    }

    context.clearDestDirectory()
    // TODO outputName이 없으면 target 이름에서 유추
    val destPath = context.destDirectory.resolve(outputName ?: "a.out").absolute()
    val args = listOf(compilerCommand, "-c") +
      srcs.map { it.absolutePathString() } // +
      // listOf("-o", destPath.absolutePathString())
    runCompiler(context, args)
    return BuildRuleReturn.value(Library(StaticObject(destPath), hdrs, srcs, deps).toBibix())
  }

  override fun buildBinary(
    context: BuildContext,
    srcs: List<Path>,
    hdrs: List<Path>,
    deps: List<Library>,
    compilerCommand: String,
    outputName: String?,
  ): BuildRuleReturn {
    context.clearDestDirectory()
    // TODO outputName이 없으면 target 이름에서 유추
    val destPath = context.destDirectory.resolve(outputName ?: "a.out").absolute()
    val args = listOf(compilerCommand) +
      srcs.map { it.absolutePathString() } +
      listOf("-o", destPath.absolutePathString())
    runCompiler(context, args)
    return BuildRuleReturn.value(Binary(destPath, srcs, deps).toBibix())
  }
}
