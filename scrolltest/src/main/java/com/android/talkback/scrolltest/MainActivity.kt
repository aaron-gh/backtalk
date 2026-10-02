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

package com.android.talkback.scrolltest

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.activity.ComponentActivity

/** Lists the screens to test with. */
class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val buttons = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    for (scenario in Scenario.entries) {
      buttons.addView(
        Button(this).apply {
          text = scenario.title
          contentDescription = "${scenario.title}. ${scenario.description}"
          setOnClickListener {
            startActivity(
              Intent(this@MainActivity, ScenarioActivity::class.java)
                .putExtra(ScenarioActivity.EXTRA_SCENARIO, scenario.name)
            )
          }
        }
      )
    }
    setContentView(ScrollView(this).apply { addView(buttons) })
  }
}
