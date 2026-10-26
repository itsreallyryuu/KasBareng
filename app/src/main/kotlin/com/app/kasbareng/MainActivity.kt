package com.app.kasbareng

import android.os.Bundle
import android.os.SystemClock
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.app.kasbareng.ui.KasViewModel
import com.app.kasbareng.ui.Tutorial
import com.app.kasbareng.ui.donate.DonateDialog
import com.app.kasbareng.ui.home.HomeFragment
import com.app.kasbareng.ui.members.MembersFragment
import com.app.kasbareng.ui.reports.ReportsFragment
import com.app.kasbareng.ui.settings.SettingsFragment
import com.app.kasbareng.ui.transactions.TransactionsFragment
import com.app.kasbareng.util.Prefs
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class MainActivity : AppCompatActivity() {

    private lateinit var vm: KasViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_App)   // keluar dari tema Starting
        AppCompatDelegate.setDefaultNightMode(Prefs.themeMode(this))
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        vm = ViewModelProvider(this)[KasViewModel::class.java]

        val nav = findViewById<BottomNavigationView>(R.id.bottom_nav)
        nav.setOnItemSelectedListener { item ->
            val f: Fragment = when (item.itemId) {
                R.id.nav_members -> MembersFragment()
                R.id.nav_transactions -> TransactionsFragment()
                R.id.nav_reports -> ReportsFragment()
                R.id.nav_settings -> SettingsFragment()
                else -> HomeFragment()
            }
            supportFragmentManager.beginTransaction().replace(R.id.container, f).commit()
            true
        }

        val overlay = findViewById<View>(R.id.splash_overlay)
        if (savedInstanceState == null) {
            nav.selectedItemId = R.id.nav_home
            runSplash(overlay)
        } else {
            overlay.visibility = View.GONE
        }
    }

    private fun applyBars(splash: Boolean) {
        val c = WindowCompat.getInsetsController(window, window.decorView)
        if (splash) {
            val navy = ContextCompat.getColor(this, R.color.brand_navy)
            window.statusBarColor = navy
            window.navigationBarColor = navy
            c.isAppearanceLightStatusBars = false
            c.isAppearanceLightNavigationBars = false
        } else {
            val light = resources.getBoolean(R.bool.light_bars)
            window.statusBarColor = ContextCompat.getColor(this, R.color.bg)
            window.navigationBarColor = ContextCompat.getColor(this, R.color.surface)
            c.isAppearanceLightStatusBars = light
            c.isAppearanceLightNavigationBars = light
        }
    }

    private fun runSplash(overlay: View) {
        applyBars(true)
        val started = SystemClock.uptimeMillis()
        lifecycleScope.launch {
            withTimeoutOrNull(5000L) { vm.ready.first { it } }
            val left = 2400L - (SystemClock.uptimeMillis() - started)
            if (left > 0) delay(left)
            applyBars(false)
            overlay.animate().alpha(0f).setDuration(350).withEndAction {
                overlay.visibility = View.GONE
                afterSplash()
            }.start()
        }
    }

    private fun afterSplash() {
        val donate = {
            if (DonateDialog.shouldShow(this)) DonateDialog.show(this)
        }
        if (!Prefs.tutorialSeen(this)) {
            Tutorial.show(this) {
                Prefs.setTutorialSeen(this)
                donate()
            }
        } else {
            donate()
        }
    }

    override fun onResume() {
        super.onResume()
        vm.refresh()
    }

    fun selectTab(id: Int) {
        findViewById<BottomNavigationView>(R.id.bottom_nav).selectedItemId = id
    }
}