package com.giyeok.bibix.plugins.zip

import com.giyeok.bibix.base.BuildContext
import com.giyeok.bibix.base.FileValue
import com.giyeok.bibix.base.NoneValue
import com.giyeok.bibix.base.StringValue
import java.nio.file.Files
import java.util.zip.GZIPOutputStream
import kotlin.io.path.outputStream

class Gzip {
  fun build(context: BuildContext): FileValue {
    val file = (context.arguments.getValue("file") as FileValue).file
    val outputFileNameValue = (context.arguments.getValue("outputFileName"))
    val outputFileName =
      if (outputFileNameValue == NoneValue) "${file.fileName}.gz" else (outputFileNameValue as StringValue).value
    val outputFile = context.destDirectory.resolve(outputFileName)

    if (context.hashChanged) {
      GZIPOutputStream(outputFile.outputStream().buffered()).use { zos ->
        Files.copy(file, zos)
      }
    }
    return FileValue(outputFile)
  }
}
