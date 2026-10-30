package com.khanz1.serlok

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.khanz1.serlok.ui.screens.onboarding.OnboardingScreen
import com.khanz1.serlok.ui.theme.SerlokTheme
import org.junit.Rule
import org.junit.Test

class OnboardingAccessibilityTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun firstPage_hasBrandHeadingAndPrimaryAction() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        compose.setContent {
            SerlokTheme { OnboardingScreen(onFinish = {}) }
        }
        compose.onNodeWithContentDescription(context.getString(R.string.app_name)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.onboarding_location_title)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.onboarding_next)).assertIsDisplayed()
        compose.assertNoUnlabeledActions()
    }

    @Test
    fun onboarding_movesToShakePageAndShowsStartAction() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        compose.setContent {
            SerlokTheme { OnboardingScreen(onFinish = {}) }
        }

        compose.onNodeWithText(context.getString(R.string.onboarding_next)).performClick()

        compose.onNodeWithText(context.getString(R.string.onboarding_sos_title)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.onboarding_start)).assertIsDisplayed()
        compose.assertNoUnlabeledActions()
    }
}
