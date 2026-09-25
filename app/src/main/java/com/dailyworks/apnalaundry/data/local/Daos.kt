package com.dailyworks.apnalaundry.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ShopDao {
    @Query("SELECT * FROM shop WHERE id = 1") fun observe(): Flow<ShopEntity?>
    @Query("SELECT * FROM shop WHERE id = 1") suspend fun get(): ShopEntity?
    @Upsert suspend fun upsert(shop: ShopEntity)
}

@Dao
interface ServiceDao {
    @Query("SELECT * FROM services WHERE deleted = 0 ORDER BY sortOrder") fun observe(): Flow<List<ServiceEntity>>
    @Query("SELECT * FROM services") suspend fun getAll(): List<ServiceEntity>
    @Upsert suspend fun upsert(service: ServiceEntity)
    @Upsert suspend fun upsertAll(services: List<ServiceEntity>)
    @Query("UPDATE services SET deleted = :deleted WHERE id = :id") suspend fun setDeleted(id: String, deleted: Boolean)
    @Query("DELETE FROM services") suspend fun clear()
}

@Dao
interface CustomerDao {
    @Query("SELECT * FROM customers") fun observe(): Flow<List<CustomerEntity>>
    @Query("SELECT * FROM customers") suspend fun observeOnce(): List<CustomerEntity>
    @Upsert suspend fun upsert(customer: CustomerEntity)
    @Upsert suspend fun upsertAll(customers: List<CustomerEntity>)
    @Query("DELETE FROM customers") suspend fun clear()
}

@Dao
interface OrderDao {
    @Query("SELECT * FROM orders") fun observe(): Flow<List<OrderEntity>>
    @Query("SELECT * FROM orders") suspend fun observeOnce(): List<OrderEntity>
    @Query("SELECT * FROM orders WHERE id = :id") suspend fun get(id: Int): OrderEntity?
    @Upsert suspend fun upsert(order: OrderEntity)
    @Upsert suspend fun upsertAll(orders: List<OrderEntity>)
    @Query("DELETE FROM orders") suspend fun clear()
}

@Dao
interface LedgerDao {
    @Query("SELECT * FROM ledger") fun observe(): Flow<List<LedgerEntity>>
    @Query("SELECT * FROM ledger") suspend fun observeOnce(): List<LedgerEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(entry: LedgerEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(entries: List<LedgerEntity>)
    @Delete suspend fun delete(entry: LedgerEntity)
    @Query("DELETE FROM ledger") suspend fun clear()
}

@Dao
interface DayCloseDao {
    @Query("SELECT * FROM day_close") fun observe(): Flow<List<DayCloseEntity>>
    @Query("SELECT * FROM day_close") suspend fun observeOnce(): List<DayCloseEntity>
    @Upsert suspend fun upsert(day: DayCloseEntity)
    @Upsert suspend fun upsertAll(days: List<DayCloseEntity>)
    @Query("DELETE FROM day_close") suspend fun clear()
}
