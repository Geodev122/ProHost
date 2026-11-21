package com.example.data.repository

import com.example.data.model.*
import com.example.ui.util.IsoDate
import com.example.util.nowMillis

/** CSV formatting for ProHostRepository's exports (pure: lists in, text out). */
object RepositoryCsv {
    private fun fmt(epochMillis: Long): String = IsoDate.localDateTimeText(epochMillis)

    /**
     * Exports the System Audit Logs panel's content (optionally date-range filtered) —
     * this is the same real, server-populated list the panel now shows live (see
     * FirestoreService.attachLiveListeners' audit-log listener), not just whatever this
     * device happened to add locally.
     */
    fun auditLogs(all: List<AuditSecurityLog>, startDateMillis: Long?, endDateMillis: Long?): String {
        val logs = all.filter { log ->
            val matchesStart = startDateMillis == null || log.timestamp >= startDateMillis
            val matchesEnd = endDateMillis == null || log.timestamp <= endDateMillis
            matchesStart && matchesEnd
        }

        val sb = StringBuilder()
        sb.appendLine("=== PROHOST SYSTEM AUDIT LOGS EXPORT (CSV) ===")
        sb.appendLine("Export Date,${fmt(nowMillis())}")
        sb.appendLine("Range,${startDateMillis?.let { fmt(it) } ?: "Beginning"} to ${endDateMillis?.let { fmt(it) } ?: "Now"}")
        sb.appendLine("Total Entries,${logs.size}")
        sb.appendLine()
        sb.appendLine("Timestamp,Action Type,Severity,Actor Email,Details")
        logs.forEach { log ->
            val detailsEscaped = log.details.replace("\"", "\"\"")
            sb.appendLine("\"${fmt(log.timestamp)}\",\"${log.actionType}\",\"${log.severity}\",\"${log.actorEmail}\",\"$detailsEscaped\"")
        }
        return sb.toString()
    }

    fun ownerRegistrations(users: List<AppUser>, spaces: List<SpaceListing>): String {
        val owners = users.filter { it.role == UserRole.PRO_HOST }
        val sb = StringBuilder()
        sb.appendLine("=== PROHOST OWNER REGISTRATIONS & WORKSPACES AUDIT ===")
        sb.appendLine("Export Date,${fmt(nowMillis())}")
        sb.appendLine("Total Registered Hosts,${owners.size}")
        sb.appendLine()
        sb.appendLine("User ID,Full Name,Email,Phone,Country,Governorate,City,Properties Count,Active Subscribed Count,Is Verified")
        owners.forEach { o ->
            val ownedSpaces = spaces.filter { it.ownerId == o.id }
            val activeSpaces = ownedSpaces.count { it.isActiveSubscription }
            sb.appendLine("\"${o.id}\",\"${o.fullName.replace("\"", "\"\"")}\",\"${o.email}\",\"${o.phone}\",\"${o.country}\",\"" +
                "${o.governorate}\",\"${o.city}\",${ownedSpaces.size},$activeSpaces,${o.isVerified}")
        }
        return sb.toString()
    }

    fun listings(spaces: List<SpaceListing>): String {
        val sb = StringBuilder()
        sb.appendLine("=== PROHOST WORKSPACE LISTINGS EXPORT (CSV) ===")
        sb.appendLine("Export Date,${fmt(nowMillis())}")
        sb.appendLine("Total Listings,${spaces.size}")
        sb.appendLine()
        sb.appendLine("Space ID,Title,Space Type,Governorate,District,Street Address,Monthly Rate USD,Is Shared,Is Verified" +
            ",Active 30d Sub,Owner Name,Owner Phone,Owner Email")
        spaces.forEach { sp ->
            sb.appendLine("\"${sp.id}\",\"${sp.title.replace("\"", "\"\"")}\",\"${sp.spaceType.name}\",\"${sp.governorate.displayName}" +
                "\",\"${sp.district}\",\"${sp.streetAddress.replace("\"", "\"\"")}\",${sp.baseMonthlyRateUsd},${sp.isShared}" +
                ",${sp.isVerified},${sp.isActiveSubscription},\"${sp.ownerName.replace("\"", "\"\"")}\",\"${sp.ownerPhone}" +
                "\",\"${sp.ownerEmail}\"")
        }
        return sb.toString()
    }

    fun bookings(bookings: List<RentalBookingRequest>): String {
        val sb = StringBuilder()
        sb.appendLine("=== PROHOST BOOKINGS LEDGER (CSV) ===")
        sb.appendLine("Export Date,${fmt(nowMillis())}")
        sb.appendLine("Total Bookings,${bookings.size}")
        sb.appendLine()
        sb.appendLine("Booking ID,Space ID,Space Title,Practitioner,Specialty,Duration Months,Total USD,Status,Start Date,End Date")
        bookings.forEach { b ->
            sb.appendLine("\"${b.id}\",\"${b.spaceId}\",\"${b.spaceTitle}\",\"${b.practitionerName}\",\"${b.practitionerSpecialty}" +
                "\",${b.durationMonths},${b.totalAmountUsd},\"${b.status}\",\"${b.startDate}\",\"${b.endDate}\"")
        }
        return sb.toString()
    }
}
