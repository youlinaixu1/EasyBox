package com.easybox.app.data.local.dao

import androidx.room.*
import com.easybox.app.data.model.AppItem
import kotlinx.coroutines.flow.Flow

@Dao
interface AppItemDao {
    @Query("SELECT * FROM app_items ORDER BY sortOrder ASC")
    fun observeAll(): Flow<List<AppItem>>

    @Query("SELECT * FROM app_items ORDER BY sortOrder ASC")
    suspend fun getAll(): List<AppItem>

    @Query("SELECT * FROM app_items WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): AppItem?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: AppItem)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<AppItem>)

    @Update
    suspend fun update(item: AppItem)

    @Delete
    suspend fun delete(item: AppItem)

    @Query("DELETE FROM app_items WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE app_items SET sortOrder = :sortOrder WHERE id = :id")
    suspend fun updateSortOrder(id: String, sortOrder: Int)
}
