package com.giyeok.bibix.plugins.cmd

import com.giyeok.bibix.base.BuildContext
import com.giyeok.bibix.base.BuildRuleReturn
import com.giyeok.bibix.base.FileValue
import com.giyeok.bibix.base.ProgressLogger
import java.nio.file.Path

fun Process.writeProcessOutputsTo(logger: ProgressLogger) {
  this.inputStream.bufferedReader().forEachLine { line ->
    val trimeed = line.trim()
    if (trimeed.isNotEmpty()) {
      logger.logInfo(trimeed)
    }
  }

  this.errorStream.bufferedReader().forEachLine { line ->
    val trimeed = line.trim()
    if (trimeed.isNotEmpty()) {
      logger.logInfo(trimeed)
    }
  }
}

class CommandImpl: CommandInterface {
  private fun executeCommand(context: BuildContext, command: String, pwd: Path?) {
    // TODO command 제대로 파싱하기
    val tokens = command.split(' ')

    val processBuilder = ProcessBuilder(tokens)
    if (pwd != null) {
      processBuilder.directory(pwd.toFile())
    }
    val process = processBuilder.start()

    process.writeProcessOutputsTo(context.progressLogger)

    process.waitFor()

    check(process.exitValue() == 0)
  }

  private fun resolvePrerequisites(
    context: BuildContext,
    prerequisites: List<CommandPrerequisite>
  ) {
    for (prerequisite in prerequisites) {
      resolvePrerequisites(context, prerequisite.prerequisites)
      executeCommand(context, prerequisite.command, prerequisite.pwd)
    }
  }

  override fun command(
    context: BuildContext,
    command: String,
    pwd: Path?,
    prerequisites: List<CommandPrerequisite>
  ): BuildRuleReturn =
    BuildRuleReturn.value(CommandPrerequisite(prerequisites, command, pwd).toBibix())

  override fun execute(
    context: BuildContext,
    command: String?,
    output: Path,
    pwd: Path?,
    prerequisites: List<CommandPrerequisite>
  ): BuildRuleReturn {
    resolvePrerequisites(context, prerequisites)
    if (command != null) {
      executeCommand(context, command, pwd)
    }
    return BuildRuleReturn.value(FileValue(output))
  }
}
