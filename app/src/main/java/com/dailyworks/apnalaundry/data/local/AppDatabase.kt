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
    version = 9,
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

        /**
         * v2 -> v3: drop the shop.closeTime column (shop closing time removed
         * from the product). SQLite can't DROP COLUMN on older engines, so the
         * table is recreated. Rows are re-stamped dirty so the change pushes.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE shop_new (" +
                        "id INTEGER NOT NULL PRIMARY KEY, " +
                        "name TEXT NOT NULL, " +
                        "phone TEXT NOT NULL, " +
                        "expressPct INTEGER NOT NULL, " +
                        "nextOrder INTEGER NOT NULL, " +
                        "nextCust INTEGER NOT NULL, " +
                        "updatedAt INTEGER NOT NULL DEFAULT 0, " +
                        "dirty INTEGER NOT NULL DEFAULT 1)"
                )
                db.execSQL(
                    "INSERT INTO shop_new (id, name, phone, expressPct, nextOrder, nextCust, updatedAt, dirty) " +
                        "SELECT id, name, phone, expressPct, nextOrder, nextCust, updatedAt, 1 FROM shop"
                )
                db.execSQL("DROP TABLE shop")
                db.execSQL("ALTER TABLE shop_new RENAME TO shop")
            }
        }

        /** v8 -> v9: the owner's free-text note on an order. */
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE orders ADD COLUMN note TEXT NOT NULL DEFAULT ''")
            }
        }

        /** v7 -> v8: GST per order (on / rate / exclusive|inclusive) + the shop's last-used GST setting. */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE orders ADD COLUMN gstOn INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE orders ADD COLUMN gstPct REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE orders ADD COLUMN gstMode TEXT NOT NULL DEFAULT 'excl'")
                db.execSQL("ALTER TABLE shop ADD COLUMN gstOn INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE shop ADD COLUMN gstPct REAL NOT NULL DEFAULT 18")
                db.execSQL("ALTER TABLE shop ADD COLUMN gstMode TEXT NOT NULL DEFAULT 'excl'")
            }
        }

        /** v6 -> v7: email address printed on the bill. */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE shop ADD COLUMN email TEXT NOT NULL DEFAULT ''")
            }
        }

        /** v5 -> v6: express / discount as a % of the clothes per order (0 = fixed ₹). */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE orders ADD COLUMN exPct INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE orders ADD COLUMN discPct INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** v4 -> v5: owner-set bill / serial number on an order ("" = the order id). */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE orders ADD COLUMN serialNo TEXT NOT NULL DEFAULT ''")
            }
        }

        /** v3 -> v4: bill details (phone on bill, address, GSTIN, UPI, logo, terms, design) + onboarding step. */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                for (col in listOf("billPhone", "address", "gstin", "upiId", "logoId", "termsCustom", "onboardingStep")) {
                    db.execSQL("ALTER TABLE shop ADD COLUMN $col TEXT NOT NULL DEFAULT ''")
                }
                db.execSQL("ALTER TABLE shop ADD COLUMN termsJson TEXT NOT NULL DEFAULT '[]'")
                db.execSQL("ALTER TABLE shop ADD COLUMN billTemplate TEXT NOT NULL DEFAULT 'classic'")
            }
        }
    }
}
