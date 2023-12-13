package com.giyeok.bibix.plugins.javafx

import com.giyeok.bibix.base.*
import java.nio.file.Path

class Libs {
  fun classPkgFrom(libDir: Path, moduleName: String): ClassInstanceValue {
    val jarFile = libDir.resolve("javafx.$moduleName.jar")
    return ClassInstanceValue(
      "com.giyeok.bibix.plugins.jvm",
      "ClassPkg",
      mapOf(
        "origin" to ClassInstanceValue(
          "com.giyeok.bibix.plugins.jvm",
          "LocalLib",
          mapOf("path" to PathValue(jarFile))
        ),
        "cpinfo" to ClassInstanceValue(
          "com.giyeok.bibix.plugins.jvm",
          "JarInfo",
          mapOf(
            "jar" to FileValue(jarFile),
          )
        ),
        "deps" to SetValue(),
        "nativeLibDirs" to SetValue(DirectoryValue(libDir))
      )
    )
  }

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

          //  base: jvm.ClassPkg,
          //  controls: jvm.ClassPkg,
          //  fxml: jvm.ClassPkg,
          //  graphics: jvm.ClassPkg,
          //  media: jvm.ClassPkg,
          //  swing: jvm.ClassPkg,
          //  web: jvm.ClassPkg,
          BuildRuleReturn.value(
            ClassInstanceValue(
              "com.giyeok.bibix.plugins.javafx",
              "JavaFxModules",
              mapOf(
                "base" to classPkgFrom(libDir, "base"),
                "controls" to classPkgFrom(libDir, "controls"),
                "fxml" to classPkgFrom(libDir, "fxml"),
                "graphics" to classPkgFrom(libDir, "graphics"),
                "media" to classPkgFrom(libDir, "media"),
                "swing" to classPkgFrom(libDir, "swing"),
                "web" to classPkgFrom(libDir, "web"),
              )
            )
          )
        }
      }
    }
  }
}
