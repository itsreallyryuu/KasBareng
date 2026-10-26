package com.app.kasbareng.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.app.kasbareng.data.dao.AllocationDetail
import com.app.kasbareng.data.db.AppDatabase
import com.app.kasbareng.data.entity.Member
import com.app.kasbareng.data.repository.CashRepository
import com.app.kasbareng.data.repository.ManageRepository
import com.app.kasbareng.data.repository.Outcome
import com.app.kasbareng.data.repository.PaymentRepository
import com.app.kasbareng.domain.BadgeCalculator
import com.app.kasbareng.domain.MemberStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID

data class MemberRow(
    val member: Member,
    val status: MemberStatus,
    val creditRupiah: Long = 0L
)

data class HomeState(
    val balance: Long = 0,
    val income: Long = 0,
    val expense: Long = 0,
    val totalOverdue: Long = 0,
    val activeMembers: Int = 0,
    val overdueMembers: Int = 0
)

class KasViewModel(app: Application) : AndroidViewModel(app) {

    private val db = AppDatabase.get(app)
    private val repo = CashRepository(db)
    private val manage = ManageRepository(db)
    private val payments = PaymentRepository(db)

    private val ticker = flow {
        while (true) {
            emit(Instant.now())
            delay(60_000)
        }
    }

    val memberRows: StateFlow<List<MemberRow>> =
        combine(repo.members, repo.obligations, ticker, repo.transactions) { members, obs, now, txs ->
            val zone = repo.zone()
            val byMember = obs.groupBy { it.memberId }
            val duesByMember = txs
                .filter { it.status == "VALID" && it.type == "DUES_PAYMENT" && it.memberId != null }
                .groupBy { it.memberId!! }
                .mapValues { e -> e.value.sumOf { it.amountRupiah } }
            members.map { m ->
                val mine = byMember[m.id].orEmpty()
                // Saldo di muka = total iuran yang diterima - yang sudah menutup kewajiban
                val credit = maxOf(0L, (duesByMember[m.id] ?: 0L) - mine.sumOf { it.paidRupiah })
                MemberRow(m, BadgeCalculator.calculate(m, mine, now, zone), credit)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val home: StateFlow<HomeState> =
        combine(memberRows, repo.incomeTotal, repo.expenseTotal) { rows, income, expense ->
            val active = rows.filter { it.member.isActive }
            HomeState(
                balance = income - expense,
                income = income,
                expense = expense,
                totalOverdue = active.sumOf { it.status.overdueRupiah },
                activeMembers = active.size,
                overdueMembers = active.count { it.status.overdueRupiah > 0 }
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())

    val transactions = repo.transactions
    val schedules = repo.schedules
    val obligations = repo.obligations

    suspend fun zone() = repo.zone()

    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready

    init {
        viewModelScope.launch {
            repo.refreshObligations()
            _ready.value = true
        }
    }

    /** Panggil juga dari onResume agar kewajiban minggu berjalan selalu terbuat. */
    fun refresh() {
        viewModelScope.launch { repo.refreshObligations() }
    }

    fun newUuid(): String = UUID.randomUUID().toString()

    private fun run(block: suspend () -> Outcome, onResult: (Outcome) -> Unit) {
        viewModelScope.launch { onResult(block()) }
    }

    suspend fun allocationDetails(txId: Long): List<AllocationDetail> = manage.allocationDetails(txId)

    // ----- Jadwal -----
    fun saveSchedule(mask: Int, amount: Long, zoneId: String, onResult: (Outcome) -> Unit) =
        run({ repo.saveSchedule(mask, amount, zoneId) }, onResult)

    fun cancelPendingSchedule(onResult: (Outcome) -> Unit) =
        run({ manage.cancelPendingSchedule() }, onResult)

    // ----- Anggota -----
    fun addMember(name: String, note: String?, onResult: (Outcome) -> Unit) =
        run({ repo.addMember(name, note) }, onResult)

    fun updateMember(id: Long, name: String, note: String?, onResult: (Outcome) -> Unit) =
        run({ repo.updateMember(id, name, note) }, onResult)

    fun deactivateMember(id: Long, onResult: (Outcome) -> Unit) =
        run({ repo.deactivateMember(id) }, onResult)

    fun reactivateMember(id: Long, onResult: (Outcome) -> Unit) =
        run({ manage.reactivateMember(id) }, onResult)

    fun deleteMember(id: Long, onResult: (Outcome) -> Unit) =
        run({ manage.deleteMember(id) }, onResult)

    fun setOpeningArrears(memberId: Long, amount: Long, onResult: (Outcome) -> Unit) =
        run({ manage.setOpeningArrears(memberId, amount) }, onResult)

    // ----- Transaksi -----
    /** excessAsIncome = true berarti pengguna setuju kelebihan disimpan sebagai saldo di muka. */
    fun recordDuesPayment(
        memberId: Long, amount: Long, uuid: String, occurredAt: Long,
        method: String?, note: String?, excessAsIncome: Boolean, arrears: Boolean,
        onResult: (Outcome) -> Unit
    ) = run(
        { payments.recordDuesPayment(memberId, amount, uuid, occurredAt, method, note, excessAsIncome, arrears) },
        onResult
    )

    fun recordOther(
        type: String, amount: Long, description: String, uuid: String, occurredAt: Long,
        onResult: (Outcome) -> Unit
    ) = run({ repo.recordOther(type, amount, description, uuid, occurredAt) }, onResult)

    fun updateTransaction(
        id: Long, occurredAt: Long, description: String?, method: String?, amount: Long?,
        onResult: (Outcome) -> Unit
    ) = run({ manage.updateTransaction(id, occurredAt, description, method, amount) }, onResult)

    fun voidTransaction(id: Long, onResult: (Outcome) -> Unit) =
        run({ repo.voidTransaction(id) }, onResult)

    fun deleteTransaction(id: Long, onResult: (Outcome) -> Unit) =
        run({ manage.deleteVoidedTransaction(id) }, onResult)
}