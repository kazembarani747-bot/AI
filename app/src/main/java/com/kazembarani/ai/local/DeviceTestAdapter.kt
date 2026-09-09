package com.kazembarani.ai.local

import java.io.File

/** Typed device-test boundary; concrete execution is supplied by the approved device/runtime adapter. */
interface DeviceTestAdapter {
    data class Status(
        val connected: Boolean,
        val serial: String? = null,
        val note: String
    )

    fun status(): Status
    fun install(apk: File): String
    fun runTests(project: File, tasks: List<String>): String
    fun logcat(): String
    fun screenshot(output: File): File
}

class UnavailableDeviceTestAdapter : DeviceTestAdapter {
    override fun status() = DeviceTestAdapter.Status(
        connected = false,
        note = "آداپتور تست دستگاه هنوز به Runtime مورداعتماد متصل نشده است."
    )

    private fun unavailable(operation: String): Nothing =
        error("$operation: آداپتور تست دستگاه در دسترس نیست.")

    override fun install(apk: File): String = unavailable("install")
    override fun runTests(project: File, tasks: List<String>): String = unavailable("test")
    override fun logcat(): String = unavailable("logcat")
    override fun screenshot(output: File): File = unavailable("screenshot")
}
