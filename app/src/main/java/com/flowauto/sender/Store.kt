package com.flowauto.sender

import android.content.Context

object Store {
    private fun p(c: Context) = c.getSharedPreferences("s", Context.MODE_PRIVATE)
    fun prompts(c: Context): List<String> =
        p(c).getString("list", "")!!.split("\n").filter { it.isNotBlank() }
    fun save(c: Context, l: List<String>) =
        p(c).edit().putString("list", l.joinToString("\n")).apply()
    fun index(c: Context): Int = p(c).getInt("idx", 0)
    fun setIndex(c: Context, i: Int) = p(c).edit().putInt("idx", i).apply()
}1
