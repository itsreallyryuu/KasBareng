package com.app.kasbareng.domain

import com.app.kasbareng.data.dao.ObligationWithPaid
import com.app.kasbareng.data.entity.Member
import java.time.Instant
import java.time.ZoneId

enum class Badge(val label: String) {
    LUNAS("LUNAS"),
    NUNGGAK("NUNGGAK"),
    BELUM_JATUH_TEMPO("BELUM JATUH TEMPO"),
    CICILAN("CICILAN"),
    BELUM_ADA_PEMBAYARAN("BELUM ADA PEMBAYARAN"),
    NONAKTIF("NONAKTIF")
}

data class MemberStatus(
    val badge: Badge,
    val overdueRupiah: Long,
    val partialRemainingRupiah: Long,
    val overdueItems: List<ObligationWithPaid>,
    val upcomingRupiah: Long
)

object BadgeCalculator {
    fun calculate(
        member: Member,
        obligations: List<ObligationWithPaid>,
        now: Instant,
        zone: ZoneId
    ): MemberStatus {
        val overdue = obligations.filter {
            it.remaining() > 0 && DueRules.isOverdue(it.dueEpochDay, now, zone)
        }
        val overdueSum = overdue.sumOf { it.remaining() }
        val upcomingSum = obligations
            .filter { it.remaining() > 0 && !DueRules.isOverdue(it.dueEpochDay, now, zone) }
            .sumOf { it.remaining() }
        val partial = obligations.filter { it.paidRupiah > 0 && it.remaining() > 0 }
        val partialSum = partial.sumOf { it.remaining() }

        val badge = when {
            !member.isActive -> Badge.NONAKTIF
            overdueSum > 0 -> Badge.NUNGGAK
            partial.isNotEmpty() -> Badge.CICILAN
            obligations.isEmpty() -> Badge.BELUM_ADA_PEMBAYARAN
            obligations.any { it.remaining() > 0 } -> Badge.BELUM_JATUH_TEMPO
            else -> Badge.LUNAS
        }
        return MemberStatus(badge, overdueSum, partialSum, overdue, upcomingSum)
    }
}