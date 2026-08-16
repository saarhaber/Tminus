package com.saarlabs.tminus.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test

/**
 * Records the classes and methods used during startup and the first interactions, so AOT can
 * compile them ahead of time instead of leaving the first run interpreted.
 *
 * Regenerate with:
 * ```
 * ./gradlew :baselineprofile:generateBaselineProfile
 * ```
 * This requires a rootable system image (AOSP or google_apis, **not** Play Store).
 */
class BaselineProfileGenerator {

    @get:Rule val rule = BaselineProfileRule()

    @Test
    fun generate() =
        rule.collect(packageName = PACKAGE_NAME, includeInStartupProfile = true) {
            pressHome()
            startActivityAndWait()

            // Home: wait for the first frame's content before moving on.
            device.wait(Until.hasObject(By.textContains("tMinus")), TIMEOUT_MS)

            // The stop picker is the heaviest screen: it builds and searches the ~9 000-stop index.
            device.findObject(By.textContains("Commutes"))?.click()
            device.wait(Until.hasObject(By.res("android:id/content")), TIMEOUT_MS)
            device.waitForIdle()
            device.pressBack()

            // Settings exercises the theme and typography paths.
            device.findObject(By.text("Settings"))?.click()
            device.waitForIdle()
        }

    private companion object {
        const val PACKAGE_NAME = "com.saarlabs.tminus"
        const val TIMEOUT_MS = 10_000L
    }
}
