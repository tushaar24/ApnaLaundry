package com.dailyworks.apnalaundry.data.sync

import com.dailyworks.apnalaundry.data.local.CustomerEntity
import com.dailyworks.apnalaundry.data.local.DayCloseEntity
import com.dailyworks.apnalaundry.data.local.LedgerEntity
import com.dailyworks.apnalaundry.data.local.OrderEntity
import com.dailyworks.apnalaundry.data.local.ServiceEntity
import com.dailyworks.apnalaundry.data.local.ShopEntity
import com.dailyworks.apnalaundry.data.decodeTerms
import com.dailyworks.apnalaundry.data.encodeTerms
import com.dailyworks.apnalaundry.domain.BillDetails
import com.dailyworks.apnalaundry.domain.Gst
import kotlinx.serialization.Serializable

// Wire DTOs for /api/laundry/sync — field-for-field mirrors of the Room
// entities (the server treats itemsJson/linesJson as opaque strings).
// Entity -> DTO for push; DTO -> entity (dirty = false, server updatedAt
// preserved) for pull.

@Serializable
data class ShopDto(
    val name: String, val phone: String, val expressPct: Int,
    val nextOrder: Int, val nextCust: Int, val updatedAt: Long,
    // Bill details + onboarding — absent from servers before 2026-10-09.
    val billPhone: String = "", val address: String = "", val gstin: String = "",
    val upiId: String = "", val logoId: String = "", val terms: List<String> = emptyList(),
    val termsCustom: String = "", val billTemplate: String = "classic", val onboardingStep: String = "",
    // Last-used GST setting — absent from servers before the GST release.
    val gstOn: Boolean = false, val gstPct: Double = Gst.DEFAULT_PCT, val gstMode: String = Gst.EXCL,
)

@Serializable
data class ServiceDto(
    val id: String, val name: String, val mode: String, val ratePerKg: Int? = null,
    val minKg: Double? = null, val readyInDays: Int? = null, val lockedToPiece: Boolean = false,
    val sortOrder: Int = 0, val deleted: Boolean = false, val itemsJson: String = "[]",
    val updatedAt: Long,
)

@Serializable
data class CustomerDto(
    val id: String, val name: String, val phone: String, val address: String,
    val pastOrders: Int = 0, val lastLabel: String = "", val agoRank: Int = 0,
    val deleted: Boolean = false, val updatedAt: Long,
)

@Serializable
data class OrderDto(
    val id: Int, val custId: String, val pickup: String, val delivery: String,
    val pickupDate: String = "", val pickupTime: String = "", val deliveryDate: String = "",
    val deliveryTime: String = "", val ddAuto: Boolean = false, val status: String,
    val cancelReason: String = "", val fee: Int = 0, val express: Boolean = false,
    val exAmt: Int = 0, val discount: Int = 0, val pre: Int = 0, val paid: Int = 0,
    val doneAt: String = "", val doneDate: String = "", val createdOn: String = "",
    val billSent: Boolean = false, val pieces: Int = 0, val linesJson: String = "[]",
    val deleted: Boolean = false, val updatedAt: Long,
    // Owner-set bill / serial no. Always sent back (the server writes it only when present).
    val serialNo: String? = null,
    // % extras (0 = fixed ₹); older servers / clients omit them.
    val exPct: Int? = null, val discPct: Int? = null,
    // GST on the order; older servers / clients omit it (the server writes it only when present).
    val gstOn: Boolean? = null, val gstPct: Double? = null, val gstMode: String? = null,
)

@Serializable
data class LedgerDto(
    val id: String, val custId: String, val date: String = "", val time: String = "",
    val ts: Long, val kind: String, val amt: Int, val method: String = "NONE",
    val tag: String = "NONE", val cover: Int = 0, val toOld: Int = 0, val toAdv: Int = 0,
    val ref: Int? = null, val note: String = "", val deleted: Boolean = false,
    val updatedAt: Long,
)

@Serializable
data class DayCloseDto(
    val date: String, val closedAt: Long, val cashCounted: Int? = null,
    val deleted: Boolean = false, val updatedAt: Long,
)

