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

package com.google.android.accessibility.talkback.scripting

import android.os.Handler
import android.os.SystemClock
import org.json.JSONObject

class ScriptSpeechHooks(private val handler: Handler) {
  @Volatile private var chains: Map<String, List<ScriptRuntime>> = emptyMap()

  fun update(runtimes: List<ScriptRuntime>) {
    chains =
      PERMISSIONS.mapValues { (hook, permission) ->
        runtimes.filter {
          hook in it.hooks &&
            (permission == null || it.allowed(permission, ScriptRuntime.handlerName(hook)))
        }
      }
  }

  fun clear() {
    chains = emptyMap()
  }

  fun hasListeners(hook: String): Boolean = chains[hook].orEmpty().isNotEmpty()

  fun rewrite(
    hook: String,
    input: String,
    accepts: (ScriptRuntime) -> Boolean = { true },
    arg: (ScriptRuntime) -> Any?,
  ): String? {
    val chain = chains[hook].orEmpty().filter(accepts).ifEmpty { return null }
    if (ScriptThread.heldUpFor(WAIT_MS)) return null
    return handler.await(WAIT_MS, hook) { runChain(chain, hook, input, arg) }
  }

  private fun runChain(
    chain: List<ScriptRuntime>,
    hook: String,
    input: String,
    arg: (ScriptRuntime) -> Any?,
  ): String? {
    val handlerName = ScriptRuntime.handlerName(hook)
    val text =
      chain.filter { it.isLoaded }.fold(input) { text, runtime ->
        val start = SystemClock.uptimeMillis()
        val data = jsonObject("arg" to arg(runtime), "text" to text)
        val result = runtime.dispatch(hook, data, LIMIT_MS)
        val elapsed = SystemClock.uptimeMillis() - start
        if (elapsed > WAIT_MS) {
          runtime.warnOnce(
            "slow $hook",
            "$handlerName took $elapsed ms, so Backtalk may have spoken without it",
          )
        }
        when {
          result == null -> text
          !runtime.allowed(ScriptPermission.SPEECH, "Changing speech from $handlerName") -> text
          else -> JSONObject(result).optString("text", text)
        }
      }
    return text.takeIf { it != input }
  }

  private companion object {
    const val WAIT_MS = 30L
    const val LIMIT_MS = 250L
    val PERMISSIONS: Map<String, ScriptPermission?> =
      mapOf(
        // The focus hook is given the item and what Backtalk will say for it, in every app.
        "focus" to ScriptPermission.SCREEN,
        "speech" to ScriptPermission.SPEECH,
        "notification" to ScriptPermission.NOTIFICATIONS,
        "announcement" to ScriptPermission.SCREEN,
      )
  }
}
