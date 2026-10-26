package com.app.kasbareng.ui.home

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.app.kasbareng.MainActivity
import com.app.kasbareng.R
import com.app.kasbareng.ui.KasViewModel
import com.app.kasbareng.ui.members.MemberDialogs
import com.app.kasbareng.ui.transactions.TxDialogs
import com.app.kasbareng.util.Rupiah
import kotlinx.coroutines.launch

class HomeFragment : Fragment(R.layout.fragment_home) {

    private val vm: KasViewModel by lazy {
        ViewModelProvider(requireActivity())[KasViewModel::class.java]
    }
    private var hasSchedule = false
    private var memberCount = 0
    private lateinit var hint: TextView
    private lateinit var hintCard: View

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val balance = view.findViewById<TextView>(R.id.home_balance)
        val income = view.findViewById<TextView>(R.id.home_income)
        val expense = view.findViewById<TextView>(R.id.home_expense)
        val overdue = view.findViewById<TextView>(R.id.home_overdue)
        val overdueSub = view.findViewById<TextView>(R.id.home_overdue_sub)
        hint = view.findViewById(R.id.home_hint)
        hintCard = view.findViewById(R.id.home_hint_card)

        view.findViewById<View>(R.id.btn_pay).setOnClickListener { TxDialogs.showPayment(this, vm, false) }
        view.findViewById<View>(R.id.btn_arrears).setOnClickListener { TxDialogs.showPayment(this, vm, true) }
        view.findViewById<View>(R.id.btn_expense).setOnClickListener { TxDialogs.showOther(this, vm, "EXPENSE") }
        view.findViewById<View>(R.id.btn_add_member).setOnClickListener {
            MemberDialogs.showForm(this, vm, null)
        }
        overdueSub.setOnClickListener { (activity as? MainActivity)?.selectTab(R.id.nav_members) }
        hintCard.setOnClickListener {
            val main = activity as? MainActivity ?: return@setOnClickListener
            main.selectTab(if (!hasSchedule) R.id.nav_settings else R.id.nav_members)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    vm.home.collect { s ->
                        balance.text = Rupiah.format(s.balance)
                        income.text = Rupiah.format(s.income)
                        expense.text = Rupiah.format(s.expense)
                        overdue.text = Rupiah.format(s.totalOverdue)
                        overdueSub.text = if (s.activeMembers == 0) "Belum ada anggota aktif"
                        else "${s.overdueMembers} dari ${s.activeMembers} anggota menunggak"
                        memberCount = s.activeMembers
                        updateHint()
                    }
                }
                launch {
                    vm.schedules.collect { list ->
                        hasSchedule = list.isNotEmpty()
                        updateHint()
                    }
                }
            }
        }
    }

    private fun updateHint() {
        when {
            !hasSchedule -> {
                hint.text = "Langkah pertama: ketuk di sini untuk membuka Setelan, lalu atur jadwal kas."
                hintCard.visibility = View.VISIBLE
            }
            memberCount == 0 -> {
                hint.text = "Jadwal kas sudah tersimpan. Ketuk di sini untuk menambahkan anggota."
                hintCard.visibility = View.VISIBLE
            }
            else -> hintCard.visibility = View.GONE
        }
    }
}