package com.giyeok.bibix.plugins.protobuf

import com.giyeok.bibix.base.BuildEnv
import com.giyeok.bibix.base.OS
import com.giyeok.bibix.base.Architecture
import com.giyeok.bibix.base.BibixValue
import com.giyeok.bibix.base.BuildContext
import com.giyeok.bibix.base.EnumValue
import com.giyeok.bibix.base.StringValue

class ProtocDownloadUrl {
  fun build(context: BuildContext): BibixValue {
    val arch = context.buildEnv.arch
    val version = (context.arguments.getValue("protobufVersion") as StringValue).value
    val noMajorVersion = version.split('.').drop(1)
    val compilerVersion = if (noMajorVersion[0].toInt() >= 21) {
      noMajorVersion.joinToString(".")
    } else {
      version
    }
    val base = "https://github.com/protocolbuffers/protobuf/releases/download/v$compilerVersion"
    val filename = when (val os = context.buildEnv.os) {
      is OS.Linux -> when (arch) {
        Architecture.X86_64 ->
          "protoc-$compilerVersion-linux-x86_64.zip"
        else -> throw IllegalArgumentException("Unsupported arch on linux: $arch")
      }

      is OS.MacOSX -> when (arch) {
        Architecture.Aarch_64 ->
          "protoc-$compilerVersion-osx-aarch_64.zip"
        else -> throw IllegalArgumentException("Unsupported arch on osx: $arch")
      }

      else -> throw IllegalArgumentException("Unsupported os: $os")
    }
    return StringValue("$base/$filename")
  }
}
