package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.CompatibilityLevel
import com.example.data.model.DeviceHardwareInfo
import com.example.download.DownloadTask
import com.example.repository.HardwareRepository
import com.example.repository.ModelRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read app name from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Vipo", appName)
    }

    @Test
    fun `load catalog parses successfully`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = ModelRepository(context)
        val catalog = repository.loadCatalog()

        assertTrue("Catalog should contain models", catalog.isNotEmpty())
        val llama = catalog.firstOrNull { it.id == "llama-3.2-1b-instruct" }
        assertNotNull("Llama 3.2 1B should be present", llama)
        assertEquals("Llama 3.2 1B Instruct", llama?.name)
        assertTrue("Variants should not be empty", llama?.variants?.isNotEmpty() == true)
    }

    @Test
    fun `hardware compatibility evaluation`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val hardwareRepo = HardwareRepository(context)

        val hw = DeviceHardwareInfo(
            totalRamGb = 8.0f,
            availableRamGb = 4.5f,
            cpuCores = 8,
            freeStorageGb = 32.0f,
            totalStorageGb = 128.0f,
            deviceName = "Test Device"
        )

        val comp1B = hardwareRepo.evaluateCompatibility(1.5f, hw)
        assertEquals(CompatibilityLevel.EXCELLENT, comp1B)

        val compHuge = hardwareRepo.evaluateCompatibility(12.0f, hw)
        assertEquals(CompatibilityLevel.MEMORY_RISK, compHuge)
    }

    @Test
    fun `download task formatting`() {
        assertEquals("0 B", DownloadTask.formatBytes(0L))
        assertEquals("512 KB", DownloadTask.formatBytes(512 * 1024L))
        assertEquals("774.0 MB", DownloadTask.formatBytes((774 * 1024 * 1024L)))
        assertEquals("2.45 GB", DownloadTask.formatBytes((2.45 * 1024 * 1024 * 1024).toLong()))
    }
}
