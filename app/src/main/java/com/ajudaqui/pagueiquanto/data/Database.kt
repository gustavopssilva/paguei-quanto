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
    suspend fun insertProducts(products: List<Product>): List<Long>

    @Insert
    suspend fun insertPurchase(purchase: Purchase): Long

    @Insert
    suspend fun insertPurchases(purchases: List<Purchase>): List<Long>

    @Insert
    suspend fun insertRecord(record: PriceRecord): Long

    @Insert
    suspend fun insertRecords(records: List<PriceRecord>): List<Long>

    @Query("DELETE FROM accounts WHERE id = :id")
    suspend fun deleteAccountById(id: Long)

    @Query("DELETE FROM purchases WHERE id = :id")
    suspend fun deletePurchaseById(id: Long)

    @Query("DELETE FROM products WHERE id = :id")
    suspend fun deleteProductById(id: Long)

    @Query("DELETE FROM price_records WHERE id = :id")
    suspend fun deletePriceRecordById(id: Long)

    @Query("SELECT * FROM accounts")
    fun getAllAccounts(): Flow<List<Account>>

    @Query("SELECT * FROM products")
    fun getAllProducts(): Flow<List<Product>>

    @Query("SELECT * FROM purchases")
    fun getAllPurchases(): Flow<List<Purchase>>

    @Query("SELECT * FROM price_records")
    fun getAllPriceRecords(): Flow<List<PriceRecord>>

    @Query("SELECT * FROM products WHERE accountId = :accountId")
    fun getProductsByAccount(accountId: Long): Flow<List<Product>>

    @Query("""
        SELECT pr.* FROM price_records pr
        INNER JOIN purchases p ON pr.purchaseId = p.id
        WHERE pr.productId = :productId
        ORDER BY p.date DESC LIMIT 1
    """)
    suspend fun getLastPriceRecord(productId: Long): PriceRecord?

    @Query("UPDATE price_records SET unitPrice = :unitPrice, quantity = :quantity WHERE id = :id")
    suspend fun updatePriceRecord(id: Long, unitPrice: Double, quantity: Double)

    @Query("UPDATE purchases SET nickname = :nickname WHERE id = :id")
    suspend fun updatePurchaseNickname(id: Long, nickname: String?)

    @Query("SELECT * FROM price_records WHERE id = :id")
    suspend fun getPriceRecordById(id: Long): PriceRecord?

    @Query("SELECT * FROM purchases WHERE date = :date")
    suspend fun getPurchasesByDate(date: Long): List<Purchase>
}

@Database(entities = [Account::class, Product::class, Purchase::class, PriceRecord::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun shoppingDao(): ShoppingDao
}

