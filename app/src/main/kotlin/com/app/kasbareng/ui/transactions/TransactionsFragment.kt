package com.app.kasbareng.ui.transactions

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.app.kasbareng.R
import com.app.kasbareng.data.entity.CashTransaction
import com.app.kasbareng.ui.KasViewModel
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

class TransactionsFragment : Fragment(R.layout.fragment_transactions) {

    private val vm: KasViewModel by lazy {
        ViewModelProvider(requireActivity())[KasViewModel::class.java]
    }
    private var all: List<CashTransaction> = emptyList()
    private var filterId = R.id.tf_all
    private lateinit var adapter: TransactionAdapter
    private lateinit var empty: TextView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        adapter = TransactionAdapter { TxDetail.show(this, vm, it) }
        val rv = view.findViewById<RecyclerView>(R.id.tx_list)
        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.addItemDecoration(DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL))
        rv.adapter = adapter
        empty = view.findViewById(R.id.tx_empty)

        view.findViewById<View>(R.id.btn_record).setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Catat transaksi")
                .setItems(
                    arrayOf("Bayar kas", "Pelunasan tunggakan", "Pengeluaran", "Pemasukan lain", "Saldo awal")
                ) { _, i ->
                    when (i) {
                        0 -> TxDialogs.showPayment(this, vm, false)
                        1 -> TxDialogs.showPayment(this, vm, true)
                        2 -> TxDialogs.showOther(this, vm, "EXPENSE")
                        3 -> TxDialogs.showOther(this, vm, "INCOME")
                        else -> TxDialogs.showOther(this, vm, "OPENING_BALANCE")
                    }
                }
                .show()
        }
        view.findViewById<ChipGroup>(R.id.tx_filter).setOnCheckedStateChangeListener { _, ids ->
            filterId = ids.firstOrNull() ?: R.id.tf_all
            render()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.transactions.collect { all = it; render() }
            }
        }
    }

    private fun render() {
        val list = all.filter { t ->
            when (filterId) {
                R.id.tf_in -> t.status == "VALID" && t.type != "EXPENSE"
                R.id.tf_out -> t.status == "VALID" && t.type == "EXPENSE"
                R.id.tf_void -> t.status != "VALID"
                else -> true
            }
        }
        adapter.submitList(list)
        if (list.isEmpty()) {
            empty.text = if (all.isEmpty()) "Belum ada transaksi. Ketuk Catat untuk memulai."
            else "Tidak ada transaksi pada filter ini."
            empty.visibility = View.VISIBLE
        } else {
            empty.visibility = View.GONE
        }
    }
}