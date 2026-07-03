package com.ajudaqui.pagueiquanto.repository

import android.content.Context
import android.content.SharedPreferences
import com.ajudaqui.pagueiquanto.data.ShoppingDao
import com.ajudaqui.pagueiquanto.model.Account
import com.ajudaqui.pagueiquanto.model.PriceRecord
import com.ajudaqui.pagueiquanto.model.Product
import com.ajudaqui.pagueiquanto.model.Purchase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File

class ShoppingRepositoryTest {

    private lateinit var fakeDao: FakeShoppingDao
    private lateinit var repository: ShoppingRepository

    @Before
    fun setUp() {
        fakeDao = FakeShoppingDao()
        val fakeContext = FakeContext()
        repository = ShoppingRepository(fakeDao, fakeContext)
    }

    @Test
    fun testHashPassword() {
        val password = "my_secure_password"
        val hash = repository.hashPassword(password)
        
        // Verifica se gera um hash SHA-256 válido (64 caracteres hexadecimais)
        assertEquals(64, hash.length)
        // Garante determismo (mesmo input gera mesmo hash)
        assertEquals(hash, repository.hashPassword(password))
        assertNotEquals(hash, repository.hashPassword("other_password"))
    }

    @Test
    fun testCompressAndDecompress() {
        val originalText = "{\"backup_version\":1,\"data\":{\"accounts\":[{\"id\":1,\"name\":\"Feira\"}]}}"
        
        val compressed = repository.compress(originalText)
        val decompressed = repository.decompress(compressed)
        
        assertEquals(originalText, decompressed)
    }

    @Test
    fun testCredentialsSavingAndRetrieval() {
        assertFalse(repository.hasBackupCredentials())
        
        val email = "test@pagueiquanto.com"
        val hash = repository.hashPassword("123456")
        
        repository.saveBackupCredentials(email, hash)
        
        assertTrue(repository.hasBackupCredentials())
        assertEquals(email, repository.getBackupEmail())
        assertEquals(hash, repository.getBackupPasswordHash())
    }

    @Test
    fun testChangeTracking() {
        // Inicialmente sem alterações pendentes
        assertFalse(repository.hasPendingChanges())
        
        // Simula uma mudança local disparando a gravação
        // Como não podemos chamar métodos de banco facilmente sem um DB completo nas transações,
        // vamos testar chamando um método que altera dados, por exemplo, criar nova conta
        runBlocking {
            repository.createNewAccount("Supermercado", "🛒")
        }
        
        // Deve registrar que há alterações pendentes
        assertTrue(repository.hasPendingChanges())
        
        // Marca como sincronizado
        repository.markBackupSynced()
        assertFalse(repository.hasPendingChanges())
    }

    @Test
    fun testExportAllData() = runBlocking {
        // Preenche o DAO fake com dados de teste
        fakeDao.accounts.add(Account(id = 1, name = "Feira", icon = "🍎"))
        fakeDao.products.add(Product(id = 10, name = "Tomate", unit = "kg", accountId = 1))
        fakeDao.purchases.add(Purchase(id = 100, accountId = 1, date = 1719532500000L, store = "Supermercado X", nickname = "Feira Semanal"))
        fakeDao.priceRecords.add(PriceRecord(id = 1000, productId = 10, purchaseId = 100, unitPrice = 8.99, quantity = 1.5))

        repository.saveBackupCredentials("user@test.com", "hash")

        val jsonString = repository.exportAllData()
        
        // Verifica se campos essenciais estão presentes no JSON exportado
        assertTrue(jsonString.contains("\"backup_version\":1"))
        assertTrue(jsonString.contains("\"name\":\"Feira\""))
        assertTrue(jsonString.contains("\"name\":\"Tomate\""))
        assertTrue(jsonString.contains("\"store\":\"Supermercado X\""))
        assertTrue(jsonString.contains("\"unitPrice\":8.99"))
        assertTrue(jsonString.contains("\"device_id\":\"user@test.com\""))
    }

