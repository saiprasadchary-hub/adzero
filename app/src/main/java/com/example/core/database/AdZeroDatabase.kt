package com.example.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        DnsLogEntity::class,
        CustomRuleEntity::class,
        BlocklistSourceEntity::class,
        BlocklistDomainEntity::class,
        AppRuleEntity::class,
        GhostMessageEntity::class,
        GhostScheduleEntity::class,
        ServiceEventEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AdZeroDatabase : RoomDatabase() {
    abstract fun dnsLogDao(): DnsLogDao
    abstract fun customRuleDao(): CustomRuleDao
    abstract fun blocklistDao(): BlocklistDao
    abstract fun appRuleDao(): AppRuleDao
    abstract fun ghostMessageDao(): GhostMessageDao
    abstract fun ghostScheduleDao(): GhostScheduleDao
    abstract fun serviceEventDao(): ServiceEventDao

    companion object {
        @Volatile
        private var INSTANCE: AdZeroDatabase? = null

        fun getInstance(context: Context): AdZeroDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AdZeroDatabase::class.java,
                    "adzero_local.db"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
