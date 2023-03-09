package com.giyeok.bibix.plugins.mustache

import com.github.mustachejava.DefaultMustacheFactory
import com.giyeok.bibix.base.*
import kotlin.io.path.bufferedReader
import kotlin.io.path.bufferedWriter

class Render {
  fun build(context: BuildContext): BibixValue {
    val template = (context.arguments.getValue("template") as FileValue).file
    val values = (context.arguments.getValue("values") as ListValue).values
    val resultFileName = (context.arguments.getValue("resultFileName") as StringValue).value
    val resultFile = context.destDirectory.resolve(resultFileName)

    val scopes = mutableMapOf<String, Any>()
    values.forEach { pairValue ->
      val pair = (pairValue as TupleValue)
      check(pair.values.size == 2)
      val key = (pair.values[0] as StringValue).value
      when (val value = pair.values[1]) {
        is StringValue ->
          scopes[key] = value.value

        else -> TODO()
      }
    }
    // TODO values to scopes

    val mustacheFactory = DefaultMustacheFactory()
    template.bufferedReader().use { reader ->
      val mustache = mustacheFactory.compile(reader, "??")
      resultFile.bufferedWriter().use { writer ->
        mustache.execute(writer, scopes)
      }
    }

    return FileValue(resultFile)
  }
}
