package com.dailyworks.apnalaundry.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ShopEntity::class,
        ServiceEntity::class,
        CustomerEntity::class,
        OrderEntity::class,
        LedgerEntity::class,
        DayCloseEntity::class,
    ],
    version = 2,
    exportSchema = true,
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

        /**
         * v1 -> v2: sync metadata on every table. Existing rows get
         * updatedAt = 0 (they lose last-write-wins against any server copy,
         * which is right — they predate sync) and dirty = 1 (everything is
         * pushed on the first sync after login).
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE shop ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE shop ADD COLUMN dirty INTEGER NOT NULL DEFAULT 1")

                db.execSQL("ALTER TABLE services ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE services ADD COLUMN dirty INTEGER NOT NULL DEFAULT 1")

                db.execSQL("ALTER TABLE customers ADD COLUMN deleted INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE customers ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE customers ADD COLUMN dirty INTEGER NOT NULL DEFAULT 1")

                db.execSQL("ALTER TABLE orders ADD COLUMN deleted INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE orders ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE orders ADD COLUMN dirty INTEGER NOT NULL DEFAULT 1")

                db.execSQL("ALTER TABLE ledger ADD COLUMN deleted INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE ledger ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE ledger ADD COLUMN dirty INTEGER NOT NULL DEFAULT 1")

                db.execSQL("ALTER TABLE day_close ADD COLUMN deleted INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE day_close ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE day_close ADD COLUMN dirty INTEGER NOT NULL DEFAULT 1")
            }
        }
    }
}
