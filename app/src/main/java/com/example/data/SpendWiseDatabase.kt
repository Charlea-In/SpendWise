package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ExpenseEntity::class,
        CategoryBudgetEntity::class,
        AccountEntity::class,
        CommitmentEntity::class,
        DetectedTransactionEntity::class,
        AccountTransferEntity::class,
        PointsAccountEntity::class
    ],
    version = 8,
    exportSchema = false
)
abstract class SpendWiseDatabase : RoomDatabase() {
    abstract fun expenseDao(): ExpenseDao
    abstract fun categoryBudgetDao(): CategoryBudgetDao
    abstract fun accountDao(): AccountDao
    abstract fun commitmentDao(): CommitmentDao
    abstract fun detectedTransactionDao(): DetectedTransactionDao
    abstract fun accountTransferDao(): AccountTransferDao
    abstract fun pointsAccountDao(): PointsAccountDao

    companion object {
        @Volatile
        private var INSTANCE: SpendWiseDatabase? = null

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `financial_commitments` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `title` TEXT NOT NULL,
                        `type` TEXT NOT NULL,
                        `amountCents` INTEGER NOT NULL,
                        `targetOrTotalCents` INTEGER NOT NULL,
                        `progressCents` INTEGER NOT NULL,
                        `cycle` TEXT NOT NULL,
                        `dueDayOfMonth` INTEGER NOT NULL,
                        `statementDayOfMonth` INTEGER NOT NULL,
                        `scheduledDateMillis` INTEGER NOT NULL,
                        `totalInstallments` INTEGER NOT NULL,
                        `completedInstallments` INTEGER NOT NULL,
                        `accountName` TEXT NOT NULL,
                        `category` TEXT NOT NULL,
                        `note` TEXT NOT NULL,
                        `reminderEnabled` INTEGER NOT NULL,
                        `isActive` INTEGER NOT NULL,
                        `lastCompletedCycleKey` TEXT NOT NULL,
                        `createdAtMillis` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `detected_transactions` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `fingerprint` TEXT NOT NULL,
                        `sourcePackage` TEXT NOT NULL,
                        `sourceAppLabel` TEXT NOT NULL,
                        `rawTitle` TEXT NOT NULL,
                        `rawMessage` TEXT NOT NULL,
                        `merchant` TEXT NOT NULL,
                        `amountCents` INTEGER NOT NULL,
                        `suggestedCategory` TEXT NOT NULL,
                        `paymentMethod` TEXT NOT NULL,
                        `cardLastFour` TEXT NOT NULL,
                        `matchedAccountName` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `receivedAtMillis` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `account_transfers` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `fromAccountName` TEXT NOT NULL,
                        `toAccountName` TEXT NOT NULL,
                        `amountCents` INTEGER NOT NULL,
                        `timestampMillis` INTEGER NOT NULL,
                        `note` TEXT NOT NULL,
                        `isDemo` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `points_accounts` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `pointsBalance` INTEGER NOT NULL,
                        `unitLabel` TEXT NOT NULL,
                        `centsPerPointTimes100` INTEGER NOT NULL,
                        `note` TEXT NOT NULL,
                        `updatedAtMillis` INTEGER NOT NULL,
                        `createdAtMillis` INTEGER NOT NULL,
                        `isDemo` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        fun getInstance(context: Context): SpendWiseDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    SpendWiseDatabase::class.java,
                    "spendwise_db"
                )
                    .addMigrations(MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
