package com.ajudaqui.pagueiquanto.repository

import com.ajudaqui.pagueiquanto.data.ShoppingDao
import com.ajudaqui.pagueiquanto.model.AccountState
import com.ajudaqui.pagueiquanto.model.PriceRecordState
import com.ajudaqui.pagueiquanto.model.ProductState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale

import android.content.Context
import java.security.MessageDigest
import org.json.JSONObject
import org.json.JSONArray
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream
import com.ajudaqui.pagueiquanto.PagueiQuantoApplication

class ShoppingRepository(private val dao: ShoppingDao, private val context: Context) {

    // Observa reativamente os dados do banco de dados e mapeia para o estado esperado na UI (UiModels)
    val accounts: StateFlow<List<AccountState>> = combine(
        dao.getAllAccounts(),
        dao.getAllProducts(),
        dao.getAllPurchases(),
        dao.getAllPriceRecords()
    ) { dbAccounts, dbProducts, dbPurchases, dbPriceRecords ->
        
        // Mapeia purchaseId para a compra correspondente apenas para compras concluídas (não rascunhos)
        val completedPurchases = dbPurchases.filter { !it.isDraft }
        val purchaseMap = completedPurchases.associateBy { it.id }

        // Mapeia productId para a lista de registros de preço concluídos correspondentes (PriceRecordState)
        val recordsByProduct = dbPriceRecords
            .filter { purchaseMap.containsKey(it.purchaseId) }
            .groupBy { it.productId }
            .mapValues { (_, records) ->
                records.map { record ->
                    val purchase = purchaseMap[record.purchaseId]
                    val dateStr = purchase?.let {
                        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(java.util.Date(it.date))
                    } ?: "2026-06-10"
                    PriceRecordState(
                        id = record.id.toString(),
                        date = dateStr,
                        store = purchase?.store ?: "Loja Padrão",
                        unitPrice = record.unitPrice,
                        quantity = record.quantity,
                        nickname = purchase?.nickname
                    )
                }
            }

        // Mapeia accountId para a lista de produtos (ProductState)
        val productsByAccount = dbProducts.groupBy { it.accountId }.mapValues { (_, products) ->
            products.map { product ->
                ProductState(
                    id = product.id.toString(),
                    name = product.name,
                    unit = product.unit,
                    brand = product.brand,
                    history = recordsByProduct[product.id] ?: emptyList()
                )
            }
        }

        // Reconstrói a lista de AccountState de forma reativa a partir do banco SQLite
        dbAccounts.map { account ->
            val accountProducts = productsByAccount[account.id] ?: emptyList()
            val hasHistory = accountProducts.flatMap { it.history }.isNotEmpty()
            
            AccountState(
                id = account.id.toString(),
                name = account.name,
                icon = account.icon,
                products = accountProducts,
                nextPurchasePrediction = if (hasHistory) "Esta semana" else "Sem previsão",
                predictionProgress = if (hasHistory) 0.7f else 0f
            )
        }
    }.stateIn(
        scope = CoroutineScope(Dispatchers.Default),
        started = SharingStarted.Eagerly,
        initialValue = emptyList()
    )



    // --- LÓGICA DE NEGÓCIO (BUSINESS LOGIC) ---

    fun getLatestPurchaseGlobal(): Pair<AccountState, String>? {
        return accounts.value
            .filter { it.lastPurchaseDate != null }
            .map { it to it.lastPurchaseDate!! }
            .maxByOrNull { it.second }
    }

    fun getGlobalHistory(): List<Triple<AccountState, String, Double>> {
        return accounts.value.flatMap { acc ->
            acc.products.flatMap { p -> p.history.map { it.date to acc } }
                .distinctBy { it.first + it.second.id }
                .map { (date, account) ->
                    val total = calculatePurchaseTotal(account, date)
                    Triple(account, date, total)
                }
        }.sortedByDescending { it.second }
    }

