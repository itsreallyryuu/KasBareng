package com.app.kasbareng.ui.members

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
import com.app.kasbareng.domain.Badge
import com.app.kasbareng.ui.KasViewModel
import com.app.kasbareng.ui.MemberRow
import com.app.kasbareng.ui.onTextChanged
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch

class MembersFragment : Fragment(R.layout.fragment_members) {

    private val vm: KasViewModel by lazy {
        ViewModelProvider(requireActivity())[KasViewModel::class.java]
    }
    private var query = ""
    private var filterId = R.id.f_aktif
    private var all: List<MemberRow> = emptyList()
    private lateinit var adapter: MemberAdapter
    private lateinit var empty: TextView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        adapter = MemberAdapter { row -> MemberDetail.show(this, vm, row) }
        val rv = view.findViewById<RecyclerView>(R.id.member_list)
        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.addItemDecoration(DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL))
        rv.adapter = adapter
        empty = view.findViewById(R.id.member_empty)

        view.findViewById<View>(R.id.btn_add).setOnClickListener {
            MemberDialogs.showForm(this, vm, null)
        }
        val etSearch = view.findViewById<TextInputEditText>(R.id.et_search)
        etSearch.onTextChanged {
            query = etSearch.text?.toString().orEmpty()
            render()
        }
        view.findViewById<ChipGroup>(R.id.filter_group).setOnCheckedStateChangeListener { _, ids ->
            filterId = ids.firstOrNull() ?: R.id.f_aktif
            render()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.memberRows.collect { all = it; render() }
            }
        }
    }

    private fun render() {
        val q = query.trim()
        val list = all.filter { r ->
            val matchName = q.isEmpty() || r.member.name.contains(q, ignoreCase = true)
            val matchFilter = when (filterId) {
                R.id.f_nunggak -> r.member.isActive && r.status.badge == Badge.NUNGGAK
                R.id.f_lunas -> r.member.isActive && r.status.badge == Badge.LUNAS
                R.id.f_nonaktif -> !r.member.isActive
                else -> r.member.isActive
            }
            matchName && matchFilter
        }
        adapter.submitList(list)
        if (list.isEmpty()) {
            empty.text = if (all.isEmpty()) "Belum ada anggota. Ketuk Tambah untuk memulai."
            else "Tidak ada anggota yang cocok."
            empty.visibility = View.VISIBLE
        } else {
            empty.visibility = View.GONE
        }
    }
}