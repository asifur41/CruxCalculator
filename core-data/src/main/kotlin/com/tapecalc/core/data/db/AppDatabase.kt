package com.tapecalc.core.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/** Local-only database. Nothing in it ever leaves the device unless the user exports it. */
@Database(
    entities = [WorkspaceEntity::class, HistoryEntity::class, VariableEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun workspaceDao(): WorkspaceDao
    abstract fun historyDao(): HistoryDao
    abstract fun variableDao(): VariableDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "tape-calculator.db").build()
    }
}
