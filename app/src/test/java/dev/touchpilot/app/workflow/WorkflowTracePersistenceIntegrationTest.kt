package dev.touchpilot.app.workflow

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.IntentSender
import android.content.ServiceConnection
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.UserHandle
import android.view.Display
import java.io.File
import java.io.InputStream
import java.util.concurrent.Executor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Integration test that verifies the full workflow trace persistence lifecycle:
 * save to store → persist to disk → reload from disk on new store instance.
 */
class WorkflowTracePersistenceIntegrationTest {
    private fun mockContext(): Context {
        val testDir = File(System.getProperty("java.io.tmpdir"), "workflow-integration-test-${System.currentTimeMillis()}")
        return StubContext(testDir.apply { mkdirs() })
    }

    private class StubContext(private val filesDir: File) : Context() {
        override fun getFilesDir(): File = filesDir
        override fun getApplicationContext(): Context = this
        override fun getPackageName(): String = "dev.touchpilot.app.test"
        override fun getAssets() = throw UnsupportedOperationException()
        override fun getResources() = throw UnsupportedOperationException()
        override fun getPackageManager() = throw UnsupportedOperationException()
        override fun getContentResolver() = throw UnsupportedOperationException()
        override fun getMainLooper() = throw UnsupportedOperationException()
        override fun getApplicationInfo() = throw UnsupportedOperationException()
        override fun getTheme() = throw UnsupportedOperationException()
        override fun setTheme(resid: Int) = throw UnsupportedOperationException()
        override fun getClassLoader() = throw UnsupportedOperationException()
        override fun getPackageResourcePath() = throw UnsupportedOperationException()
        override fun getPackageCodePath() = throw UnsupportedOperationException()
        override fun getSharedPreferences(name: String?, mode: Int) = throw UnsupportedOperationException()
        override fun openFileInput(name: String?) = throw UnsupportedOperationException()
        override fun openFileOutput(name: String?, mode: Int) = throw UnsupportedOperationException()
        override fun deleteFile(name: String?) = throw UnsupportedOperationException()
        override fun getFileStreamPath(name: String?) = throw UnsupportedOperationException()
        override fun fileList() = throw UnsupportedOperationException()
        override fun getDataDir() = throw UnsupportedOperationException()
        override fun getDir(name: String?, mode: Int) = throw UnsupportedOperationException()
        override fun getDatabasePath(name: String?) = throw UnsupportedOperationException()
        override fun databaseList() = throw UnsupportedOperationException()
        override fun getWallpaper() = throw UnsupportedOperationException()
        override fun peekWallpaper() = throw UnsupportedOperationException()
        override fun getWallpaperDesiredMinimumWidth() = throw UnsupportedOperationException()
        override fun getWallpaperDesiredMinimumHeight() = throw UnsupportedOperationException()
        override fun setWallpaper(bitmap: Bitmap?) = throw UnsupportedOperationException()
        override fun setWallpaper(data: InputStream?) = throw UnsupportedOperationException()
        override fun clearWallpaper() = throw UnsupportedOperationException()
        override fun startActivity(intent: Intent?) = throw UnsupportedOperationException()
        override fun startActivity(intent: Intent?, options: Bundle?) = throw UnsupportedOperationException()
        override fun startActivities(intents: Array<out Intent>?) = throw UnsupportedOperationException()
        override fun startActivities(intents: Array<out Intent>?, options: Bundle?) = throw UnsupportedOperationException()
        override fun startIntentSender(intent: IntentSender?, fillInIntent: Intent?, flagsMask: Int, flagsValues: Int, extraFlags: Int) = throw UnsupportedOperationException()
        override fun startIntentSender(intent: IntentSender?, fillInIntent: Intent?, flagsMask: Int, flagsValues: Int, extraFlags: Int, options: Bundle?) = throw UnsupportedOperationException()
        override fun sendBroadcast(intent: Intent?) = throw UnsupportedOperationException()
        override fun sendBroadcast(intent: Intent?, receiverPermission: String?) = throw UnsupportedOperationException()
        override fun sendOrderedBroadcast(intent: Intent?, receiverPermission: String?) = throw UnsupportedOperationException()
        override fun sendOrderedBroadcast(intent: Intent, receiverPermission: String?, resultReceiver: BroadcastReceiver?, scheduler: Handler?, initialCode: Int, initialData: String?, initialExtras: Bundle?) = throw UnsupportedOperationException()
        override fun sendBroadcastAsUser(intent: Intent?, user: UserHandle?) = throw UnsupportedOperationException()
        override fun sendBroadcastAsUser(intent: Intent?, user: UserHandle?, receiverPermission: String?) = throw UnsupportedOperationException()
        override fun sendOrderedBroadcastAsUser(intent: Intent?, user: UserHandle?, receiverPermission: String?, resultReceiver: BroadcastReceiver?, scheduler: Handler?, initialCode: Int, initialData: String?, initialExtras: Bundle?) = throw UnsupportedOperationException()
        override fun sendStickyBroadcast(intent: Intent?) = throw UnsupportedOperationException()
        override fun sendStickyOrderedBroadcast(intent: Intent?, resultReceiver: BroadcastReceiver?, scheduler: Handler?, initialCode: Int, initialData: String?, initialExtras: Bundle?) = throw UnsupportedOperationException()
        override fun removeStickyBroadcast(intent: Intent?) = throw UnsupportedOperationException()
        override fun sendStickyBroadcastAsUser(intent: Intent?, user: UserHandle?) = throw UnsupportedOperationException()
        override fun sendStickyOrderedBroadcastAsUser(intent: Intent?, user: UserHandle?, resultReceiver: BroadcastReceiver?, scheduler: Handler?, initialCode: Int, initialData: String?, initialExtras: Bundle?) = throw UnsupportedOperationException()
        override fun removeStickyBroadcastAsUser(intent: Intent?, user: UserHandle?) = throw UnsupportedOperationException()
        override fun registerReceiver(receiver: BroadcastReceiver?, filter: IntentFilter?) = throw UnsupportedOperationException()
        override fun registerReceiver(receiver: BroadcastReceiver?, filter: IntentFilter?, flags: Int) = throw UnsupportedOperationException()
        override fun registerReceiver(receiver: BroadcastReceiver?, filter: IntentFilter?, broadcastPermission: String?, scheduler: Handler?) = throw UnsupportedOperationException()
        override fun registerReceiver(receiver: BroadcastReceiver?, filter: IntentFilter?, broadcastPermission: String?, scheduler: Handler?, flags: Int) = throw UnsupportedOperationException()
        override fun unregisterReceiver(receiver: BroadcastReceiver?) = throw UnsupportedOperationException()
        override fun startService(service: Intent?) = throw UnsupportedOperationException()
        override fun stopService(service: Intent?) = throw UnsupportedOperationException()
        override fun bindService(service: Intent, conn: ServiceConnection, flags: Int) = throw UnsupportedOperationException()
        override fun bindService(service: Intent, conn: ServiceConnection, flags: Context.BindServiceFlags) = throw UnsupportedOperationException()
        override fun bindService(service: Intent, flags: Int, executor: Executor, conn: ServiceConnection) = throw UnsupportedOperationException()
        override fun bindService(service: Intent, flags: Context.BindServiceFlags, executor: Executor, conn: ServiceConnection) = throw UnsupportedOperationException()
        override fun unbindService(conn: ServiceConnection) = throw UnsupportedOperationException()
        override fun startInstrumentation(className: ComponentName, profileFile: String?, arguments: Bundle?) = throw UnsupportedOperationException()
        override fun getSystemService(name: String) = throw UnsupportedOperationException()
        override fun getSystemServiceName(serviceClass: Class<*>) = throw UnsupportedOperationException()
        override fun checkPermission(permission: String, pid: Int, uid: Int) = throw UnsupportedOperationException()
        override fun checkCallingPermission(permission: String) = throw UnsupportedOperationException()
        override fun checkCallingOrSelfPermission(permission: String) = throw UnsupportedOperationException()
        override fun checkSelfPermission(permission: String) = throw UnsupportedOperationException()
        override fun enforcePermission(permission: String, pid: Int, uid: Int, message: String?) = throw UnsupportedOperationException()
        override fun enforceCallingPermission(permission: String, message: String?) = throw UnsupportedOperationException()
        override fun enforceCallingOrSelfPermission(permission: String, message: String?) = throw UnsupportedOperationException()
        override fun grantUriPermission(toPackage: String?, uri: android.net.Uri?, modeFlags: Int) = throw UnsupportedOperationException()
        override fun revokeUriPermission(uri: android.net.Uri?, modeFlags: Int) = throw UnsupportedOperationException()
        override fun revokeUriPermission(toPackage: String?, uri: android.net.Uri?, modeFlags: Int) = throw UnsupportedOperationException()
        override fun checkUriPermission(uri: android.net.Uri?, pid: Int, uid: Int, modeFlags: Int) = throw UnsupportedOperationException()
        override fun checkUriPermission(uri: android.net.Uri?, readPermission: String?, writePermission: String?, pid: Int, uid: Int, modeFlags: Int) = throw UnsupportedOperationException()
        override fun checkCallingUriPermission(uri: android.net.Uri?, modeFlags: Int) = throw UnsupportedOperationException()
        override fun checkCallingOrSelfUriPermission(uri: android.net.Uri?, modeFlags: Int) = throw UnsupportedOperationException()
        override fun enforceUriPermission(uri: android.net.Uri?, pid: Int, uid: Int, modeFlags: Int, message: String?) = throw UnsupportedOperationException()
        override fun enforceUriPermission(uri: android.net.Uri?, readPermission: String?, writePermission: String?, pid: Int, uid: Int, modeFlags: Int, message: String?) = throw UnsupportedOperationException()
        override fun enforceCallingUriPermission(uri: android.net.Uri?, modeFlags: Int, message: String?) = throw UnsupportedOperationException()
        override fun enforceCallingOrSelfUriPermission(uri: android.net.Uri?, modeFlags: Int, message: String?) = throw UnsupportedOperationException()
        override fun createPackageContext(packageName: String?, flags: Int) = throw UnsupportedOperationException()
        override fun createContextForSplit(splitName: String?) = throw UnsupportedOperationException()
        override fun createConfigurationContext(overrideConfiguration: Configuration) = throw UnsupportedOperationException()
        override fun createDisplayContext(display: Display) = throw UnsupportedOperationException()
        override fun createDeviceProtectedStorageContext() = throw UnsupportedOperationException()
        override fun isDeviceProtectedStorage() = throw UnsupportedOperationException()
    }

