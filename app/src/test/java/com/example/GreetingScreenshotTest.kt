package com.example

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.example.data.model.DeviceHardwareInfo
import com.example.ui.components.DeviceSpecsCard
import com.example.ui.theme.VipoTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [34])
class GreetingScreenshotTest {

    @get:Rule val composeTestRule = createComposeRule()

    @Test
    fun greeting_screenshot() {
        val hw = DeviceHardwareInfo(
            totalRamGb = 8.0f,
            availableRamGb = 4.2f,
            cpuCores = 8,
            freeStorageGb = 45.0f,
            totalStorageGb = 128.0f,
            deviceName = "Google Pixel 8"
        )
        composeTestRule.setContent {
            VipoTheme {
                DeviceSpecsCard(hardwareInfo = hw)
            }
        }

        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/greeting.png")
    }
}