    @Test
    fun testRestoreAndMergeBackup() = runBlocking {
        // Prepara JSON de backup simulado
        val backupJson = """
        {
            "backup_version": 1,
            "data": {
                "accounts": [
                    { "id": 5, "name": "Mercado", "icon": "🛒" }
                ],
                "products": [
                    { "id": 50, "name": "Arroz", "unit": "kg", "accountId": 5, "brand": "Marca X" }
                ],
                "purchases": [
                    { "id": 500, "accountId": 5, "date": 1719532500000, "store": "Supermercado A", "nickname": "Compra Mensal", "isDraft": false, "invoiceUrl": null }
                ],
                "price_records": [
                    { "id": 5000, "productId": 50, "purchaseId": 500, "unitPrice": 5.50, "quantity": 2.0 }
                ]
            }
        }
        """.trimIndent()

        // Garante que o banco está vazio inicialmente
        assertTrue(fakeDao.accounts.isEmpty())
        assertTrue(fakeDao.products.isEmpty())
        assertTrue(fakeDao.purchases.isEmpty())
        assertTrue(fakeDao.priceRecords.isEmpty())

        // Executa a restauração e mesclagem
        repository.restoreAndMergeBackup(backupJson)

        // Verifica se os dados foram inseridos corretamente no banco/DAO
        assertEquals(1, fakeDao.accounts.size)
        assertEquals("Mercado", fakeDao.accounts[0].name)
        assertEquals("🛒", fakeDao.accounts[0].icon)

        assertEquals(1, fakeDao.products.size)
        assertEquals("Arroz", fakeDao.products[0].name)
        assertEquals("kg", fakeDao.products[0].unit)
        assertEquals("Marca X", fakeDao.products[0].brand)

        assertEquals(1, fakeDao.purchases.size)
        assertEquals("Supermercado A", fakeDao.purchases[0].store)
        assertEquals("Compra Mensal", fakeDao.purchases[0].nickname)

        assertEquals(1, fakeDao.priceRecords.size)
        assertEquals(5.50, fakeDao.priceRecords[0].unitPrice, 0.0)
        assertEquals(2.0, fakeDao.priceRecords[0].quantity, 0.0)
    }

    // --- CLASSES FAKES PARA MOCK EM TESTES UNITÁRIOS JVM ---

    private class FakeShoppingDao : ShoppingDao {
        val accounts = mutableListOf<Account>()
        val products = mutableListOf<Product>()
        val purchases = mutableListOf<Purchase>()
        val priceRecords = mutableListOf<PriceRecord>()

        override suspend fun insertAccount(account: Account): Long {
            val id = (accounts.size + 1).toLong()
            accounts.add(account.copy(id = id))
            return id
        }

        override suspend fun insertProduct(product: Product): Long {
            val id = (products.size + 1).toLong()
            products.add(product.copy(id = id))
            return id
        }

        override suspend fun insertProducts(productsList: List<Product>): List<Long> {
            return productsList.map { insertProduct(it) }
        }

        override suspend fun insertPurchase(purchase: Purchase): Long {
            val id = (purchases.size + 1).toLong()
            purchases.add(purchase.copy(id = id))
            return id
        }

        override suspend fun insertPurchases(purchasesList: List<Purchase>): List<Long> {
            return purchasesList.map { insertPurchase(it) }
        }

        override suspend fun insertRecord(record: PriceRecord): Long {
            val id = (priceRecords.size + 1).toLong()
            priceRecords.add(record.copy(id = id))
            return id
        }

        override suspend fun insertRecords(records: List<PriceRecord>): List<Long> {
            return records.map { insertRecord(it) }
        }

        override suspend fun deleteAccountById(id: Long) {
            accounts.removeIf { it.id == id }
        }

        override suspend fun deletePurchaseById(id: Long) {
            purchases.removeIf { it.id == id }
        }

