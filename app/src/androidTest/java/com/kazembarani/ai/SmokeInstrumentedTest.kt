package com.kazembarani.ai

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SmokeInstrumentedTest {
    @Test
    fun appContextHasExpectedPackage() {
        assertEquals("com.kazembarani.ai", InstrumentationRegistry.getInstrumentation().targetContext.packageName)
    }

    @Test
    fun v16StudioActivityLaunches() {
        // V16StudioActivity may intentionally finish immediately when the API key is missing
        // and redirect to WelcomeActivity. The smoke test should therefore verify launch
        // without requiring the Activity to still be alive at the onActivity callback.
        ActivityScenario.launch<V16StudioActivity>(Intent(context(), V16StudioActivity::class.java)).use { }
    }

    @Test
    fun welcomeActivityLaunches() {
        ActivityScenario.launch<WelcomeActivity>(Intent(context(), WelcomeActivity::class.java)).use { }
    }

    @Test
    fun studioActivityLaunches() {
        ActivityScenario.launch<StudioActivity>(Intent(context(), StudioActivity::class.java)).use { }
    }

    private fun context() = InstrumentationRegistry.getInstrumentation().targetContext
}
