package com.app.kasbareng.util

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

object Prefs {
    private fun sp(ctx: Context) =
        ctx.getSharedPreferences("kasbareng_prefs", Context.MODE_PRIVATE)

    fun themeMode(ctx: Context): Int =
        sp(ctx).getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)

    fun setThemeMode(ctx: Context, mode: Int) {
        sp(ctx).edit().putInt("theme_mode", mode).apply()
    }

    fun tutorialSeen(ctx: Context): Boolean = sp(ctx).getBoolean("tutorial_seen", false)

    fun setTutorialSeen(ctx: Context) {
        sp(ctx).edit().putBoolean("tutorial_seen", true).apply()
    }

    fun donateHideUntil(ctx: Context): Long = sp(ctx).getLong("donate_hide_until", 0L)

    fun setDonateHideUntil(ctx: Context, millis: Long) {
        sp(ctx).edit().putLong("donate_hide_until", millis).apply()
    }
}