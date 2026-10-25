package com.kitsune.color

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.abs

class Flag(private val sp: SharedPreferences, private val key: String, default: Boolean) {
    var value by mutableStateOf(sp.getBoolean(key, default))
        private set

    fun set(v: Boolean) {
        value = v
        sp.edit().putBoolean(key, v).apply()
    }
}

/** Todas as preferências do app (as mesmas do menu de engrenagem da versão em HTML). */
class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("prefs", Context.MODE_PRIVATE)

    val haptic = Flag(sp, "haptic", true)
    val reduce = Flag(sp, "reduce", false)
    val scanOnStart = Flag(sp, "scanOnStart", true)
    val autoDemo = Flag(sp, "autoDemo", true)
    val welcome = Flag(sp, "welcome", true)
    val reconnect = Flag(sp, "reconnect", true)
    val clock = Flag(sp, "clock", true)
    val confirmOff = Flag(sp, "confirmOff", false)
    val tabModes = Flag(sp, "tabModes", true)
    val tabMic = Flag(sp, "tabMic", true)
    val tabMusic = Flag(sp, "tabMusic", true)
    val tabSched = Flag(sp, "tabSched", true)
    val experimental = Flag(sp, "experimental", false)
    val remember = Flag(sp, "remember", false)
    val wake = Flag(sp, "wake", true)
    val stopOnLeave = Flag(sp, "stopOnLeave", true)
    val autoNext = Flag(sp, "autoNext", true)
    val adv = Flag(sp, "adv", false)
    val blur = Flag(sp, "blur", true)

    /** 0 = sistema, 1 = claro, 2 = escuro */
    var themeMode by mutableIntStateOf(sp.getInt("theme", 0))
        private set
    fun setTheme(v: Int) { themeMode = v; sp.edit().putInt("theme", v).apply() }

    /** 0 = sem, 1 = leve, 2 = forte */
    var wallBlur by mutableIntStateOf(sp.getInt("wallBlur", 1))
        private set
    fun changeWallBlur(v: Int) { wallBlur = v; sp.edit().putInt("wallBlur", v).apply() }

    /** 0 = cinza padrão, 1 = foto clara, 2 = foto escura, 3 = da galeria */
    var wallChoice by mutableIntStateOf(sp.getInt("wallChoice", 0))
        private set
    fun changeWallChoice(v: Int) { wallChoice = v; sp.edit().putInt("wallChoice", v).apply() }

    var wallVersion by mutableIntStateOf(sp.getInt("wallVer", 0))
        private set
    fun wallChanged() { wallVersion++; sp.edit().putInt("wallVer", wallVersion).apply() }

    var ledCount by mutableIntStateOf(sp.getInt("ledCount", 150))
        private set
    fun changeLedCount(v: Int) { ledCount = v.coerceIn(8, 300); sp.edit().putInt("ledCount", ledCount).apply() }

    var ledOrder by mutableIntStateOf(sp.getInt("ledOrder", 1))
        private set
    fun changeLedOrder(v: Int) { ledOrder = v; sp.edit().putInt("ledOrder", v).apply() }

    var lastScreen by mutableIntStateOf(sp.getInt("lastScreen", 0))
        private set
    fun changeLastScreen(v: Int) { lastScreen = v; sp.edit().putInt("lastScreen", v).apply() }
}

/** Cores recentes (as fixadas ficam sempre no início e nunca são apagadas). */
class Recents(ctx: Context) {
    data class C(val h: Int, val s: Int, val pinned: Boolean)

    private val sp = ctx.getSharedPreferences("recents", Context.MODE_PRIVATE)
    val list = mutableStateListOf<C>()

    init {
        sp.getString("l", "")!!.split(";").filter { it.isNotBlank() }.forEach {
            try {
                val p = it.split(",")
                list.add(C(p[0].toInt(), p[1].toInt(), p[2] == "1"))
            } catch (_: Exception) {
            }
        }
    }

    private fun save() {
        sp.edit().putString("l", list.joinToString(";") { "${it.h},${it.s},${if (it.pinned) 1 else 0}" }).apply()
    }

    private fun same(c: C, h: Int, s: Int) = abs(c.h - h) < 4 && abs(c.s - s) < 30

    fun remember(h: Int, s: Int) {
        if (list.any { it.pinned && same(it, h, s) }) return
        list.removeAll { !it.pinned && same(it, h, s) }
        list.add(0, C(h, s, false))
        val pinned = list.filter { it.pinned }
        val rest = list.filter { !it.pinned }
        list.clear()
        list.addAll(pinned + rest)
        while (list.size > 16) {
            val i = list.indexOfLast { !it.pinned }
            if (i < 0) break
            list.removeAt(i)
        }
        save()
    }

    fun togglePin(i: Int) {
        if (i !in list.indices) return
        val c = list[i]
        list[i] = c.copy(pinned = !c.pinned)
        val pinned = list.filter { it.pinned }
        val rest = list.filter { !it.pinned }
        list.clear()
        list.addAll(pinned + rest)
        save()
    }
}