    private fun trace(runId: String, task: String = "test task", capturedAt: Long = 1000L) = WorkflowTrace(
        runId = runId,
        task = task,
        capturedAtMillis = capturedAt,
        steps = listOf(
            WorkflowTraceStep(
                index = 1,
                tool = "tap",
                args = mapOf("text" to "Button"),
                succeeded = true,
                verification = null,
            )
        ),
        screenSignals = emptyList(),
    )

    @Test
    fun tracesPersistedAcrossStoreInstances() {
        val context = mockContext()

        // First session: create store, record traces
        val store1 = WorkflowTraceStore(context)
        store1.record(trace("run-1", capturedAt = 1000L))
        store1.record(trace("run-2", capturedAt = 2000L))
        assertEquals(2, store1.size)

        // Second session: create new store, should load persisted traces
        val store2 = WorkflowTraceStore(context)
        assertEquals(2, store2.size)
        assertNotNull(store2.forRun("run-1"))
        assertNotNull(store2.forRun("run-2"))

        // Verify correct ordering (newest first)
        assertEquals(listOf("run-2", "run-1"), store2.all().map { it.runId })

        // Cleanup
        context.filesDir.deleteRecursively()
    }

    @Test
    fun deletedTracesDoNotReappearInNewStore() {
        val context = mockContext()

        // First session: create and delete
        val store1 = WorkflowTraceStore(context)
        store1.record(trace("run-1"))
        store1.record(trace("run-2"))
        store1.delete("run-1")
        assertEquals(1, store1.size)

        // Second session: deleted trace should not be loaded
        val store2 = WorkflowTraceStore(context)
        assertEquals(1, store2.size)
        assertNull(store2.forRun("run-1"))
        assertNotNull(store2.forRun("run-2"))

        // Cleanup
        context.filesDir.deleteRecursively()
    }