    fun getGlobalRecentItems(): List<ProductState> {
        val latest = getLatestPurchaseGlobal()?.second ?: return emptyList()
        return accounts.value.flatMap { it.products }
            .filter { it.history.any { h -> h.date == latest } }
    }

    fun getPurchaseItems(date: String, nickname: String?): List<Pair<ProductState, PriceRecordState>> {
        val account = accounts.value.find { acc -> 
            acc.products.any { p -> p.history.any { h -> h.date == date && h.nickname == nickname } } 
        } ?: return emptyList()
        
        return account.products.flatMap { product ->
            product.history
                .filter { it.date == date && it.nickname == nickname }
                .map { product to it }
        }
    }

    fun calculatePurchaseTotal(account: AccountState, date: String): Double {
        return account.products.sumOf { p -> 
            p.history.find { it.date == date }?.let { it.unitPrice * it.quantity } ?: 0.0 
        }
    }

    // --- AÇÕES DE PERSISTÊNCIA REAL NO BANCO SQLITE (ROOM) ---

    suspend fun createNewAccount(name: String, icon: String) {
        withContext(Dispatchers.IO) {
            dao.insertAccount(com.ajudaqui.pagueiquanto.model.Account(name = name, icon = icon))
        }
        notifyDatabaseChanged()
    }

    suspend fun getDraftPurchase(accountId: String): com.ajudaqui.pagueiquanto.model.Purchase? {
        val accId = accountId.toLongOrNull() ?: return null
        return withContext(Dispatchers.IO) {
            dao.getAllPurchases().first().find { it.accountId == accId && it.isDraft }
        }
    }

    suspend fun getDraftPriceRecords(purchaseId: Long): List<com.ajudaqui.pagueiquanto.model.PriceRecord> {
        return withContext(Dispatchers.IO) {
            dao.getAllPriceRecords().first().filter { it.purchaseId == purchaseId }
        }
    }

    suspend fun getProductById(productId: Long): com.ajudaqui.pagueiquanto.model.Product? {
        return withContext(Dispatchers.IO) {
            dao.getProductById(productId)
        }
    }

    /**
     * Salva o rascunho atual no banco e retorna o mapa de ids temporários ("new_...") para o id real
     * gerado no banco, para que o chamador possa reusar o produto e evitar duplicatas em saves seguintes.
     */
    suspend fun saveDraftTransaction(accountId: String, items: List<com.ajudaqui.pagueiquanto.viewmodel.PurchaseItemState>): Map<String, String> {
        val accId = accountId.toLongOrNull() ?: return emptyMap()
        val resolvedIds = mutableMapOf<String, String>()
        withContext(Dispatchers.IO) {
            val allPurchases = dao.getAllPurchases().first()
            val draft = allPurchases.find { it.accountId == accId && it.isDraft }
            val dateMillis = System.currentTimeMillis()
            val draftId = if (draft == null) {
                dao.insertPurchase(com.ajudaqui.pagueiquanto.model.Purchase(
                    accountId = accId,
                    date = dateMillis,
                    store = "Loja Rascunho",
                    nickname = "Rascunho",
                    isDraft = true
                ))
            } else {
                draft.id
            }

            val existingRecords = dao.getAllPriceRecords().first().filter { it.purchaseId == draftId }
            existingRecords.forEach {
                dao.deletePriceRecordById(it.id)
            }

            items.forEach { item ->
                var prodId = item.productId.toLongOrNull()

                if (prodId == null || item.productId.startsWith("new_")) {
                    prodId = dao.insertProduct(com.ajudaqui.pagueiquanto.model.Product(
                        name = item.productName,
                        unit = item.unit,
                        accountId = accId,
                        brand = item.brand
                    ))
                    resolvedIds[item.productId] = prodId.toString()
                }

                val qtyVal = if (item.actualQty.isBlank()) 1.0 else (item.actualQty.replace(",", ".").toDoubleOrNull() ?: 1.0)
                dao.insertRecord(com.ajudaqui.pagueiquanto.model.PriceRecord(
                    productId = prodId,
                    purchaseId = draftId,
                    unitPrice = item.currentPrice.replace(",", ".").toDoubleOrNull() ?: 0.0,
                    quantity = qtyVal
                ))
            }
        }
        notifyDatabaseChanged()
        return resolvedIds
    }

