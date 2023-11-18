package com.giyeok.bibix.plugins.xz

import com.giyeok.bibix.base.BuildContext
import com.giyeok.bibix.base.FileValue
import org.tukaani.xz.XZInputStream
import kotlin.io.path.inputStream
import kotlin.io.path.name
import kotlin.io.path.outputStream

class Decompress {
  fun build(context: BuildContext): FileValue {
    val xzFile = (context.arguments.getValue("xzFile") as FileValue).file
    val destDirectory = context.destDirectory
    val destFile = destDirectory.resolve(xzFile.fileName.name.substringBeforeLast('.'))

    if (context.hashChanged) {
      XZInputStream(xzFile.inputStream().buffered()).use { xis ->
        destFile.outputStream().buffered().use { os ->
          val buf = ByteArray(8192)
          var size = xis.read(buf)
          while (size >= 0) {
            os.write(buf, 0, size)
            size = xis.read(buf)
          }
        }
      }
    }
    return FileValue(destFile)
  }
}
