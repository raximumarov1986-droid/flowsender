package com.flowauto.sender

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class FlowService : AccessibilityService() {
    companion object { var instance: FlowService? = null }

    private val h = Handler(Looper.getMainLooper())
    private var wm: WindowManager? = null
    private var panel: LinearLayout? = null
    private var info: TextView? = null
    private var play: Button? = null

    private var running = false
    private var waiting = false   // prompt joylangan, Generate kutilmoqda
    private var armed = false     // maydonda prompt ko'rindi
    private var busy = false      // 2 soniyalik kutish ketmoqda
    private var insertedAt = 0L
    private var status = "Tayyor"

    private val advanceR = Runnable { advance() }
    private val pollR = object : Runnable {
        override fun run() {
            check()
            h.postDelayed(this, 500)
        }
    }

    override fun onServiceConnected() {
        instance = this
        showPanel()
        refresh()
        h.post(pollR)
    }

    override fun onAccessibilityEvent(e: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onDestroy() {
        instance = null
        h.removeCallbacksAndMessages(null)
        panel?.let { runCatching { wm?.removeView(it) } }
        super.onDestroy()
    }

    fun refresh() {
        val n = Store.prompts(this).size
        val i = Store.index(this)
        val c = when {
            n == 0 -> "Ro'yxat bo'sh"
            i >= n -> "Tugadi $n/$n"
            else -> "${i + 1}/$n"
        }
        info?.text = "$c\n$status"
        play?.text = if (running) "⏸" else "▶"
    }

    private fun check() {
        if (!running || !waiting || busy) return
        val list = Store.prompts(this)
        val i = Store.index(this)
        if (i >= list.size) return
        val node = findEdit() ?: return
        val t = node.text?.toString() ?: ""
        val has = t.contains(list[i].take(15))
        if (has) {
            armed = true
        } else if (armed) {
            busy = true
            status = "2 soniya kutilmoqda"
            refresh()
            h.postDelayed(advanceR, 2000)
        } else if (System.currentTimeMillis() - insertedAt > 3000) {
            running = false
            waiting = false
            status = "Joylanmadi. Maydonga bosib, ▶ bosing"
            refresh()
        }
    }

    private fun advance() {
        Store.setIndex(this, Store.index(this) + 1)
        waiting = false
        armed = false
        busy = false
        if (running) insertCurrent() else refresh()
    }

    private fun insertCurrent() {
        if (!running) return
        val list = Store.prompts(this)
        val i = Store.index(this)
        if (i >= list.size) {
            running = false
            status = "Hammasi tugadi ✓"
            refresh()
            return
        }
        val node = findEdit()
        if (node == null) {
            status = "Maydon topilmadi: prompt maydoniga bosing"
            refresh()
            h.postDelayed({ if (running && !waiting) insertCurrent() }, 1500)
            return
        }
        val text = list[i]
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("p", text))
        node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val len = node.text?.length ?: 0
        node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, Bundle().apply {
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, 0)
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, len)
        })
        var ok = node.performAction(AccessibilityNodeInfo.ACTION_PASTE)
        if (!ok) {
            ok = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            })
        }
        waiting = ok
        armed = false
        busy = false
        insertedAt = System.currentTimeMillis()
        status = if (ok) "Generate bosing" else "Joylab bo'lmadi"
        refresh()
    }

    private fun findEdit(): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)?.let { if (it.isEditable) return it }
        return firstEditable(root)
    }

    private fun firstEditable(n: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (n.isEditable) return n
        for (k in 0 until n.childCount) {
            val c = n.getChild(k) ?: continue
            firstEditable(c)?.let { return it }
        }
        return null
    }

    private fun toggle() {
        if (running) {
            running = false
            h.removeCallbacks(advanceR)
            busy = false
            status = "Pauza"
        } else {
            val n = Store.prompts(this).size
            if (n == 0 || Store.index(this) >= n) {
                status = "Ro'yxat bo'sh yoki tugagan"
            } else {
                running = true
                if (waiting) status = "Generate bosing" else insertCurrent()
            }
        }
        refresh()
    }

    private fun move(d: Int) {
        h.removeCallbacks(advanceR)
        val n = Store.prompts(this).size
        Store.setIndex(this, (Store.index(this) + d).coerceIn(0, n))
        waiting = false
        armed = false
        busy = false
        if (running) insertCurrent() else { status = "Pauza"; refresh() }
    }

    private fun stop() {
        h.removeCallbacks(advanceR)
        running = false
        waiting = false
        armed = false
        busy = false
        status = "To'xtatildi"
        refresh()
    }

    private fun showPanel() {
        val w = getSystemService(WINDOW_SERVICE) as WindowManager
        wm = w
        fun b(t: String, f: () -> Unit) = Button(this).apply {
            text = t
            textSize = 18f
            minWidth = 0
            minimumWidth = 0
            setPadding(24, 8, 24, 8)
            setOnClickListener { f() }
        }
        val tv = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 14f
            setPadding(24, 12, 24, 12)
        }
        info = tv
        val p = b("▶") { toggle() }
        play = p
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(b("⏮") { move(-1) })
            addView(p)
            addView(b("⏭") { move(1) })
            addView(b("■") { stop() })
        }
        val pn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xE6202124.toInt())
            addView(tv)
            addView(row)
        }
        panel = pn
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = 20; y = 300 }
        var sx = 0; var sy = 0; var dx = 0f; var dy = 0f
        tv.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { sx = lp.x; sy = lp.y; dx = e.rawX; dy = e.rawY }
                MotionEvent.ACTION_MOVE -> {
                    lp.x = sx + (e.rawX - dx).toInt()
                    lp.y = sy + (e.rawY - dy).toInt()
                    w.updateViewLayout(pn, lp)
                }
            }
            true
        }
        w.addView(pn, lp)
    }
}