    suspend fun savePurchaseTransaction(
        accountId: String, 
        store: String, 
        nickname: String?, 
        items: List<com.ajudaqui.pagueiquanto.viewmodel.PurchaseItemState>,
        invoiceUrl: String? = null
    ) {
        val accId = accountId.toLongOrNull() ?: return
        
        withContext(Dispatchers.IO) {
            val dateMillis = System.currentTimeMillis()
            
            val allPurchases = dao.getAllPurchases().first()
            val draft = allPurchases.find { it.accountId == accId && it.isDraft }
            if (draft != null) {
                dao.deletePurchaseById(draft.id)
            }
            
            val purchaseId = dao.insertPurchase(com.ajudaqui.pagueiquanto.model.Purchase(
                accountId = accId,
                date = dateMillis,
                store = store,
                nickname = nickname,
                isDraft = false,
                invoiceUrl = invoiceUrl
            ))

            items.forEach { item ->
                var prodId = item.productId.toLongOrNull()
                
                if (prodId == null || item.productId.startsWith("new_")) {
                    prodId = dao.insertProduct(com.ajudaqui.pagueiquanto.model.Product(
                        name = item.productName,
                        unit = item.unit,
                        accountId = accId,
                        brand = item.brand
                    ))
                }

                val qtyVal = if (item.actualQty.isBlank()) 1.0 else (item.actualQty.replace(",", ".").toDoubleOrNull() ?: 1.0)
                dao.insertRecord(com.ajudaqui.pagueiquanto.model.PriceRecord(
                    productId = prodId,
                    purchaseId = purchaseId,
                    unitPrice = item.currentPrice.replace(",", ".").toDoubleOrNull() ?: 0.0,
                    quantity = qtyVal
                ))
            }
        }
        notifyDatabaseChanged()
    }

    suspend fun deleteAccount(accountId: String) {
        withContext(Dispatchers.IO) {
            dao.deleteAccountById(accountId.toLongOrNull() ?: return@withContext)
        }
        notifyDatabaseChanged()
    }

    suspend fun deleteProduct(productId: String) {
        withContext(Dispatchers.IO) {
            dao.deleteProductById(productId.toLongOrNull() ?: return@withContext)
        }
        notifyDatabaseChanged()
    }

    suspend fun deletePriceRecord(recordId: String) {
        withContext(Dispatchers.IO) {
            dao.deletePriceRecordById(recordId.toLongOrNull() ?: return@withContext)
        }
        notifyDatabaseChanged()
    }

    suspend fun deletePurchaseByDate(date: String, nickname: String?) {
        val recordsToDelete = getPurchaseItems(date, nickname).map { it.second }
        withContext(Dispatchers.IO) {
            recordsToDelete.forEach {
                dao.deletePriceRecordById(it.id.toLongOrNull() ?: return@forEach)
            }
        }
        notifyDatabaseChanged()
    }

    suspend fun updatePriceRecord(recordId: String, unitPrice: Double, quantity: Double) {
        val recId = recordId.toLongOrNull() ?: return
        withContext(Dispatchers.IO) {
            dao.updatePriceRecord(recId, unitPrice, quantity)
        }
        notifyDatabaseChanged()
    }

