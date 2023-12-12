package com.giyeok.bibix.plugins.javafx

import com.giyeok.bibix.base.Architecture
import com.giyeok.bibix.base.BuildContext
import com.giyeok.bibix.base.OS
import com.giyeok.bibix.base.StringValue

class SdkDownloadUrl {
  fun build(context: BuildContext): StringValue {
    val arch = context.buildEnv.arch

    val version = (context.arguments.getValue("javafxVersion") as StringValue).value

    val url = when (val os = context.buildEnv.os) {
      is OS.MacOSX -> when (arch) {
        Architecture.Aarch_64 ->
          "https://download2.gluonhq.com/openjfx/$version/openjfx-${version}_osx-aarch64_bin-sdk.zip"

        else -> throw IllegalArgumentException("Unsupported arch on osx: $arch")
      }

      is OS.Linux -> when (arch) {
        Architecture.X86_64 ->
          "https://download2.gluonhq.com/openjfx/$version/openjfx-${version}_linux-x64_bin-sdk.zip"

        else -> throw IllegalArgumentException("Unsupported arch on linux: $arch")
      }

      else -> throw IllegalArgumentException("Unsupported arch: $arch")
    }
    return StringValue(url)
  }
}
