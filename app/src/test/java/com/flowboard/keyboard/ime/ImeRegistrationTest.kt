package com.flowboard.keyboard.ime

import android.content.Intent
import android.content.pm.PackageManager
import com.flowboard.keyboard.ui.KeyboardView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Framework-level checks against the merged manifest and the real service class.
 * These run on the JVM through Robolectric; they are not a substitute for installing the
 * APK on a device, but they do exercise Android's own registration and lifecycle code.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ImeRegistrationTest {

    @Test
    fun `service is discoverable as an input method`() {
        val context = RuntimeEnvironment.getApplication()
        val services = context.packageManager.queryIntentServices(
            Intent("android.view.InputMethod"),
            PackageManager.GET_META_DATA
        )
        val entry = services.firstOrNull {
            it.serviceInfo.name == FlowBoardImeService::class.java.name
        }
        assertNotNull("FlowBoard's IME service is not registered", entry)
        assertEquals("android.permission.BIND_INPUT_METHOD", entry!!.serviceInfo.permission)
        assertNotNull(
            "android.view.im metadata (res/xml/method.xml) is missing",
            entry.serviceInfo.loadXmlMetaData(context.packageManager, "android.view.im")
        )
    }

    @Test
    fun `service starts and creates the keyboard view`() {
        val service = Robolectric.buildService(FlowBoardImeService::class.java).create().get()
        val view = service.onCreateInputView()
        assertNotNull("onCreateInputView returned null", view)
        assertTrue("input view is not a KeyboardView", view is KeyboardView)
    }
}
