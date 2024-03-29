package com.giyeok.bibix.plugins.cc

import com.giyeok.bibix.base.*
import java.nio.file.Path
import kotlin.io.path.absolute
import kotlin.io.path.absolutePathString
import kotlin.io.path.name

class Compile {
  fun runCompiler(context: BuildContext, args: List<String>) {
    val pwd = (context.arguments.getValue("pwd") as PathValue).path.absolute()

    context.progressLogger.logInfo("args: ${args.joinToString(" ")} pwd=${pwd.absolutePathString()}")
    val process = Runtime.getRuntime().exec(args.toTypedArray(), null, pwd.toFile())
    val errorMessage = String(process.errorStream.readAllBytes())
    val stdMessage = String(process.inputStream.readAllBytes())
    process.waitFor()

    if (process.exitValue() != 0) {
      context.progressLogger.logError(errorMessage)
      context.progressLogger.logInfo(stdMessage)
      throw IllegalStateException("cc compile error(args=$args)\n$errorMessage")
    }
  }

  fun prepareHdrs(hdrs: List<FilesWithRoot>): List<Path> {
    val hdrDirs = hdrs.map { it.root }
    // TODO hdrDirs가 가리키는 디렉토리에 hdrs에 포함되지 않은 다른 파일이 있으면
    //      해당되는 hdrs만 복사해서 복사한 디렉토리를 대신 포함
    return hdrDirs.distinct()
  }

  fun hdrValueFrom(value: BibixValue): FilesWithRoot =
    when (value) {
      is FileValue -> FilesWithRoot(value.file.parent, listOf(value.file))
      is ClassInstanceValue -> FilesWithRoot.fromBibix(value)
      else -> throw IllegalStateException()
    }

  fun buildLibrary(
    context: BuildContext
  ): BuildRuleReturn =
    buildLibraryImpl(
      context,
      (context.arguments.getValue("srcs") as SetValue).values.map { (it as FileValue).file },
      (context.arguments.getValue("hdrs") as ListValue).values.map { hdrValueFrom(it) },
      (context.arguments.getValue("deps") as ListValue).values.map { Library.fromBibix(it) },
      (context.arguments.getValue("compilerCommand") as StringValue).value,
      (context.arguments.getValue("copts") as ListValue).values.map { (it as StringValue).value },
    )

  fun buildBinary(
    context: BuildContext
  ): BuildRuleReturn {
    TODO()
  }

  fun buildLibraryImpl(
    context: BuildContext,
    srcs: List<Path>,
    hdrs: List<FilesWithRoot>,
    deps: List<Library>,
    compilerCommand: String,
    copts: List<String>,
  ): BuildRuleReturn {
//    if (!context.hashChanged && context.prevResult != null) {
//      return BuildRuleReturn.value(context.prevResult!!)
//    }

    context.clearDestDirectory()
    // TODO outputName이 없으면 target 이름에서 유추
    val destPath = context.destDirectory.resolve("a.out").absolute()

    if (srcs.isEmpty()) {
      return BuildRuleReturn.value(Library(listOf(), ObjType.StaticObj, hdrs, srcs, deps).toBibix())
    } else {
      val argsBuilder = mutableListOf(compilerCommand, "-c")

      val mergedHdrs = mergeHdrs(hdrs, deps)
      if (mergedHdrs.isNotEmpty()) {
        val preparedHdrs = prepareHdrs(mergedHdrs)
        preparedHdrs.forEach { hdr ->
          argsBuilder.add("-I${hdr.absolutePathString()}")
        }
      }
      argsBuilder.addAll(copts)

      val args = argsBuilder.toList()
      val objs = srcs.map { src ->
        val filename = src.name.substringBeforeLast('.')
        val outpath = context.destDirectory.resolve("$filename.o")
        runCompiler(
          context,
          args + listOf(src.absolutePathString(), "-o", outpath.absolutePathString())
        )
        outpath
      }
      return BuildRuleReturn.value(Library(objs, ObjType.StaticObj, hdrs, srcs, deps).toBibix())
    }
  }

  private fun mergeHdrs(hdrs: List<FilesWithRoot>, deps: List<Library>): List<FilesWithRoot> {
    val merged = mutableListOf<FilesWithRoot>()
    merged.addAll(hdrs)
    fun traverseLibrary(lib: Library) {
      merged.addAll(lib.hdrs)
      lib.deps.forEach { traverseLibrary(it) }
    }
    deps.forEach {
      traverseLibrary(it)
    }
    return merged
  }

  fun buildBinaryImpl(
    context: BuildContext,
    srcs: List<Path>,
    hdrs: List<FilesWithRoot>,
    deps: List<Library>,
    compilerCommand: String,
  ): BuildRuleReturn {
    context.clearDestDirectory()
    // TODO outputName이 없으면 target 이름에서 유추
    val destPath = context.destDirectory.resolve("a.out").absolute()
    val args = listOf(compilerCommand) +
      srcs.map { it.absolutePathString() } +
      listOf("-o", destPath.absolutePathString())
    runCompiler(context, args)
    return BuildRuleReturn.value(Binary(destPath, srcs, deps).toBibix())
  }
}
