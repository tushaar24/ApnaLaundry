package com.dailyworks.apnalaundry.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        ShopEntity::class,
        ServiceEntity::class,
        CustomerEntity::class,
        OrderEntity::class,
        LedgerEntity::class,
        DayCloseEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun shopDao(): ShopDao
    abstract fun serviceDao(): ServiceDao
    abstract fun customerDao(): CustomerDao
    abstract fun orderDao(): OrderDao
    abstract fun ledgerDao(): LedgerDao
    abstract fun dayCloseDao(): DayCloseDao

    companion object {
        const val NAME = "apnalaundry.db"
    }
}
