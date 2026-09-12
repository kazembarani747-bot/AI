package com.kazembarani.ai

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SmokeInstrumentedTest {
    @Test
    fun appContextHasExpectedPackage() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.kazembarani.ai", context.packageName)
    }

    @Test
    fun studioActivityLaunches() {
        ActivityScenario.launch<StudioActivity>(Intent(context(), StudioActivity::class.java)).use { scenario ->
            scenario.onActivity { activity -> assertNotNull(activity) }
        }
    }

    private fun context() = InstrumentationRegistry.getInstrumentation().targetContext
}
