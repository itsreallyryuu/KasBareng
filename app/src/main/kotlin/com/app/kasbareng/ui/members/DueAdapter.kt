package com.app.kasbareng.ui.members

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.app.kasbareng.R

data class DueRow(val id: Long, val title: String, val sub: String, val tag: String, val colorRes: Int)

private object DueDiff : DiffUtil.ItemCallback<DueRow>() {
    override fun areItemsTheSame(a: DueRow, b: DueRow) = a.id == b.id
    override fun areContentsTheSame(a: DueRow, b: DueRow) = a == b
}

class DueAdapter : ListAdapter<DueRow, DueAdapter.VH>(DueDiff) {

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val title: TextView = v.findViewById(R.id.tx_title)
        val sub: TextView = v.findViewById(R.id.tx_sub)
        val tag: TextView = v.findViewById(R.id.tx_amount)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_transaction, parent, false))

    override fun onBindViewHolder(h: VH, position: Int) {
        val r = getItem(position)
        h.title.text = r.title
        h.sub.text = r.sub
        h.tag.text = r.tag
        h.tag.textSize = 12f
        h.tag.setTextColor(ContextCompat.getColor(h.itemView.context, r.colorRes))
        h.itemView.isClickable = false
    }
}