    @Test
    fun deleteAllClearsPersistedTraces() {
        val context = mockContext()

        // First session: create and delete all
        val store1 = WorkflowTraceStore(context)
        store1.record(trace("run-1"))
        store1.record(trace("run-2"))
        store1.record(trace("run-3"))
        val count = store1.deleteAll()
        assertEquals(3, count)
        assertEquals(0, store1.size)

        // Second session: should start empty
        val store2 = WorkflowTraceStore(context)
        assertEquals(0, store2.size)
        assertEquals(emptyList(), store2.all())

        // Cleanup
        context.filesDir.deleteRecursively()
    }

    @Test
    fun updatingTraceReplacesPersistedVersion() {
        val context = mockContext()

        // First session: create trace
        val store1 = WorkflowTraceStore(context)
        store1.record(trace("run-1", task = "original"))
        assertEquals("original", store1.forRun("run-1")?.task)

        // Second session: update trace
        val store2 = WorkflowTraceStore(context)
        store2.record(trace("run-1", task = "updated"))
        assertEquals("updated", store2.forRun("run-1")?.task)
        assertEquals(1, store2.size)

        // Third session: should see updated version
        val store3 = WorkflowTraceStore(context)
        assertEquals(1, store3.size)
        assertEquals("updated", store3.forRun("run-1")?.task)

        // Cleanup
        context.filesDir.deleteRecursively()
    }

    @Test
    fun storeWithoutContextDoesNotPersist() {
        // Store without context should work in-memory only
        val store1 = WorkflowTraceStore(context = null)
        store1.record(trace("run-1"))
        store1.record(trace("run-2"))
        assertEquals(2, store1.size)

        // New store instance should start empty (no persistence)
        val store2 = WorkflowTraceStore(context = null)
        assertEquals(0, store2.size)
    }
}
