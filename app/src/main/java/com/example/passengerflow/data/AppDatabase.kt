package com.example.passengerflow.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Entity(tableName = "measurements")
data class MeasurementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAtMillis: Long
)

@Entity(tableName = "stops")
data class StopEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val measurementId: Long,
    val name: String?,
    val latitude: Double?,
    val longitude: Double?,
    val arrivalTimeMillis: Long,
    val departureTimeMillis: Long?,
    val entered: Int,
    val exited: Int
)

data class MeasurementWithStops(
    val measurement: MeasurementEntity,
    @Relation(parentColumn = "id", entityColumn = "measurementId")
    val stops: List<StopEntity>
)

@Dao
interface MeasurementDao {
    @Insert
    suspend fun insertMeasurement(measurement: MeasurementEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStops(stops: List<StopEntity>)

    @Query("SELECT * FROM measurements ORDER BY createdAtMillis DESC")
    fun observeMeasurements(): Flow<List<MeasurementEntity>>

    @Query("SELECT * FROM measurements WHERE id = :id LIMIT 1")
    suspend fun findMeasurement(id: Long): MeasurementEntity?

    @Query("SELECT * FROM stops WHERE measurementId = :measurementId ORDER BY arrivalTimeMillis ASC")
    suspend fun getStops(measurementId: Long): List<StopEntity>

    @Query("SELECT * FROM stops WHERE measurementId = :measurementId ORDER BY arrivalTimeMillis ASC")
    fun observeStops(measurementId: Long): Flow<List<StopEntity>>
}

private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE stops ADD COLUMN name TEXT")
    }
}

@Database(
    entities = [MeasurementEntity::class, StopEntity::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun measurementDao(): MeasurementDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "passenger_flow.db"
                ).addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