@Serializable
data class SyncChanges(
    val shop: ShopDto? = null,
    val services: List<ServiceDto> = emptyList(),
    val customers: List<CustomerDto> = emptyList(),
    val orders: List<OrderDto> = emptyList(),
    val ledger: List<LedgerDto> = emptyList(),
    val dayCloses: List<DayCloseDto> = emptyList(),
) {
    val isEmpty: Boolean
        get() = shop == null && services.isEmpty() && customers.isEmpty()
            && orders.isEmpty() && ledger.isEmpty() && dayCloses.isEmpty()
}

@Serializable data class PushRequest(val changes: SyncChanges)
@Serializable data class PushResponse(val success: Boolean, val applied: Int = 0, val skipped: Int = 0, val message: String? = null)
@Serializable data class PullResponse(val success: Boolean, val checkpoint: Long = 0, val changes: SyncChanges = SyncChanges(), val message: String? = null)

// ---- entity <-> DTO ----

fun ShopEntity.toDto() = ShopDto(
    name, phone, expressPct, nextOrder, nextCust, updatedAt,
    billPhone, address, gstin, upiId, logoId, decodeTerms(termsJson), termsCustom, billTemplate, onboardingStep,
    gstOn = gstOn, gstPct = gstPct, gstMode = gstMode,
)
fun ShopDto.toEntity() = ShopEntity(
    1, name, phone, expressPct, nextOrder, nextCust, updatedAt, dirty = false,
    billPhone = billPhone.ifBlank { phone }, address = address, gstin = gstin, upiId = upiId, logoId = logoId,
    termsJson = encodeTerms(terms), termsCustom = termsCustom,
    billTemplate = BillDetails.templateOrDefault(billTemplate), onboardingStep = BillDetails.stepOrBlank(onboardingStep),
    gstOn = gstOn, gstPct = Gst.pctFrom(gstPct, Gst.DEFAULT_PCT), gstMode = Gst.modeOrDefault(gstMode),
)

fun ServiceEntity.toDto() = ServiceDto(id, name, mode, ratePerKg, minKg, readyInDays, lockedToPiece, sortOrder, deleted, itemsJson, updatedAt)
fun ServiceDto.toEntity() = ServiceEntity(id, name, mode, ratePerKg, minKg, readyInDays, lockedToPiece, sortOrder, deleted, itemsJson, updatedAt, dirty = false)

fun CustomerEntity.toDto() = CustomerDto(id, name, phone, address, pastOrders, lastLabel, agoRank, deleted, updatedAt)
fun CustomerDto.toEntity() = CustomerEntity(id, name, phone, address, pastOrders, lastLabel, agoRank, deleted, updatedAt, dirty = false)

fun OrderEntity.toDto() = OrderDto(
    id, custId, pickup, delivery, pickupDate, pickupTime, deliveryDate, deliveryTime, ddAuto, status,
    cancelReason, fee, express, exAmt, discount, pre, paid, doneAt, doneDate, createdOn, billSent,
    pieces, linesJson, deleted, updatedAt, serialNo, exPct, discPct, gstOn, gstPct, gstMode,
)
fun OrderDto.toEntity() = OrderEntity(
    id, custId, pickup, delivery, pickupDate, pickupTime, deliveryDate, deliveryTime, ddAuto, status,
    cancelReason, fee, express, exAmt, discount, pre, paid, doneAt, doneDate, createdOn, billSent,
    pieces, linesJson, deleted, updatedAt, dirty = false, serialNo = serialNo ?: "",
    exPct = exPct ?: 0, discPct = discPct ?: 0,
    gstOn = gstOn ?: false, gstPct = Gst.pctFrom(gstPct), gstMode = Gst.modeOrDefault(gstMode),
)

fun LedgerEntity.toDto() = LedgerDto(id, custId, date, time, ts, kind, amt, method, tag, cover, toOld, toAdv, ref, note, deleted, updatedAt)
fun LedgerDto.toEntity() = LedgerEntity(id, custId, date, time, ts, kind, amt, method, tag, cover, toOld, toAdv, ref, note, deleted, updatedAt, dirty = false)

fun DayCloseEntity.toDto() = DayCloseDto(date, closedAt, cashCounted, deleted, updatedAt)
fun DayCloseDto.toEntity() = DayCloseEntity(date, closedAt, cashCounted, deleted, updatedAt, dirty = false)

@Serializable data class LogoUpload(val contentType: String, val dataBase64: String)
@Serializable data class LogoUploadResponse(val success: Boolean, val logoId: String? = null, val message: String? = null)
@Serializable data class BillLinkResponse(val success: Boolean, val token: String? = null)