    suspend fun addItemToExistingPurchase(
        date: String,
        nickname: String?,
        productName: String,
        unit: String,
        price: Double,
        quantity: Double,
        brand: String? = null
    ) {
        withContext(Dispatchers.IO) {
            val allPurchases = dao.getAllPurchases().first()
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val purchase = allPurchases.find { p ->
                val dateStr = sdf.format(java.util.Date(p.date))
                dateStr == date && p.nickname == nickname
            } ?: return@withContext

            val accId = purchase.accountId

            val allProducts = dao.getAllProducts().first()
            val existingProduct = allProducts.find { it.accountId == accId && it.name.equals(productName, ignoreCase = true) }
            
            val prodId = existingProduct?.id ?: dao.insertProduct(
                com.ajudaqui.pagueiquanto.model.Product(
                    name = productName,
                    unit = unit,
                    accountId = accId,
                    brand = brand
                )
            )

            dao.insertRecord(
                com.ajudaqui.pagueiquanto.model.PriceRecord(
                    productId = prodId,
                    purchaseId = purchase.id,
                    unitPrice = price,
                    quantity = quantity
                )
            )
        }
        notifyDatabaseChanged()
    }
    private val prefs = context.getSharedPreferences("backup_prefs", Context.MODE_PRIVATE)

    fun saveBackupCredentials(email: String, passwordHash: String) {
        prefs.edit().apply {
            putString("backup_email", email)
            putString("backup_password_hash", passwordHash)
            apply()
        }
    }

    fun getBackupEmail(): String? = prefs.getString("backup_email", null)
    fun getBackupPasswordHash(): String? = prefs.getString("backup_password_hash", null)

    private fun notifyDatabaseChanged() {
        prefs.edit().putLong("last_update", System.currentTimeMillis()).apply()
    }

    fun hasPendingChanges(): Boolean {
        val lastUpdate = prefs.getLong("last_update", 0L)
        val lastSync = prefs.getLong("last_sync", 0L)
        return lastUpdate > lastSync
    }

    fun markBackupSynced() {
        prefs.edit().putLong("last_sync", System.currentTimeMillis()).apply()
    }

    fun hasBackupCredentials(): Boolean {
        return !getBackupEmail().isNullOrBlank() && !getBackupPasswordHash().isNullOrBlank()
    }

    suspend fun exportAllData(): String {
        return withContext(Dispatchers.IO) {
            val accounts = dao.getAllAccounts().first()
            val products = dao.getAllProducts().first()
            val purchases = dao.getAllPurchases().first()
            val priceRecords = dao.getAllPriceRecords().first()

            val rootJson = JSONObject().apply {
                put("backup_version", 1)
                put("timestamp", System.currentTimeMillis())
                put("device_id", getBackupEmail() ?: "unknown")

                val dataJson = JSONObject().apply {
                    val accountsArray = JSONArray()
                    accounts.forEach { acc ->
                        accountsArray.put(JSONObject().apply {
                            put("id", acc.id)
                            put("name", acc.name)
                            put("icon", acc.icon)
                        })
                    }
                    put("accounts", accountsArray)

                    val productsArray = JSONArray()
                    products.forEach { prod ->
                        productsArray.put(JSONObject().apply {
                            put("id", prod.id)
                            put("name", prod.name)
                            put("unit", prod.unit)
                            put("accountId", prod.accountId)
                            put("brand", prod.brand)
                        })
                    }
                    put("products", productsArray)

                    val purchasesArray = JSONArray()
                    purchases.forEach { pur ->
                        purchasesArray.put(JSONObject().apply {
                            put("id", pur.id)
                            put("accountId", pur.accountId)
                            put("date", pur.date)
                            put("store", pur.store)
                            put("nickname", pur.nickname)
                            put("isDraft", pur.isDraft)
                            put("invoiceUrl", pur.invoiceUrl)
                        })
                    }
                    put("purchases", purchasesArray)

                    val priceRecordsArray = JSONArray()
                    priceRecords.forEach { pr ->
                        priceRecordsArray.put(JSONObject().apply {
                            put("id", pr.id)
                            put("productId", pr.productId)
                            put("purchaseId", pr.purchaseId)
                            put("unitPrice", pr.unitPrice)
                            put("quantity", pr.quantity)
                        })
                    }
                    put("price_records", priceRecordsArray)
                }
                put("data", dataJson)
            }
            rootJson.toString()
        }
    }

