package com.app.kasbareng.ui.members

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.app.kasbareng.R
import com.app.kasbareng.domain.Badge
import com.app.kasbareng.ui.MemberRow
import com.app.kasbareng.ui.UiFormat
import com.app.kasbareng.util.Rupiah

private object MemberDiff : DiffUtil.ItemCallback<MemberRow>() {
    override fun areItemsTheSame(a: MemberRow, b: MemberRow) = a.member.id == b.member.id
    override fun areContentsTheSame(a: MemberRow, b: MemberRow) = a == b
}

class MemberAdapter(private val onClick: (MemberRow) -> Unit) :
    ListAdapter<MemberRow, MemberAdapter.VH>(MemberDiff) {

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val name: TextView = v.findViewById(R.id.item_name)
        val sub: TextView = v.findViewById(R.id.item_sub)
        val badge: TextView = v.findViewById(R.id.item_badge)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_member, parent, false))

    override fun onBindViewHolder(h: VH, position: Int) {
        val row = getItem(position)
        val ctx = h.itemView.context
        val s = row.status

        h.name.text = row.member.name
        val sub = when (s.badge) {
            Badge.NUNGGAK -> "${s.overdueItems.size} pembayaran belum dibayar"
            Badge.CICILAN -> "Sisa ${Rupiah.format(s.partialRemainingRupiah)}"
            else -> row.member.note.orEmpty()
        }
        h.sub.text = sub
        h.sub.visibility = if (sub.isEmpty()) View.GONE else View.VISIBLE

        h.badge.text = when (s.badge) {
            Badge.NUNGGAK -> "${s.badge.label}\n${Rupiah.format(s.overdueRupiah)}"
            Badge.CICILAN -> "${s.badge.label}\nSisa ${Rupiah.format(s.partialRemainingRupiah)}"
            else -> s.badge.label
        }
        val color = UiFormat.badgeColor(ctx, s.badge)
        h.badge.setTextColor(color)
        h.badge.background = GradientDrawable().apply {
            cornerRadius = UiFormat.dp(ctx, 6).toFloat()
            setStroke(UiFormat.dp(ctx, 1), color)
        }
        h.itemView.setOnClickListener { onClick(row) }
    }
}