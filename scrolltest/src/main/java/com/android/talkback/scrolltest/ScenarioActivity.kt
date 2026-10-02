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

import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2

/** Shows one [Scenario], named by [EXTRA_SCENARIO]. */
class ScenarioActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val scenario =
      intent.getStringExtra(EXTRA_SCENARIO)?.let { name ->
        Scenario.entries.firstOrNull { it.name == name }
      } ?: Scenario.SCROLL_VIEW
    title = scenario.title
    when (scenario) {
      Scenario.SCROLL_VIEW -> setContentView(ScrollView(this).apply { addView(lines(1..60)) })
      Scenario.NESTED_SCROLL_VIEW ->
        setContentView(NestedScrollView(this).apply { addView(lines(1..60)) })
      Scenario.SCROLL_VIEW_WITH_PAGER -> setContentView(scrollViewWithPager())
      Scenario.SCROLL_VIEW_WITH_COLLAPSED_SECTION -> setContentView(scrollViewWithCollapsedSection())
      Scenario.COMPOSE_SCROLL -> setContent { composeLines() }
      Scenario.LIST -> setContentView(list(reverse = false, label = "Item"))
      Scenario.CHAT_LIST -> setContentView(list(reverse = true, label = "Message"))
    }
  }

  private fun scrollViewWithPager(): View {
    val content = lines(1..15)
    content.addView(
      ViewPager2(this).apply {
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(320))
        // Lays out the next page off to the side, so its lines are in the tree but not on screen.
        offscreenPageLimit = 1
        adapter = PagerAdapter()
      }
    )
    addLines(content, 16..40)
    return ScrollView(this).apply { addView(content) }
  }

  private fun scrollViewWithCollapsedSection(): View {
    val content = lines(1..15)
    content.addView(line("Section, collapsed"))
    // No height, but its lines keep theirs, so they report a size in their parent while hidden.
    val collapsed = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    for (i in 1..5) {
      collapsed.addView(line("Hidden line $i"))
    }
    content.addView(collapsed, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0))
    addLines(content, 16..40)
    return ScrollView(this).apply { addView(content) }
  }

  @androidx.compose.runtime.Composable
  private fun composeLines() {
    // Keeps the lines clear of the system bars, which an app targeting Android 15 draws behind.
    Column(
      Modifier.fillMaxSize()
        .windowInsetsPadding(WindowInsets.safeDrawing)
        .verticalScroll(rememberScrollState())
    ) {
      for (i in 1..60) {
        BasicText(
          "Item $i",
          // Full width, like the lines on the other screens, so touching anywhere on a line finds it.
          Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
          style = TextStyle(fontSize = 20.sp),
        )
      }
    }
  }

  private fun list(reverse: Boolean, label: String): View =
    RecyclerView(this).apply {
      layoutManager = LinearLayoutManager(this@ScenarioActivity, RecyclerView.VERTICAL, reverse)
      adapter = LineAdapter(label, 100)
    }

  private fun lines(range: IntRange): LinearLayout {
    val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    addLines(layout, range)
    return layout
  }

  private fun addLines(layout: LinearLayout, range: IntRange) {
    for (i in range) {
      layout.addView(line("Item $i"))
    }
  }

  private fun line(text: String) =
    TextView(this).apply {
      this.text = text
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
      setPadding(dp(16), dp(14), dp(16), dp(14))
    }

  private fun dp(value: Int) =
    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics)
      .toInt()

  private inner class LineAdapter(private val label: String, private val count: Int) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
      // Full width, as a list's rows are otherwise only as wide as their text, so touching anywhere
      // on a row finds it.
      val row = line("")
      row.layoutParams =
        RecyclerView.LayoutParams(
          ViewGroup.LayoutParams.MATCH_PARENT,
          ViewGroup.LayoutParams.WRAP_CONTENT,
        )
      return object : RecyclerView.ViewHolder(row) {}
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
      (holder.itemView as TextView).text = "$label ${position + 1}"
    }

    override fun getItemCount() = count
  }

  private inner class PagerAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
      val page =
        LinearLayout(this@ScenarioActivity).apply {
          orientation = LinearLayout.VERTICAL
          layoutParams =
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
      return object : RecyclerView.ViewHolder(page) {}
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
      val page = holder.itemView as LinearLayout
      page.removeAllViews()
      for (i in 1..4) {
        page.addView(line("Page ${position + 1}, line $i"))
      }
    }

    override fun getItemCount() = 3
  }

  companion object {
    const val EXTRA_SCENARIO = "scenario"
  }
}
