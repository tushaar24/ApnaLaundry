package com.dailyworks.apnalaundry.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

// Query conventions:
//   observe()/observeOnce() — UI/domain reads, tombstones (deleted = 1) excluded
//   getAll()/get(id)        — raw reads for snapshot/undo and the sync engine
//   dirtyRows()             — rows pending push
//   clearDirty(id, updatedAt) — guarded by updatedAt so a row edited while the
//                               push was in flight stays dirty

@Dao
interface ShopDao {
    @Query("SELECT * FROM shop WHERE id = 1") fun observe(): Flow<ShopEntity?>
    @Query("SELECT * FROM shop WHERE id = 1") suspend fun get(): ShopEntity?
    @Upsert suspend fun upsert(shop: ShopEntity)
    @Query("UPDATE shop SET dirty = 0 WHERE id = 1 AND updatedAt = :updatedAt") suspend fun clearDirty(updatedAt: Long)
    @Query("DELETE FROM shop") suspend fun clear()
}

@Dao
interface ServiceDao {
    @Query("SELECT * FROM services WHERE deleted = 0 ORDER BY sortOrder") fun observe(): Flow<List<ServiceEntity>>
    @Query("SELECT * FROM services") suspend fun getAll(): List<ServiceEntity>
    @Query("SELECT * FROM services WHERE id = :id") suspend fun get(id: String): ServiceEntity?
    @Query("SELECT * FROM services WHERE dirty = 1") suspend fun dirtyRows(): List<ServiceEntity>
    @Upsert suspend fun upsert(service: ServiceEntity)
    @Upsert suspend fun upsertAll(services: List<ServiceEntity>)
    @Query("UPDATE services SET deleted = :deleted, dirty = 1, updatedAt = :now WHERE id = :id")
    suspend fun setDeleted(id: String, deleted: Boolean, now: Long)
    @Query("UPDATE services SET dirty = 0 WHERE id = :id AND updatedAt = :updatedAt") suspend fun clearDirty(id: String, updatedAt: Long)
    @Query("DELETE FROM services") suspend fun clear()
}

@Dao
interface CustomerDao {
    @Query("SELECT * FROM customers WHERE deleted = 0") fun observe(): Flow<List<CustomerEntity>>
    @Query("SELECT * FROM customers WHERE deleted = 0") suspend fun observeOnce(): List<CustomerEntity>
    @Query("SELECT * FROM customers") suspend fun getAll(): List<CustomerEntity>
    @Query("SELECT * FROM customers WHERE id = :id") suspend fun get(id: String): CustomerEntity?
    @Query("SELECT * FROM customers WHERE dirty = 1") suspend fun dirtyRows(): List<CustomerEntity>
    @Upsert suspend fun upsert(customer: CustomerEntity)
    @Upsert suspend fun upsertAll(customers: List<CustomerEntity>)
    @Query("UPDATE customers SET dirty = 0 WHERE id = :id AND updatedAt = :updatedAt") suspend fun clearDirty(id: String, updatedAt: Long)
    @Query("DELETE FROM customers") suspend fun clear()
}

@Dao
interface OrderDao {
    @Query("SELECT * FROM orders WHERE deleted = 0") fun observe(): Flow<List<OrderEntity>>
    @Query("SELECT * FROM orders WHERE deleted = 0") suspend fun observeOnce(): List<OrderEntity>
    @Query("SELECT * FROM orders") suspend fun getAll(): List<OrderEntity>
    @Query("SELECT * FROM orders WHERE id = :id") suspend fun get(id: Int): OrderEntity?
    @Query("SELECT * FROM orders WHERE dirty = 1") suspend fun dirtyRows(): List<OrderEntity>
    @Upsert suspend fun upsert(order: OrderEntity)
    @Upsert suspend fun upsertAll(orders: List<OrderEntity>)
    @Query("UPDATE orders SET dirty = 0 WHERE id = :id AND updatedAt = :updatedAt") suspend fun clearDirty(id: Int, updatedAt: Long)
    @Query("DELETE FROM orders") suspend fun clear()
}

@Dao
interface LedgerDao {
    @Query("SELECT * FROM ledger WHERE deleted = 0") fun observe(): Flow<List<LedgerEntity>>
    @Query("SELECT * FROM ledger WHERE deleted = 0") suspend fun observeOnce(): List<LedgerEntity>
    @Query("SELECT * FROM ledger") suspend fun getAll(): List<LedgerEntity>
    @Query("SELECT * FROM ledger WHERE id = :id") suspend fun get(id: String): LedgerEntity?
    @Query("SELECT * FROM ledger WHERE dirty = 1") suspend fun dirtyRows(): List<LedgerEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(entry: LedgerEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(entries: List<LedgerEntity>)
    @Delete suspend fun delete(entry: LedgerEntity)
    @Query("UPDATE ledger SET dirty = 0 WHERE id = :id AND updatedAt = :updatedAt") suspend fun clearDirty(id: String, updatedAt: Long)
    @Query("DELETE FROM ledger") suspend fun clear()
}

@Dao
interface DayCloseDao {
    @Query("SELECT * FROM day_close WHERE deleted = 0") fun observe(): Flow<List<DayCloseEntity>>
    @Query("SELECT * FROM day_close WHERE deleted = 0") suspend fun observeOnce(): List<DayCloseEntity>
    @Query("SELECT * FROM day_close") suspend fun getAll(): List<DayCloseEntity>
    @Query("SELECT * FROM day_close WHERE date = :date") suspend fun get(date: String): DayCloseEntity?
    @Query("SELECT * FROM day_close WHERE dirty = 1") suspend fun dirtyRows(): List<DayCloseEntity>
    @Upsert suspend fun upsert(day: DayCloseEntity)
    @Upsert suspend fun upsertAll(days: List<DayCloseEntity>)
    @Query("UPDATE day_close SET dirty = 0 WHERE date = :date AND updatedAt = :updatedAt") suspend fun clearDirty(date: String, updatedAt: Long)
    @Query("DELETE FROM day_close") suspend fun clear()
}
