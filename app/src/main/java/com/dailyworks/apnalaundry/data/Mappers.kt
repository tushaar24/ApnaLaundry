package com.dailyworks.apnalaundry.data

import com.dailyworks.apnalaundry.data.local.CustomerEntity
import com.dailyworks.apnalaundry.data.local.LedgerEntity
import com.dailyworks.apnalaundry.data.local.OrderEntity
import com.dailyworks.apnalaundry.data.local.ServiceEntity
import com.dailyworks.apnalaundry.data.local.ShopEntity
import com.dailyworks.apnalaundry.domain.*
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

private val json = Json { ignoreUnknownKeys = true }
private val itemSer = ListSerializer(ServiceItem.serializer())
private val lineSer = ListSerializer(OrderLine.serializer())

private fun encodeItems(v: List<ServiceItem>) = json.encodeToString(itemSer, v)
private fun decodeItems(v: String) = if (v.isBlank()) emptyList() else json.decodeFromString(itemSer, v)
private fun encodeLines(v: List<OrderLine>) = json.encodeToString(lineSer, v)
private fun decodeLines(v: String) = if (v.isBlank()) emptyList() else json.decodeFromString(lineSer, v)

fun ServiceEntity.toDomain() = Service(
    id, name, PricingMode.valueOf(mode), ratePerKg, minKg, readyInDays, lockedToPiece, decodeItems(itemsJson), sortOrder,
)

fun Service.toEntity(deleted: Boolean = false) = ServiceEntity(
    id, name, mode.name, ratePerKg, minKg, readyInDays, lockedToPiece, sortOrder, deleted, encodeItems(items),
)

fun CustomerEntity.toDomain() = Customer(id, name, phone, address, pastOrders, lastLabel, agoRank)
fun Customer.toEntity() = CustomerEntity(id, name, phone, address, pastOrders, lastLabel, agoRank)

fun OrderEntity.toDomain() = Order(
    id, custId, Route.valueOf(pickup), Route.valueOf(delivery), pickupDate, pickupTime, deliveryDate,
    deliveryTime, ddAuto, OrderStatus.valueOf(status), cancelReason, fee, express, exAmt, discount,
    pre, paid, doneAt, doneDate, createdOn, billSent, pieces, decodeLines(linesJson),
)

fun Order.toEntity() = OrderEntity(
    id, custId, pickup.name, delivery.name, pickupDate, pickupTime, deliveryDate, deliveryTime, ddAuto,
    status.name, cancelReason, fee, express, exAmt, discount, pre, paid, doneAt, doneDate, createdOn,
    billSent, pieces, encodeLines(lines),
)

fun LedgerEntity.toDomain() = LedgerEntry(
    id, custId, date, time, ts, LedgerKind.valueOf(kind), amt, PayMethod.valueOf(method),
    PayTag.valueOf(tag), cover, toOld, toAdv, ref, note,
)

fun LedgerEntry.toEntity() = LedgerEntity(
    id, custId, date, time, ts, kind.name, amt, method.name, tag.name, cover, toOld, toAdv, ref, note,
)

private val termsSer = ListSerializer(String.serializer())
fun encodeTerms(v: List<String>): String = json.encodeToString(termsSer, v)
fun decodeTerms(v: String): List<String> = runCatching { json.decodeFromString(termsSer, v) }.getOrDefault(emptyList())

fun ShopEntity.toDomain() = Shop(
    name, phone, expressPct,
    billPhone = billPhone, address = address, gstin = gstin, upiId = upiId, logoId = logoId,
    terms = decodeTerms(termsJson), termsCustom = termsCustom, billTemplate = billTemplate, onboardingStep = onboardingStep,
)
