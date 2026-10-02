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

package com.google.android.accessibility.talkback.migration

import com.google.android.accessibility.talkback.migration.AppIdMove.InstallKind
import org.junit.Assert.assertEquals
import org.junit.Test

class AppIdMoveTest {
  private val old = AppIdMove.OLD_PACKAGE
  private val new = AppIdMove.NEW_PACKAGE

  @Test
  fun samePackageUpdates() {
    assertEquals(InstallKind.UPDATE, AppIdMove.installKind(old, old, sameSigner = true))
    assertEquals(InstallKind.UPDATE, AppIdMove.installKind(new, new, sameSigner = true))
  }

  @Test
  fun newAppIdMoves() {
    assertEquals(InstallKind.MOVE, AppIdMove.installKind(new, old, sameSigner = true))
  }

  @Test
  fun newAppIdWithAnotherKeyIsRefused() {
    assertEquals(InstallKind.REFUSE, AppIdMove.installKind(new, old, sameSigner = false))
  }

  @Test
  fun otherPackagesAreRefused() {
    assertEquals(InstallKind.REFUSE, AppIdMove.installKind("com.example.app", old, true))
    assertEquals(InstallKind.REFUSE, AppIdMove.installKind(old, new, true))
    assertEquals(InstallKind.REFUSE, AppIdMove.installKind(null, old, true))
    assertEquals(InstallKind.REFUSE, AppIdMove.installKind("", old, true))
  }

  @Test
  fun authorityAndServiceFollowThePackage() {
    assertEquals("com.android.talkback.migration", AppIdMove.authority(old))
    assertEquals(
      "fyi.quin.backtalk/com.google.android.marvin.talkback.TalkBackService",
      AppIdMove.serviceName(new),
    )
  }
}
