package com.ajudaqui.pagueiquanto.data

import androidx.room.*
import com.ajudaqui.pagueiquanto.model.Account
import com.ajudaqui.pagueiquanto.model.PriceRecord
import com.ajudaqui.pagueiquanto.model.Product
import com.ajudaqui.pagueiquanto.model.Purchase
import kotlinx.coroutines.flow.Flow

@Dao
interface ShoppingDao {
    @Insert
    suspend fun insertAccount(account: Account): Long

    @Insert
    suspend fun insertProduct(product: Product): Long

    @Insert
    suspend fun insertPurchase(purchase: Purchase): Long

    @Insert
    suspend fun insertRecord(record: PriceRecord): Long

    @Query("SELECT * FROM accounts")
    fun getAllAccounts(): Flow<List<Account>>

    @Query("SELECT * FROM products WHERE accountId = :accountId")
    fun getProductsByAccount(accountId: Long): Flow<List<Product>>

    @Query("""
        SELECT pr.* FROM price_records pr
        INNER JOIN purchases p ON pr.purchaseId = p.id
        WHERE pr.productId = :productId
        ORDER BY p.date DESC LIMIT 1
    """)
    suspend fun getLastPriceRecord(productId: Long): PriceRecord?
}

@Database(entities = [Account::class, Product::class, Purchase::class, PriceRecord::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun shoppingDao(): ShoppingDao
}