    fun compress(data: String): ByteArray {
        val bos = ByteArrayOutputStream()
        GZIPOutputStream(bos).use { gzip ->
            gzip.write(data.toByteArray(Charsets.UTF_8))
        }
        return bos.toByteArray()
    }

    suspend fun sendBackupToLambda(bytes: ByteArray) {
        withContext(Dispatchers.IO) {
            val email = getBackupEmail() ?: throw Exception("No email configured")
            val auth = getBackupPasswordHash() ?: throw Exception("No password hash configured")

            val url = java.net.URL("https://backup.pagueiquanto.com/backup")
            val conn = url.openConnection() as java.net.HttpURLConnection
            try {
                conn.doOutput = true
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/octet-stream")
                conn.setRequestProperty("Content-Encoding", "gzip")
                conn.setRequestProperty("X-Backup-Email", email)
                conn.setRequestProperty("X-Backup-Auth", auth)
                conn.setConnectTimeout(15000)
                conn.setReadTimeout(15000)

                conn.outputStream.use { os ->
                    os.write(bytes)
                }

                val responseCode = conn.responseCode
                if (responseCode != 200) {
                    throw Exception("Failed to send backup: HTTP $responseCode")
                }
            } finally {
                conn.disconnect()
            }
        }
    }

    suspend fun fetchBackupFromLambda(): String? {
        return withContext(Dispatchers.IO) {
            val email = getBackupEmail() ?: throw Exception("No email configured")
            val auth = getBackupPasswordHash() ?: throw Exception("No password hash configured")

            val url = java.net.URL("https://backup.pagueiquanto.com/restore")
            val conn = url.openConnection() as java.net.HttpURLConnection
            try {
                conn.requestMethod = "POST"
                conn.setRequestProperty("X-Backup-Email", email)
                conn.setRequestProperty("X-Backup-Auth", auth)
                conn.setConnectTimeout(15000)
                conn.setReadTimeout(15000)

                val responseCode = conn.responseCode
                if (responseCode == 404) {
                    return@withContext null
                }
                if (responseCode != 200) {
                    throw Exception("Failed to restore backup: HTTP $responseCode")
                }

                val bytes = conn.inputStream.use { it.readBytes() }
                decompress(bytes)
            } finally {
                conn.disconnect()
            }
        }
    }

    fun decompress(bytes: ByteArray): String {
        val bis = java.io.ByteArrayInputStream(bytes)
        java.util.zip.GZIPInputStream(bis).bufferedReader(Charsets.UTF_8).use { reader ->
            return reader.readText()
        }
    }

