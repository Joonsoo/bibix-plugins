package com.giyeok.bibix.plugins.scala

import com.giyeok.bibix.base.*
import scala.jdk.`CollectionConverters$`
import scala.tools.nsc.Global
import scala.tools.nsc.Settings
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries

class Library {
  fun allFilesOf(directory: Path): Set<Path> =
    directory.listDirectoryEntries().flatMap { sub ->
      if (sub.isDirectory()) {
        allFilesOf(sub)
      } else {
        listOf(sub)
      }
    }.toSet()

  fun findResourceDirectoriesOf(paths: Collection<Path>): Set<Path> {
    val mutPaths = paths.toMutableSet()
    val resDirs = mutableSetOf<Path>()
    while (mutPaths.isNotEmpty()) {
      val path = mutPaths.first()
      val directory = path.parent
      val dirFiles = allFilesOf(directory)
      if (paths.containsAll(dirFiles)) {
        resDirs.removeIf { it.startsWith(directory) }
        resDirs.add(directory)
        mutPaths.removeAll(dirFiles)
      } else {
        resDirs.add(path)
      }
      mutPaths.remove(path)
    }
    return resDirs
  }

  fun build(context: BuildContext): BuildRuleReturn {
    val deps = context.arguments.getValue("deps") as SetValue
    val srcsValue = context.arguments.getValue("srcs") as SetValue
    val srcs = srcsValue.values.map { (it as FileValue).file }
    val resourcesValue = context.arguments.getValue("resources") as SetValue
    val resources = resourcesValue.values.map { (it as FileValue).file }
    val sdkVersion = (context.arguments.getValue("sdkVersion") as StringValue).value

    if (!context.hashChanged) {
      return BuildRuleReturn.value(
        ClassPkg(
          LocalBuilt(context.objectIdHash, "scala.library"),
          ClassesInfo(listOf(context.destDirectory), listOf(), srcs),
          deps.values.map { ClassPkg.fromBibix(it) }
        ).toBibix()
      )
    }

    check(srcs.isNotEmpty()) { "srcs must not be empty" }
    return BuildRuleReturn.evalAndThen(
      "jvm.resolveClassPkgs",
      mapOf("classPkgs" to deps)
    ) { classPaths ->
      val settings = Settings()
      val cps = ClassPaths.fromBibix(classPaths)
      cps.cps.forEach { cpPath ->
        settings.classpath().append(cpPath.absolutePathString())
      }
      settings.outputDirs().setSingleOutput(context.destDirectory.absolutePathString())
      // settings.usejavacp().`value_$eq`(true)

      val global = Global(settings)
      val run = global.Run()
      val srcPaths = srcs.map { it.absolutePathString() }
      val srcScala = `CollectionConverters$`.`MODULE$`.ListHasAsScala(srcPaths).asScala().toList()
      run.compile(srcScala)

      // 컴파일 실패시 예외 발생
      if (global.reporter().hasErrors()) {
        throw IllegalStateException(
          "${global.reporter().errorCount()} errors reported from scala compiler"
        )
      }

      val resDirs = findResourceDirectoriesOf(resources)
      if (resDirs.any { !it.isDirectory() }) {
        throw IllegalStateException("Not implemented yet(Currently, the directories containing resource files must not contain non-resource files)")
      }

      BuildRuleReturn.value(
        ClassPkg(
          LocalBuilt(context.objectIdHash, "scala.library"),
          ClassesInfo(listOf(context.destDirectory), resDirs.toList(), srcs),
          deps.values.map { ClassPkg.fromBibix(it) }
        ).toBibix()
      )
    }
  }
}
