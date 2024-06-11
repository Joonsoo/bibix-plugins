package com.giyeok.bibix.plugins.zip

import com.giyeok.bibix.base.BuildContext
import com.giyeok.bibix.base.DirectoryValue
import com.giyeok.bibix.base.FileValue
import com.giyeok.bibix.base.NoneValue
import com.giyeok.bibix.base.SetValue
import com.giyeok.bibix.base.StringValue
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.Path
import kotlin.io.path.absolute
import kotlin.io.path.absolutePathString
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.outputStream
import kotlin.io.path.pathString
import kotlin.io.path.relativeTo
import kotlin.math.min

class Zip {
  fun build(context: BuildContext): FileValue {
    val files = (context.arguments.getValue("files") as SetValue).values
      .map { (it as FileValue).file.absolute() }
    val baseDirArg = context.arguments.getValue("baseDir")
    val baseDir = if (baseDirArg == NoneValue) {
      commonAncestorDir(files)
    } else {
      (baseDirArg as DirectoryValue).directory
    }
    check(files.all { it.startsWith(baseDir) }) { "Invalid baseDir" }

    val outputFileNameValue = (context.arguments.getValue("outputFileName"))
    val outputFileName =
      if (outputFileNameValue == NoneValue) "output.zip" else (outputFileNameValue as StringValue).value
    val outputFile = context.destDirectory.resolve(outputFileName)

    if (context.hashChanged) {
      ZipOutputStream(outputFile.outputStream().buffered()).use { zos ->
        for (file in files) {
          if (file.isRegularFile()) {
            val path = file.absolute().relativeTo(baseDir)
            zos.putNextEntry(ZipEntry(path.pathString))
            Files.copy(file, zos)
          }
        }
      }
    }
    return FileValue(outputFile)
  }

  private fun commonAncestorDir(files: List<Path>): Path {
    var dir = files.first().parent
    for (file in files.drop(1)) {
      if (!file.parent.startsWith(dir)) {
        dir = commonAncestorOf(file.parent, dir)
      }
    }
    return dir
  }

  fun commonAncestorOf(a: Path, b: Path): Path {
    val names = mutableListOf<String>()
    for (i in 0..<min(a.nameCount, b.nameCount)) {
      if (a.getName(i) == b.getName(i)) {
        names.add(a.getName(i).name)
      } else {
        break
      }
    }
    return Path("/" + names.joinToString("/"))
  }
}

fun main() {
  println(Zip().commonAncestorOf(Path("/a/b/c"), Path("/a/x/y")))
  println(Zip().commonAncestorOf(Path("/a/b/c"), Path("/a/b/c")))
  println(Zip().commonAncestorOf(Path("/"), Path("/a/b/c")))
}
