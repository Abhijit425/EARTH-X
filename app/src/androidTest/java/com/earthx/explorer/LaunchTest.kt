package com.earthx.explorer

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LaunchTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test fun launchesAndShowsNavigation() {
        rule.waitForIdle()
        assertTrue(rule.onAllNodesWithText("Countries").fetchSemanticsNodes().isNotEmpty())
    }
}
