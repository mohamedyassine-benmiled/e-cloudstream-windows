package com.lagradost.desktop.platform

import com.sun.jna.Callback
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.platform.win32.WinUser
import com.sun.jna.win32.StdCallLibrary
import java.awt.Window

/** Windows marks mouse messages promoted from fingers. Never classify a real mouse or pen as touch. */
internal fun isPromotedTouch(extraInfo: Long): Boolean =
    extraInfo and 0xFFFFFF80L == 0xFF515780L

/** Observe, but do not consume or synthesize, native input. Windows still owns tap-to-click. */
object WindowsTouch {
    @Volatile var lastPrimaryWasTouch = false
        private set

    private interface User32 : StdCallLibrary {
        fun GetMessageExtraInfo(): Long
        fun GetWindowLongPtrW(hwnd: HWND, index: Int): Pointer?
        fun SetWindowLongPtrW(hwnd: HWND, index: Int, callback: Callback): Pointer?
        fun CallWindowProcW(previous: Pointer, hwnd: HWND, message: Int, wp: Long, lp: Long): Long
        fun EnumChildWindows(hwnd: HWND, callback: WinUser.WNDENUMPROC, data: Pointer?): Boolean
        fun GetClassNameW(hwnd: HWND, buffer: CharArray, count: Int): Int
    }
    private interface Proc : Callback {
        fun callback(hwnd: HWND, message: Int, wp: Long, lp: Long): Long
    }
    private val api by lazy {
        if (!System.getProperty("os.name", "").startsWith("Windows")) null
        else runCatching { Native.load("user32", User32::class.java) }.getOrNull()
    }
    // Strong references are required while Windows can call back. Remove them on WM_NCDESTROY.
    private val installed = java.util.concurrent.ConcurrentHashMap<Long, Proc>()

    fun install(window: Window): Boolean {
        val user = api ?: return false
        if (!window.isDisplayable) return false
        return runCatching {
            val top = HWND(Pointer(Native.getComponentID(window)))
            observe(user, top)
            user.EnumChildWindows(top, WinUser.WNDENUMPROC { child, _ ->
                val buffer = CharArray(128)
                val n = user.GetClassNameW(child, buffer, buffer.size)
                if (n > 0 && String(buffer, 0, n) == "SunAwtCanvas") observe(user, child)
                true
            }, null)
            installed.containsKey(Pointer.nativeValue(top.pointer))
        }.onFailure { android.util.Log.w("WindowsTouch", "Touch source detection unavailable; use Touchscreen swipes: On if needed") }.getOrDefault(false)
    }

    private fun observe(user: User32, hwnd: HWND) {
        val id = Pointer.nativeValue(hwnd.pointer)
        if (installed.containsKey(id)) return
        var previous = user.GetWindowLongPtrW(hwnd, -4) ?: return
        val callback = object : Proc {
            override fun callback(hwnd: HWND, message: Int, wp: Long, lp: Long): Long {
                val before = previous
                try {
                    if (message == 0x0201) { // WM_LBUTTONDOWN, queried on the native message thread
                        lastPrimaryWasTouch = isPromotedTouch(user.GetMessageExtraInfo())
                    }
                } catch (_: Throwable) {
                    lastPrimaryWasTouch = false
                }
                return try {
                    user.CallWindowProcW(before, hwnd, message, wp, lp)
                } finally {
                    if (message == 0x0082) installed.remove(id) // WM_NCDESTROY
                }
            }
        }
        installed[id] = callback
        val replaced = user.SetWindowLongPtrW(hwnd, -4, callback)
        if (replaced == null || Pointer.nativeValue(replaced) == 0L) installed.remove(id)
        else previous = replaced
    }
}
