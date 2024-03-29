package com.giyeok.bibix.plugins.grpc

import com.giyeok.bibix.base.*
import java.nio.file.attribute.PosixFilePermission
import kotlin.io.path.absolutePathString
import kotlin.io.path.getPosixFilePermissions
import kotlin.io.path.setPosixFilePermissions
import kotlin.io.path.writeText

class KotlinPlugin {
  fun url(context: BuildContext): StringValue {
    val version = (context.arguments.getValue("version") as StringValue).value
    val url =
      "https://repo1.maven.org/maven2/io/grpc/protoc-gen-grpc-kotlin/$version/protoc-gen-grpc-kotlin-$version-jdk8.jar"

    return StringValue(url)
  }

  fun createEnv(context: BuildContext): FileValue {
    val envDirectory = context.destDirectory
    val pluginPath = envDirectory.resolve("protoc-gen-grpc-kotlin")

    if (context.hashChanged) {
      context.clearDestDirectory()
      val pluginJar = (context.arguments.getValue("pluginJar") as FileValue).file

      pluginPath.writeText(
        """
        #!/bin/sh
        java -jar ${pluginJar.absolutePathString()}
        """.trimIndent()
      )
      val prevPermissions = pluginPath.getPosixFilePermissions()
      pluginPath.setPosixFilePermissions(prevPermissions + PosixFilePermission.OWNER_EXECUTE)
    }
    return FileValue(pluginPath)
  }
}
