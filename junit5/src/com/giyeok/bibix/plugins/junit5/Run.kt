package com.giyeok.bibix.plugins.junit5

import com.giyeok.bibix.base.*
import org.junit.platform.engine.TestExecutionResult
import org.junit.platform.engine.discovery.DiscoverySelectors.selectClasspathRoots
import org.junit.platform.launcher.TestIdentifier
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder
import org.junit.platform.launcher.core.LauncherFactory
import org.junit.platform.launcher.listeners.SummaryGeneratingListener

class Run {
  fun run(context: ActionContext): BuildRuleReturn {
    val deps = context.arguments.getValue("deps")
    return BuildRuleReturn.evalAndThen(
      "resolveClassPkgs",
      mapOf("classPkgs" to deps)
    ) { classPaths ->
      val classPathsValue = classPaths as ClassInstanceValue
      val cps = (classPathsValue.getField("cps") as SetValue).values.map {
        (it as PathValue).path
      }
      val res = classPathsValue.getField("res") as SetValue // set<path>

      val launcher = LauncherFactory.create()
      val discoveryRequest = LauncherDiscoveryRequestBuilder.request()
        .selectors(selectClasspathRoots(cps.toSet()))
        .build()
      val testPlan = launcher.discover(discoveryRequest)

      launcher.registerTestExecutionListeners(object: SummaryGeneratingListener() {
        override fun dynamicTestRegistered(testIdentifier: TestIdentifier?) {
          super.dynamicTestRegistered(testIdentifier)

          context.progressLogger.logInfo("Registered: $testIdentifier")
        }

        override fun executionStarted(testIdentifier: TestIdentifier?) {
          super.executionStarted(testIdentifier)

          context.progressLogger.logInfo("Started: $testIdentifier")
        }

        override fun executionSkipped(testIdentifier: TestIdentifier?, reason: String?) {
          super.executionSkipped(testIdentifier, reason)

          context.progressLogger.logInfo("Skipped: $testIdentifier")
        }

        override fun executionFinished(
          testIdentifier: TestIdentifier?,
          testExecutionResult: TestExecutionResult?
        ) {
          super.executionFinished(testIdentifier, testExecutionResult)

          context.progressLogger.logInfo("Finished: $testIdentifier $testExecutionResult")
        }
      })
      launcher.execute(testPlan)

      BuildRuleReturn.done()
    }
  }
}