    suspend fun restoreAndMergeBackup(jsonString: String) {
        withContext(Dispatchers.IO) {
            val root = JSONObject(jsonString)
            val dataObj = root.getJSONObject("data")

            val accountsArray = dataObj.getJSONArray("accounts")
            val productsArray = dataObj.getJSONArray("products")
            val purchasesArray = dataObj.getJSONArray("purchases")
            val priceRecordsArray = dataObj.getJSONArray("price_records")

            val db = (context.applicationContext as PagueiQuantoApplication).database

            // Executa em transação para consistência
            db.runInTransaction {
                kotlinx.coroutines.runBlocking {
                    val localAccounts = dao.getAllAccounts().first()
                    val localProducts = dao.getAllProducts().first()
                    val localPurchases = dao.getAllPurchases().first()

                    val accountIdMap = mutableMapOf<Long, Long>()
                    val productIdMap = mutableMapOf<Long, Long>()
                    val purchaseIdMap = mutableMapOf<Long, Long>()

                    // 1. Mesclar Accounts
                    for (i in 0 until accountsArray.length()) {
                        val accJson = accountsArray.getJSONObject(i)
                        val backupId = accJson.getLong("id")
                        val name = accJson.getString("name")
                        val icon = accJson.getString("icon")

                        val existing = localAccounts.find { it.name.equals(name, ignoreCase = true) }
                        if (existing != null) {
                            accountIdMap[backupId] = existing.id
                        } else {
                            val newId = dao.insertAccount(com.ajudaqui.pagueiquanto.model.Account(name = name, icon = icon))
                            accountIdMap[backupId] = newId
                        }
                    }

                    // 2. Mesclar Products
                    for (i in 0 until productsArray.length()) {
                        val prodJson = productsArray.getJSONObject(i)
                        val backupId = prodJson.getLong("id")
                        val name = prodJson.getString("name")
                        val unit = prodJson.getString("unit")
                        val backupAccountId = prodJson.getLong("accountId")
                        val brand = if (prodJson.isNull("brand")) null else prodJson.getString("brand")

                        val mappedAccountId = accountIdMap[backupAccountId] ?: continue

                        val existing = localProducts.find { it.name.equals(name, ignoreCase = true) && it.accountId == mappedAccountId }
                        if (existing != null) {
                            productIdMap[backupId] = existing.id
                        } else {
                            val newId = dao.insertProduct(com.ajudaqui.pagueiquanto.model.Product(
                                name = name,
                                unit = unit,
                                accountId = mappedAccountId,
                                brand = brand
                            ))
                            productIdMap[backupId] = newId
                        }
                    }

                    // 3. Mesclar Purchases (Sobrescrever em caso de conflitos)
                    for (i in 0 until purchasesArray.length()) {
                        val purJson = purchasesArray.getJSONObject(i)
                        val backupId = purJson.getLong("id")
                        val backupAccountId = purJson.getLong("accountId")
                        val date = purJson.getLong("date")
                        val store = purJson.getString("store")
                        val nickname = if (purJson.isNull("nickname")) null else purJson.getString("nickname")
                        val isDraft = purJson.getBoolean("isDraft")
                        val invoiceUrl = if (purJson.isNull("invoiceUrl")) null else purJson.getString("invoiceUrl")

                        val mappedAccountId = accountIdMap[backupAccountId] ?: continue

                        val existing = localPurchases.find { it.date == date && it.store.equals(store, ignoreCase = true) && it.accountId == mappedAccountId }
                        if (existing != null) {
                            dao.deletePurchaseById(existing.id)
                        }

                        val newId = dao.insertPurchase(com.ajudaqui.pagueiquanto.model.Purchase(
                            accountId = mappedAccountId,
                            date = date,
                            store = store,
                            nickname = nickname,
                            isDraft = isDraft,
                            invoiceUrl = invoiceUrl
                        ))
                        purchaseIdMap[backupId] = newId
                    }

                    // 4. Inserir PriceRecords correspondentes
                    for (i in 0 until priceRecordsArray.length()) {
                        val prJson = priceRecordsArray.getJSONObject(i)
                        val backupProductId = prJson.getLong("productId")
                        val backupPurchaseId = prJson.getLong("purchaseId")
                        val unitPrice = prJson.getDouble("unitPrice")
                        val quantity = prJson.getDouble("quantity")

                        val mappedProductId = productIdMap[backupProductId] ?: continue
                        val mappedPurchaseId = purchaseIdMap[backupPurchaseId] ?: continue

                        dao.insertRecord(com.ajudaqui.pagueiquanto.model.PriceRecord(
                            productId = mappedProductId,
                            purchaseId = mappedPurchaseId,
                            unitPrice = unitPrice,
                            quantity = quantity
                        ))
                    }
                }
            }
            notifyDatabaseChanged()
        }
    }

    fun hashPassword(password: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(password.toByteArray(Charsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }
    }
}