        override suspend fun deleteProductById(id: Long) {
            products.removeIf { it.id == id }
        }

        override suspend fun deletePriceRecordById(id: Long) {
            priceRecords.removeIf { it.id == id }
        }

        override fun getAllAccounts(): Flow<List<Account>> = flowOf(accounts)
        override fun getAllProducts(): Flow<List<Product>> = flowOf(products)
        override fun getAllPurchases(): Flow<List<Purchase>> = flowOf(purchases)
        override fun getAllPriceRecords(): Flow<List<PriceRecord>> = flowOf(priceRecords)

        override fun getProductsByAccount(accountId: Long): Flow<List<Product>> {
            return flowOf(products.filter { it.accountId == accountId })
        }

        override suspend fun getLastPriceRecord(productId: Long): PriceRecord? {
            return priceRecords.filter { it.productId == productId }.lastOrNull()
        }

        override suspend fun updatePriceRecord(id: Long, unitPrice: Double, quantity: Double) {
            val index = priceRecords.indexOfFirst { it.id == id }
            if (index != -1) {
                priceRecords[index] = priceRecords[index].copy(unitPrice = unitPrice, quantity = quantity)
            }
        }

        override suspend fun updatePurchaseNickname(id: Long, nickname: String?) {
            val index = purchases.indexOfFirst { it.id == id }
            if (index != -1) {
                purchases[index] = purchases[index].copy(nickname = nickname)
            }
        }

        override suspend fun getPriceRecordById(id: Long): PriceRecord? {
            return priceRecords.find { it.id == id }
        }

        override suspend fun getPurchasesByDate(date: Long): List<Purchase> {
            return purchases.filter { it.date == date }
        }

        override suspend fun getProductById(id: Long): Product? {
            return products.find { it.id == id }
        }
    }

    private class FakeContext : android.content.ContextWrapper(null) {
        private val fakePrefs = FakeSharedPreferences()

        override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences {
            return fakePrefs
        }

        override fun getApplicationContext(): Context = this
    }

    private class FakeSharedPreferences : SharedPreferences {
        private val map = mutableMapOf<String, Any>()

        override fun getAll(): MutableMap<String, *> = map
        override fun getString(key: String?, defValue: String?): String? = map[key] as? String ?: defValue
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = map[key] as? MutableSet<String> ?: defValues
        override fun getInt(key: String?, defValue: Int): Int = map[key] as? Int ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = map[key] as? Long ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = map[key] as? Float ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = map[key] as? Boolean ?: defValue
        override fun contains(key: String?): Boolean = map.containsKey(key)
        override fun edit(): SharedPreferences.Editor = FakeEditor(map)
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

        private class FakeEditor(private val sharedMap: MutableMap<String, Any>) : SharedPreferences.Editor {
            private val tempMap = mutableMapOf<String, Any>()

            override fun putString(key: String, value: String?): SharedPreferences.Editor {
                if (value != null) tempMap[key] = value
                return this
            }

            override fun putStringSet(key: String, values: MutableSet<String>?): SharedPreferences.Editor {
                if (values != null) tempMap[key] = values
                return this
            }

            override fun putInt(key: String, value: Int): SharedPreferences.Editor {
                tempMap[key] = value
                return this
            }

            override fun putLong(key: String, value: Long): SharedPreferences.Editor {
                tempMap[key] = value
                return this
            }

            override fun putFloat(key: String, value: Float): SharedPreferences.Editor {
                tempMap[key] = value
                return this
            }

            override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor {
                tempMap[key] = value
                return this
            }

            override fun remove(key: String): SharedPreferences.Editor {
                tempMap.remove(key)
                return this
            }

            override fun clear(): SharedPreferences.Editor {
                tempMap.clear()
                return this
            }

            override fun commit(): Boolean {
                sharedMap.putAll(tempMap)
                return true
            }

            override fun apply() {
                sharedMap.putAll(tempMap)
            }
        }
    }
}
