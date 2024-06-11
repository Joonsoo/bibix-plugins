package com.giyeok.bibix.plugins.cmd

import com.giyeok.bibix.base.BuildContext
import com.giyeok.bibix.base.BuildRuleReturn
import com.giyeok.bibix.base.FileValue
import java.nio.file.Path

class CommandImpl: CommandInterface {
  private fun executeCommand(context: BuildContext, command: String, pwd: Path) {
    // TODO command 제대로 파싱하기
    val tokens = command.split(' ')
    val process = ProcessBuilder(tokens).directory(pwd.toFile()).start()

    val stdout = String(process.inputStream.readAllBytes())
    if (stdout.isNotEmpty()) {
      context.progressLogger.logInfo(stdout)
    }

    val errorMessage = String(process.errorStream.readAllBytes())
    if (errorMessage.isNotEmpty()) {
      context.progressLogger.logError(errorMessage)
    }

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
    pwd: Path,
    prerequisites: List<CommandPrerequisite>
  ): BuildRuleReturn =
    BuildRuleReturn.value(CommandPrerequisite(prerequisites, command, pwd).toBibix())

  override fun execute(
    context: BuildContext,
    command: String?,
    output: Path,
    pwd: Path,
    prerequisites: List<CommandPrerequisite>
  ): BuildRuleReturn {
    resolvePrerequisites(context, prerequisites)
    if (command != null) {
      executeCommand(context, command, pwd)
    }
    return BuildRuleReturn.value(FileValue(output))
  }
}
