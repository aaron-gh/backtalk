/*
 * Copyright 2026 Backtalk contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.android.accessibility.talkback.actor.gemini

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Small models follow the screen answer format loosely: they write a node as a bare number where
 * the format asks for an object, an image as an object where it asks for a string, or forget to
 * close a list. This puts those cases right so that the answer parses. An answer that already has
 * the right shape is returned as it is.
 */
internal object ScreenJsonRepair {
  private val IMAGE_TEXT_KEYS = listOf("description", "text", "label", "caption")

  fun repair(json: String): String {
    val root =
      try {
        JSONObject(json)
      } catch (_: JSONException) {
        try {
          JSONObject(balance(json))
        } catch (_: JSONException) {
          return json
        }
      }
    return try {
      root.optJSONArray("top_images")?.let { root.put("top_images", images(it)) }
      for (key in listOf("top_ui_elements", "ui_elements")) {
        root.optJSONArray(key)?.let { root.put(key, elements(it)) }
      }
      root.toString()
    } catch (_: JSONException) {
      json
    }
  }

  /**
   * Closes what the model left open and drops what does not belong: a closer that does not match
   * the innermost opener first closes the openers inside it, and a stray closer is ignored. A
   * string that was never ended is ended, and a trailing comma is removed.
   */
  fun balance(text: String): String {
    val out = StringBuilder()
    val open = ArrayDeque<Char>()
    var inString = false
    var escaped = false
    for (c in text) {
      if (inString) {
        out.append(c)
        if (escaped) escaped = false else if (c == '\\') escaped = true else if (c == '"') inString = false
        continue
      }
      when (c) {
        '"' -> {
          inString = true
          out.append(c)
        }
        '{' -> {
          open.addLast('}')
          out.append(c)
        }
        '[' -> {
          open.addLast(']')
          out.append(c)
        }
        '}',
        ']' -> {
          if (c in open) {
            while (open.last() != c) appendCloser(out, open.removeLast())
            appendCloser(out, open.removeLast())
          }
        }
        else -> out.append(c)
      }
    }
    if (inString) out.append('"')
    while (open.isNotEmpty()) appendCloser(out, open.removeLast())
    return out.toString()
  }

  private fun appendCloser(out: StringBuilder, closer: Char) {
    var end = out.length
    while (end > 0 && out[end - 1].isWhitespace()) end--
    out.setLength(end)
    if (out.isNotEmpty() && out.last() == ',') out.setLength(out.length - 1)
    // A key with no value yet, such as {"a":, would not parse.
    if (out.isNotEmpty() && out.last() == ':') out.append("null")
    out.append(closer)
  }

  /**
   * The text to show when an answer cannot be parsed at all: the value of [key] if the model wrote
   * it, otherwise the whole answer with the JSON punctuation taken out.
   */
  fun fallbackText(text: String, key: String): String {
    val value = Regex("\"" + key + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)").find(text)?.groupValues?.get(1)
    if (!value.isNullOrBlank()) {
      return value.replace("\\\"", "\"").replace("\\n", " ").replace("\\\\", "\\").trim()
    }
    return text.replace(Regex("[{}\\[\\]\"]"), " ").replace(Regex("\\s+"), " ").trim()
  }

  private fun images(array: JSONArray): JSONArray {
    val fixed = JSONArray()
    for (i in 0 until array.length()) {
      when (val image = array.get(i)) {
        is String -> fixed.put(image)
        is JSONObject ->
          IMAGE_TEXT_KEYS.firstNotNullOfOrNull { image.optString(it).takeIf { s -> s.isNotEmpty() } }
            ?.let { fixed.put(it) }
        JSONObject.NULL -> {}
        else -> fixed.put(image.toString())
      }
    }
    return fixed
  }

  private fun elements(array: JSONArray): JSONArray {
    val fixed = JSONArray()
    for (i in 0 until array.length()) {
      val element = array.optJSONObject(i) ?: continue
      if (element.has("potentiallyMatchingNodes")) {
        element.put("potentiallyMatchingNodes", nodes(element.get("potentiallyMatchingNodes")))
      }
      fixed.put(element)
    }
    return fixed
  }

  private fun nodes(value: Any): JSONArray {
    val list = value as? JSONArray ?: JSONArray().put(value)
    val fixed = JSONArray()
    for (i in 0 until list.length()) {
      when (val node = list.get(i)) {
        is JSONObject -> fixed.put(node)
        is Number -> fixed.put(JSONObject().put("uniqueId", node.toInt()))
        is String ->
          node.trim().toIntOrNull()?.let { fixed.put(JSONObject().put("uniqueId", it)) }
      }
    }
    return fixed
  }
}
