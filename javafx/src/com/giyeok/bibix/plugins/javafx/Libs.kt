package com.giyeok.bibix.plugins.javafx

import com.giyeok.bibix.base.*

class Libs {
  fun build(context: BuildContext): BuildRuleReturn {
    val javafxVersion = (context.arguments.getValue("javafxVersion") as StringValue).value
    return BuildRuleReturn.evalAndThen(
      "sdkDownloadUrl",
      mapOf(
        "javafxVersion" to context.arguments.getValue("javafxVersion"),
        "arch" to context.arguments.getValue("arch"),
      )
    ) { urlValue ->
      BuildRuleReturn.evalAndThen(
        "curl.download",
        mapOf("url" to urlValue)
      ) { downloadFileValue ->
        BuildRuleReturn.evalAndThen(
          "zip.unzip",
          mapOf("zipFile" to downloadFileValue)
        ) { unzipDirValue ->
          val unzipDir = (unzipDirValue as DirectoryValue).directory
          val libDir = unzipDir.resolve("javafx-sdk-$javafxVersion").resolve("lib")

          BuildRuleReturn.value(
            ClassInstanceValue(
              "com.giyeok.bibix.plugins.jvm",
              "ClassPkg",
              mapOf(
                "origin" to ClassInstanceValue(
                  "com.giyeok.bibix.plugins.jvm",
                  "LocalLib",
                  mapOf("path" to DirectoryValue(libDir))
                ),
                "cpinfo" to ClassInstanceValue(
                  "com.giyeok.bibix.plugins.jvm",
                  "ClassesInfo",
                  mapOf(
                    "classDirs" to SetValue(DirectoryValue(libDir)),
                    "resDirs" to SetValue()
                  )
                ),
                "deps" to SetValue(),
              )
            )
          )
        }
      }
    }
  }
}
