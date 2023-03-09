package com.giyeok.bibix.plugins.java

import com.giyeok.bibix.base.*
import java.nio.file.Path

class Library {
  private fun built(targetId: String, dest: Path, deps: List<ClassPkg>): BuildRuleReturn =
    BuildRuleReturn.value(
      ClassPkg(
        LocalBuilt(targetId, "java.library"),
        ClassesInfo(listOf(dest), listOf(), null),
        deps,
      ).toBibix()
    )

  fun Path.absolutePathString() = toAbsolutePath().toString()

  fun build(context: BuildContext): BuildRuleReturn {
    val depsValue = context.arguments.getValue("deps") as SetValue
    val deps = depsValue.values.map { ClassPkg.fromBibix(it) }
    val dest = context.destDirectory

    if (!context.hashChanged) {
      return built(context.targetId, dest, deps)
    }

    return BuildRuleReturn.evalAndThen(
      "jvm.resolveClassPkgs",
      mapOf("classPkgs" to depsValue)
    ) { classPaths ->
      val cps = (classPaths as ClassInstanceValue)["cps"] as SetValue

      val srcs = (context.arguments["srcs"]!! as SetValue).values.map { src ->
        (src as FileValue).file
      }

      val args = mutableListOf("javac")
      if (cps.values.isNotEmpty()) {
        args.add("-cp")
        args.add(cps.values.joinToString(":") { (it as PathValue).path.absolutePathString() })
      }
      args.add("-d")
      args.add(dest.absolutePathString())
      args.addAll(srcs.map { it.absolutePathString() })

      println("java args: $args")

      val process = Runtime.getRuntime().exec(args.toTypedArray())
      val errorMessage = String(process.errorStream.readAllBytes())
      process.waitFor()

      if (process.exitValue() != 0) {
        throw IllegalStateException("java compile error(args=$args)\n$errorMessage")
      }

      // ClassPkg = (origin: ClassOrigin, cps: set<path>, deps: set<ClassPkg>)
      built(context.targetId, dest, deps)
    }
  }

  fun run(context: ActionContext) {
    TODO()
  }
}

