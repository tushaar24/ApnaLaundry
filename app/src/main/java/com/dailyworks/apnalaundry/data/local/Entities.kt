package com.dailyworks.apnalaundry.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.dailyworks.apnalaundry.core.SyncClock

// Every table carries sync metadata (see data/sync/SyncManager):
//   updatedAt — client logical clock (epoch ms); the server resolves conflicts
//               last-write-wins on this value
//   dirty     — true when the row has local changes not yet pushed
//   deleted   — tombstone: hidden from the UI but kept (and pushed) so other
//               devices learn about the deletion
// Constructor defaults stamp rows as fresh local writes; rows applied from a
// server pull are built with explicit values (dirty = false, server updatedAt).

@Entity(tableName = "shop")
data class ShopEntity(
    @PrimaryKey val id: Int = 1,
    val name: String,
    val phone: String,
    val expressPct: Int,
    val nextOrder: Int,
    val nextCust: Int,
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = SyncClock.now(),
    @ColumnInfo(defaultValue = "1") val dirty: Boolean = true,
)

@Entity(tableName = "services")
data class ServiceEntity(
    @PrimaryKey val id: String,
    val name: String,
    val mode: String,            // PIECE | WEIGHT
    val ratePerKg: Int?,
    val minKg: Double?,
    val readyInDays: Int?,
    val lockedToPiece: Boolean,
    val sortOrder: Int,
    val deleted: Boolean,
    val itemsJson: String,
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = SyncClock.now(),
    @ColumnInfo(defaultValue = "1") val dirty: Boolean = true,
)

@Entity(tableName = "customers")
data class CustomerEntity(
    @PrimaryKey val id: String,
    val name: String,
    val phone: String,
    val address: String,
    val pastOrders: Int,
    val lastLabel: String,
    val agoRank: Int,
    @ColumnInfo(defaultValue = "0") val deleted: Boolean = false,
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = SyncClock.now(),
    @ColumnInfo(defaultValue = "1") val dirty: Boolean = true,
)

@Entity(tableName = "orders")
data class OrderEntity(
    @PrimaryKey val id: Int,
    val custId: String,
    val pickup: String,          // SHOP | HOME
    val delivery: String,
    val pickupDate: String,
    val pickupTime: String,
    val deliveryDate: String,
    val deliveryTime: String,
    val ddAuto: Boolean,
    val status: String,          // OrderStatus
    val cancelReason: String,
    val fee: Int,
    val express: Boolean,
    val exAmt: Int,
    val discount: Int,
    val pre: Int,
    val paid: Int,
    val doneAt: String,
    val doneDate: String,
    val createdOn: String,
    val billSent: Boolean,
    val pieces: Int,
    val linesJson: String,
    @ColumnInfo(defaultValue = "0") val deleted: Boolean = false,
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = SyncClock.now(),
    @ColumnInfo(defaultValue = "1") val dirty: Boolean = true,
)

@Entity(tableName = "ledger")
data class LedgerEntity(
    @PrimaryKey val id: String,
    val custId: String,
    val date: String,
    val time: String,
    val ts: Long,
    val kind: String,            // LedgerKind
    val amt: Int,
    val method: String,          // PayMethod
    val tag: String,             // PayTag
    val cover: Int,
    val toOld: Int,
    val toAdv: Int,
    val ref: Int?,
    val note: String,
    @ColumnInfo(defaultValue = "0") val deleted: Boolean = false,
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = SyncClock.now(),
    @ColumnInfo(defaultValue = "1") val dirty: Boolean = true,
)

@Entity(tableName = "day_close")
data class DayCloseEntity(
    @PrimaryKey val date: String,
    val closedAt: Long,
    val cashCounted: Int?,
    @ColumnInfo(defaultValue = "0") val deleted: Boolean = false,
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = SyncClock.now(),
    @ColumnInfo(defaultValue = "1") val dirty: Boolean = true,
)
