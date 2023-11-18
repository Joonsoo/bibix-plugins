package com.giyeok.bibix.plugins.tar

import com.giyeok.bibix.base.BuildContext
import com.giyeok.bibix.base.DirectoryValue
import com.giyeok.bibix.base.FileValue
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import kotlin.io.path.createDirectory
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream

class Decompress {
  fun build(context: BuildContext): DirectoryValue {
    val tarFile = (context.arguments.getValue("tarFile") as FileValue).file
    val destDirectory = context.destDirectory

    if (context.hashChanged) {
      TarArchiveInputStream(tarFile.inputStream().buffered()).use { tis ->
        var entry = tis.nextEntry
        while (entry != null) {
          val dest = destDirectory.resolve(entry.name)
          if (entry.isDirectory) {
            dest.createDirectory()
          } else {
            dest.outputStream().buffered().use { os ->
              val bytes = ByteArray(8192)
              var size = tis.read(bytes)
              while (size > 0) {
                os.write(bytes, 0, size)
                size = tis.read(bytes)
              }
            }
          }
          entry = tis.nextEntry
        }
      }
    }
    return DirectoryValue(destDirectory)
  }
}
