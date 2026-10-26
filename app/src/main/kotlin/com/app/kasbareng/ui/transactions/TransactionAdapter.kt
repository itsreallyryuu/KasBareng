package com.app.kasbareng.ui.transactions

import android.graphics.Paint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.app.kasbareng.R
import com.app.kasbareng.data.entity.CashTransaction
import com.app.kasbareng.ui.UiFormat
import com.app.kasbareng.util.Rupiah

private object TxDiff : DiffUtil.ItemCallback<CashTransaction>() {
    override fun areItemsTheSame(a: CashTransaction, b: CashTransaction) = a.id == b.id
    override fun areContentsTheSame(a: CashTransaction, b: CashTransaction) = a == b
}

class TransactionAdapter(private val onClick: (CashTransaction) -> Unit) :
    ListAdapter<CashTransaction, TransactionAdapter.VH>(TxDiff) {

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val title: TextView = v.findViewById(R.id.tx_title)
        val sub: TextView = v.findViewById(R.id.tx_sub)
        val amount: TextView = v.findViewById(R.id.tx_amount)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_transaction, parent, false))

    override fun onBindViewHolder(h: VH, position: Int) {
        val t = getItem(position)
        val ctx = h.itemView.context
        val voided = t.status != "VALID"
        val out = t.type == "EXPENSE"

        h.title.text = t.description
        h.sub.text = UiFormat.typeLabel(t.type) + " · " + UiFormat.dateOnly(t.occurredAt) +
            if (voided) " · DIBATALKAN" else ""
        h.amount.text = (if (out) "-" else "+") + Rupiah.format(t.amountRupiah)
        h.amount.setTextColor(
            ContextCompat.getColor(
                ctx,
                when {
                    voided -> R.color.status_nonaktif
                    out -> R.color.status_nunggak
                    else -> R.color.status_lunas
                }
            )
        )
        val strike = Paint.STRIKE_THRU_TEXT_FLAG
        h.amount.paintFlags = if (voided) h.amount.paintFlags or strike else h.amount.paintFlags and strike.inv()
        h.title.paintFlags = if (voided) h.title.paintFlags or strike else h.title.paintFlags and strike.inv()
        h.itemView.setOnClickListener { onClick(t) }
    }
}