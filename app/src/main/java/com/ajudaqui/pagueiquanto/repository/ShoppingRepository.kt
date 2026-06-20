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
        
        // Mapeia purchaseId para a compra correspondente para pegar data, loja, apelido
        val purchaseMap = dbPurchases.associateBy { it.id }

        // Mapeia productId para a lista de registros de preço correspondentes (PriceRecordState)
        val recordsByProduct = dbPriceRecords.groupBy { it.productId }.mapValues { (_, records) ->
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

    suspend fun savePurchaseTransaction(
        accountId: String, 
        store: String, 
        nickname: String?, 
        items: List<com.ajudaqui.pagueiquanto.viewmodel.PurchaseItemState>
    ) {
        val accId = accountId.toLongOrNull() ?: return
        
        withContext(Dispatchers.IO) {
            val dateMillis = System.currentTimeMillis()
            
            // 1. Salva a nova Compra
            val purchaseId = dao.insertPurchase(com.ajudaqui.pagueiquanto.model.Purchase(
                accountId = accId,
                date = dateMillis,
                store = store,
                nickname = nickname
            ))

            // 2. Salva os produtos novos criados e os registros de preço de cada item
            items.forEach { item ->
                var prodId = item.productId.toLongOrNull()
                
                // Se for um item novo adicionado na hora, salva primeiro no banco
                if (prodId == null || item.productId.startsWith("new_")) {
                    prodId = dao.insertProduct(com.ajudaqui.pagueiquanto.model.Product(
                        name = item.productName,
                        unit = item.unit,
                        accountId = accId
                    ))
                }

                // Salva o registro de preço associado à compra
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
}
