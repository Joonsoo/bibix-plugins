package com.giyeok.bibix.plugins.scala

import com.giyeok.bibix.base.*
import com.giyeok.bibix.plugins.base.*
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
    val runtimeDeps = context.arguments.getValue("runtimeDeps") as SetValue
    val srcsValue = context.arguments.getValue("srcs") as SetValue
    val srcs = srcsValue.values.map { (it as FileValue).file }
    val resourcesValue = context.arguments.getValue("resources") as SetValue
    val resources = resourcesValue.values.map { (it as FileValue).file }
    val sdkVersion = context.arguments.getValue("sdkVersion") as StringValue
    val outVersion = context.arguments.getValue("outVersion") as StringValue

    val resDirs = findResourceDirectoriesOf(resources)
    if (resDirs.any { !it.isDirectory() }) {
      throw IllegalStateException("Not implemented yet(Currently, the directories containing resource files must not contain non-resource files)")
    }

    if (!context.hashChanged && context.prevResult != null) {
      context.progressLogger.logInfo("Reusing previous value...")
      return BuildRuleReturn.value(context.prevResult!!)
    }

    check(srcs.isNotEmpty()) { "srcs must not be empty" }
    return BuildRuleReturn.evalAndThen(
      "maven.artifact",
      mapOf(
        "group" to StringValue("org.scala-lang"),
        "artifact" to StringValue("scala-library"),
        "version" to sdkVersion
      )
    ) { sdkClassPkg ->
      // sdkClassPkg should be at the beginning of the deps
      val newDeps = listOf(sdkClassPkg) + deps.values
      BuildRuleReturn.evalAndThen(
        "jvm.resolveClassPkgs",
        mapOf("classPkgs" to SetValue(newDeps))
      ) { classPaths ->
        val settings = Settings()
        val cps = ClassPaths.fromBibix(classPaths)
        cps.cps.forEach { cpPath ->
          settings.classpath().append(cpPath.absolutePathString())
        }
        settings.outputDirs().setSingleOutput(context.destDirectory.absolutePathString())
        // settings.usejavacp().`value_$eq`(true)
        settings.target().`v_$eq`(outVersion.value)

        val srcPaths = srcs.map { it.absolutePathString() }
        context.progressLogger.logInfo("scala compiler:")
        context.progressLogger.logInfo("srcs=$srcPaths")
        context.progressLogger.logInfo("settings=$settings")

        val global = Global(settings)
        val run = global.Run()
        val srcScala = `CollectionConverters$`.`MODULE$`.ListHasAsScala(srcPaths).asScala().toList()
        run.compile(srcScala)

        // 컴파일 실패시 예외 발생
        if (global.reporter().hasErrors()) {
          throw IllegalStateException(
            "${global.reporter().errorCount()} errors reported from scala compiler"
          )
        }

        BuildRuleReturn.value(
          ClassPkg(
            origin=LocalBuilt(context.targetId, "scala.library"),
            cpinfo=ClassesInfo(listOf(context.destDirectory), resDirs.toList(), srcs),
            deps=newDeps.map { ClassPkg.fromBibix(it) },
            runtimeDeps=runtimeDeps.values.map { ClassPkg.fromBibix(it) },
            nativeLibDirs=listOf<Path>(),
          ).toBibix()
        )
      }
    }
  }
}
