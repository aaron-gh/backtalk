package com.google.android.accessibility.talkback.updatetasks

import com.google.android.accessibility.talkback.updatetasks.Versions.VERSION_UNKNOWN
import com.google.android.libraries.accessibility.utils.log.LogUtils

/**
 * Holds all update tasks for different versions.
 *
 * @param targetVersion the version that we migrate to
 * @param fromVersion the version that we start to do migration task
 * @param runForExistingUserOnly indicates whether we only do migration tasks on non first time
 *   users
 * @param onMigrate the migration task that we perform if the **previous version** is between
 *   [fromVersion] and [targetVersion]
 * @param onMatchCurrentVersion the migration task that we perform if the **current version** is
 *   [targetVersion]
 */
data class VersionUpdateTask(
  val targetVersion: Version,
  val fromVersion: Version = Versions.VERSION_0,
  val runForExistingUserOnly: Boolean = true,
  val onMigrate: (() -> Unit)? = null,
  val onMatchCurrentVersion: (() -> Unit)? = null,
) {

  companion object {
    private const val TAG = "VersionUpdateTask"
  }

  internal fun matchCurrentVersion(currentVersion: Version): Boolean {
    return targetVersion == currentVersion
  }

  internal fun shouldMigrate(previousVersion: Version): Boolean {
    return when {
      previousVersion.virtualTargetVersion > targetVersion.virtualTargetVersion -> {
        LogUtils.e(
          TAG,
          "previousVersion ($previousVersion) shouldn't larger than targetVersion ($targetVersion)",
        )
        false
      }
      runForExistingUserOnly && previousVersion == Versions.VERSION_FIRST_TIME_USER -> false
      else ->
        fromVersion.virtualTargetVersion <= previousVersion.virtualTargetVersion &&
          previousVersion.virtualTargetVersion < targetVersion.virtualTargetVersion
    }
  }
}

object Versions {
  val VERSION_FIRST_TIME_USER = Version(0, 0, 0)
  val VERSION_0 = Version(0, 0, 0)
  val VERSION_12_0_0 = Version(12, 0, 0)
  val VERSION_13_0_0 = Version(13, 0, 0)
  val VERSION_14_0_0 = Version(14, 0, 0)
  val VERSION_15_0_0 = Version(15, 0, 0)
  // Denotes that we fail to parse the version name.
  val VERSION_UNKNOWN = Version(-1, -1, -1)
}

data class Version(val major: Int, val minor: Int, val build: Int) {
  val virtualTargetVersion: Int = convertToVirtualVersionCode(this)
}

internal fun convertToVirtualVersionCode(version: Version): Int {
  return 10000 * version.major + 100 * version.minor + version.build
}

internal fun convertToVersion(versionName: String?): Version {
  if (versionName == null) return Versions.VERSION_FIRST_TIME_USER
  val parts = versionName.substringBefore('-').split(".")
  // Backtalk releases can use a single build number, such as 600.
  if (parts.size != 1 && parts.size != 3) return VERSION_UNKNOWN
  val numbers = parts.map { it.toIntOrNull() ?: return VERSION_UNKNOWN }
  if (numbers.any { it < 0 }) return VERSION_UNKNOWN
  return Version(numbers[0], numbers.getOrElse(1) { 0 }, numbers.getOrElse(2) { 0 })
}
