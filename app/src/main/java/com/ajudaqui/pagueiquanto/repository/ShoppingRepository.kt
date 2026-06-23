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

class ShoppingRepository(private val dao: ShoppingDao) {

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
    }

    suspend fun deleteAccount(accountId: String) {
        withContext(Dispatchers.IO) {
            dao.deleteAccountById(accountId.toLongOrNull() ?: return@withContext)
        }
    }

    suspend fun deleteProduct(productId: String) {
        withContext(Dispatchers.IO) {
            dao.deleteProductById(productId.toLongOrNull() ?: return@withContext)
        }
    }

    suspend fun deletePriceRecord(recordId: String) {
        withContext(Dispatchers.IO) {
            dao.deletePriceRecordById(recordId.toLongOrNull() ?: return@withContext)
        }
    }

    suspend fun deletePurchaseByDate(date: String, nickname: String?) {
        val recordsToDelete = getPurchaseItems(date, nickname).map { it.second }
        withContext(Dispatchers.IO) {
            recordsToDelete.forEach {
                dao.deletePriceRecordById(it.id.toLongOrNull() ?: return@forEach)
            }
        }
    }

    suspend fun updatePriceRecord(recordId: String, unitPrice: Double, quantity: Double) {
        val recId = recordId.toLongOrNull() ?: return
        withContext(Dispatchers.IO) {
            dao.updatePriceRecord(recId, unitPrice, quantity)
        }
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
    }
}
