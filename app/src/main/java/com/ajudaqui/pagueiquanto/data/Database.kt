package com.ajudaqui.pagueiquanto.data

import androidx.room.*
import com.ajudaqui.pagueiquanto.model.PriceRecord
import com.ajudaqui.pagueiquanto.model.Product
import kotlinx.coroutines.flow.Flow

@Dao
interface ShoppingDao {
    @Insert
    suspend fun insertProduct(product: Product): Long

    @Insert
    suspend fun insertRecord(record: PriceRecord)

    @Query("SELECT * FROM products")
    fun getAllProducts(): Flow<List<Product>>

    @Query("SELECT * FROM price_records WHERE productId = :productId ORDER BY date DESC LIMIT 1")
    suspend fun getLastPriceRecord(productId: Long): PriceRecord?
}

@Database(entities = [Product::class, PriceRecord::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun shoppingDao(): ShoppingDao
}
