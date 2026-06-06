package com.example.project2

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

// version 1 -> 2: thêm 2 cột vào lich_su_moi_truong + 2 bảng mới (canh_bao, tuoi).
// Đồ án nên dùng fallbackToDestructiveMigration cho gọn (mất dữ liệu cũ khi nâng cấp schema).
// Nếu cần GIỮ dữ liệu cũ, hãy viết Migration(1,2) thật (xem ghi chú cuối file INTEGRATION).
@Database(
    entities = [ThongSoEntity::class, AlertEntity::class, WateringEntity::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun thongSoDao(): ThongSoDao
    abstract fun alertDao(): AlertDao
    abstract fun wateringDao(): WateringDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "iot_database"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE lich_su_moi_truong " +
                            "ADD COLUMN soilScore INTEGER NOT NULL DEFAULT 100"
                )

                db.execSQL(
                    "ALTER TABLE lich_su_moi_truong " +
                            "ADD COLUMN soilStatusText TEXT NOT NULL DEFAULT ''"
                )

                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_lich_su_moi_truong_timestamp " +
                            "ON lich_su_moi_truong(timestamp)"
                )

                db.execSQL(
                    """
            CREATE TABLE IF NOT EXISTS lich_su_canh_bao (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                timestamp INTEGER NOT NULL,
                type TEXT NOT NULL,
                severity TEXT NOT NULL,
                title TEXT NOT NULL,
                recommendation TEXT NOT NULL
            )
            """.trimIndent()
                )

                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_lich_su_canh_bao_timestamp " +
                            "ON lich_su_canh_bao(timestamp)"
                )

                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_lich_su_canh_bao_type " +
                            "ON lich_su_canh_bao(type)"
                )

                db.execSQL(
                    """
            CREATE TABLE IF NOT EXISTS lich_su_tuoi (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                timestamp INTEGER NOT NULL,
                mode TEXT NOT NULL,
                durationSeconds INTEGER NOT NULL
            )
            """.trimIndent()
                )

                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_lich_su_tuoi_timestamp " +
                            "ON lich_su_tuoi(timestamp)"
                )
            }
        }
    }
}
