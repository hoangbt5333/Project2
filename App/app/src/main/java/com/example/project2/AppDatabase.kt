package com.example.project2

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

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
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
