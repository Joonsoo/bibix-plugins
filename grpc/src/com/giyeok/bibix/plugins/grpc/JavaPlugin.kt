package com.giyeok.bibix.plugins.grpc

import com.giyeok.bibix.base.*
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission
import kotlin.io.path.absolute
import kotlin.io.path.getPosixFilePermissions
import kotlin.io.path.setPosixFilePermissions

class JavaPlugin {
  fun url(context: BuildContext): StringValue {
    val version = (context.arguments.getValue("version") as StringValue).value
    val arch = context.buildEnv.arch

    val url = when (val os = context.buildEnv.os) {
      is OS.Linux -> when (arch) {
        Architecture.X86_64 ->
          "https://repo1.maven.org/maven2/io/grpc/protoc-gen-grpc-java/$version/protoc-gen-grpc-java-$version-linux-x86_64.exe"
        else -> throw IllegalArgumentException("Unsupported arch: $arch")
      }
      is OS.MacOSX -> when (arch) {
        Architecture.Aarch_64 ->
          "https://repo1.maven.org/maven2/io/grpc/protoc-gen-grpc-java/$version/protoc-gen-grpc-java-$version-osx-aarch_64.exe"
        else -> throw IllegalArgumentException("Unsupported arch: $arch")
      }
      else -> throw IllegalArgumentException("Unsupported os: $os")
    }
    return StringValue(url)
  }

  fun createEnv(context: BuildContext): FileValue {
    val envDirectory = context.clearDestDirectory()
    val pluginPath = envDirectory.resolve("protoc-gen-grpc-java")

    if (context.hashChanged) {
      val pluginExe = (context.arguments.getValue("pluginExe") as FileValue).file
      val prevPermissions = pluginExe.getPosixFilePermissions()
      pluginExe.setPosixFilePermissions(prevPermissions + PosixFilePermission.OWNER_EXECUTE)

      Files.createSymbolicLink(pluginPath, pluginExe.absolute())
    }
    return FileValue(pluginPath)
  }
}
