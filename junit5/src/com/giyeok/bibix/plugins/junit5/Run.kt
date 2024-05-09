package com.giyeok.bibix.plugins.junit5

import com.giyeok.bibix.base.ActionContext
import com.giyeok.bibix.base.BuildRuleReturn
import com.giyeok.bibix.base.ClassInstanceValue
import com.giyeok.bibix.base.DirectoryValue
import com.giyeok.bibix.base.ProgressLogger
import com.giyeok.bibix.base.SetValue
import org.junit.platform.engine.TestExecutionResult
import org.junit.platform.engine.discovery.ClassNameFilter.includeClassNamePatterns
import org.junit.platform.engine.discovery.DiscoverySelectors.selectDirectory
import org.junit.platform.engine.reporting.ReportEntry
import org.junit.platform.launcher.TestIdentifier
import org.junit.platform.launcher.TestPlan
import org.junit.platform.launcher.core.LauncherConfig
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder
import org.junit.platform.launcher.core.LauncherFactory
import org.junit.platform.launcher.listeners.SummaryGeneratingListener
import org.junit.vintage.engine.VintageTestEngine
import java.io.PrintWriter
import java.io.StringWriter
import java.nio.file.Path
import kotlin.io.path.Path


class Run {
  class TestExecutionListener(val progressLogger: ProgressLogger): SummaryGeneratingListener() {
    override fun dynamicTestRegistered(testIdentifier: TestIdentifier?) {
      super.dynamicTestRegistered(testIdentifier)

      progressLogger.logInfo("Registered: $testIdentifier")
    }

    override fun executionStarted(testIdentifier: TestIdentifier?) {
      super.executionStarted(testIdentifier)

      progressLogger.logInfo("Started: $testIdentifier")
    }

    override fun executionSkipped(testIdentifier: TestIdentifier?, reason: String?) {
      super.executionSkipped(testIdentifier, reason)

      progressLogger.logInfo("Skipped: $testIdentifier")
    }

    override fun executionFinished(
      testIdentifier: TestIdentifier?,
      testExecutionResult: TestExecutionResult?
    ) {
      super.executionFinished(testIdentifier, testExecutionResult)

      progressLogger.logInfo("Finished: $testIdentifier $testExecutionResult")
    }

    override fun testPlanExecutionStarted(testPlan: TestPlan?) {
      super.testPlanExecutionStarted(testPlan)

      progressLogger.logInfo("Plan Started: $testPlan")
    }

    override fun testPlanExecutionFinished(testPlan: TestPlan?) {
      super.testPlanExecutionFinished(testPlan)

      progressLogger.logInfo("Plan Finished: $testPlan")
    }

    override fun reportingEntryPublished(testIdentifier: TestIdentifier?, entry: ReportEntry?) {
      super.reportingEntryPublished(testIdentifier, entry)

      progressLogger.logInfo("Result: $testIdentifier $entry")
    }
  }

  fun runTests(cps: Set<Path>, progressLogger: ProgressLogger) {
    progressLogger.logInfo("cps: $cps")

    val discoveryRequestBuilder = LauncherDiscoveryRequestBuilder.request()
    cps.forEach { cp ->
      discoveryRequestBuilder.selectors(selectDirectory(cp.toFile()))
    }
    discoveryRequestBuilder.filters(includeClassNamePatterns(".*"))

    val discoveryRequest = discoveryRequestBuilder
      .build()
    progressLogger.logInfo("${discoveryRequest.configurationParameters}")

    LauncherFactory.openSession(
      LauncherConfig.builder()
        .addTestEngines(VintageTestEngine())
        .build()
    ).use { session ->
      val launcher = session.launcher

      val listener = SummaryGeneratingListener()
      launcher.registerTestExecutionListeners(TestExecutionListener(progressLogger))
      launcher.registerTestExecutionListeners(listener)

      val testPlan = launcher.discover(discoveryRequest)

      progressLogger.logInfo("$testPlan hasTests=${testPlan.containsTests()}")

      progressLogger.logInfo("Starting JUnit5 tests...")

      launcher.execute(testPlan)

      progressLogger.logInfo("Finished JUnit5 tests...")

      val writer = StringWriter()
      PrintWriter(writer).use {
        listener.summary.printTo(it)
      }
      progressLogger.logInfo("Test summary: $writer")
    }
  }

  fun run(context: ActionContext): BuildRuleReturn {
    val deps = context.arguments.getValue("deps")

    val cps = mutableListOf<Path>()
    (deps as SetValue).values.map {
      val cp = (it as ClassInstanceValue).getField("cpinfo") as ClassInstanceValue
      when (cp.className) {
        "JarInfo" -> TODO()

        "ClassesInfo" -> {
          (cp.getField("classDirs") as SetValue).values.forEach { dir ->
            cps.add((dir as DirectoryValue).directory)
          }
        }

        else -> throw IllegalStateException()
      }
    }

    runTests(cps.toSet(), context.progressLogger)
    return BuildRuleReturn.done()


//    return BuildRuleReturn.evalAndThen(
//      "jvm.resolveClassPkgs",
//      mapOf("classPkgs" to deps)
//    ) { classPaths ->
//      val classPathsValue = classPaths as ClassInstanceValue
//      val cps = (classPathsValue.getField("cps") as SetValue).values.map {
//        (it as PathValue).path
//      }
//
//      val cpParents = cps.map { if (it.extension == "jar") it else it.parent }.toSet()
//
//      runTests(cpParents.toSet(), context.progressLogger)
//
//      BuildRuleReturn.done()
//    }
  }

  companion object {
    @JvmStatic
    fun main(args: Array<String>) {
      Run().runTests(
        setOf(Path("/Users/joonsoo/Documents/workspace/bibix-plugins/junit5/bbxbuild/objects/d6883b629bb0807898692a225e6e0bd4c874ffc1")),
        object: ProgressLogger {
          override fun logError(message: String) {
          }

          override fun logInfo(message: String) {
            println(message)
          }

          override fun logVerbose(message: String) {
          }
        }
      )
    }
  }
}
