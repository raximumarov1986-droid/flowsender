package com.flowauto.sender

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

class MainActivity : AppCompatActivity() {
    private lateinit var input: EditText
    private lateinit var from: EditText
    private lateinit var info: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val d = resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()
        fun btn(t: String, f: () -> Unit) = MaterialButton(this).apply {
            text = t
            setOnClickListener { f() }
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(8) }
        }
        val steps = TextView(this).apply {
            text = "1) Promptlarni pastga qo'yib \"Saqlash\" bosing.\n" +
                "2) Accessibility sozlamalarida \"Flow Auto Prompt Sender\"ni yoqing.\n" +
                "3) Flow'ga o'ting, yangi project oching, prompt maydoniga bir marta bosing.\n" +
                "4) Ekrandagi panelda ▶ bosing. Birinchi prompt joylanadi, Generate'ni o'zingiz bosasiz."
            setPadding(0, 0, 0, dp(12))
        }
        input = EditText(this).apply {
            hint = "Promptlarni shu yerga qo'ying (har qator = 1 prompt)"
            minLines = 8
            maxLines = 14
            gravity = Gravity.TOP
            setText(Store.prompts(this@MainActivity).joinToString("\n"))
        }
        from = EditText(this).apply {
            hint = "Nechanchi promptdan boshlash (bo'sh = 1)"
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        info = TextView(this).apply { setPadding(0, dp(12), 0, 0) }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(24), dp(16), dp(24))
            addView(steps)
            addView(input)
            addView(from)
            addView(btn("Saqlash va ajratish") { save() })
            addView(btn("Accessibility sozlamalari") {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            })
            addView(info)
        }
        setContentView(ScrollView(this).apply { addView(col) })
    }

    override fun onResume() {
        super.onResume()
        show()
    }

    private fun save() {
        val lines = input.text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        Store.save(this, lines)
        val st = (from.text.toString().toIntOrNull() ?: 1).coerceIn(1, maxOf(lines.size, 1)) - 1
        Store.setIndex(this, st)
        FlowService.instance?.refresh()
        show()
    }

    private fun show() {
        val l = Store.prompts(this)
        if (l.isEmpty()) {
            info.text = "Ro'yxat bo'sh."
            return
        }
        val i = Store.index(this)
        fun line(k: Int) = "${k + 1}. " + l[k].take(60)
        val sb = StringBuilder("Jami: ${l.size} ta prompt\nHozirgi: ${minOf(i + 1, l.size)}\n\n")
        for (k in 0 until minOf(3, l.size)) sb.append(line(k)).append('\n')
        if (l.size > 3) sb.append("...\n").append(line(l.size - 1))
        info.text = sb.toString()
    }
}
