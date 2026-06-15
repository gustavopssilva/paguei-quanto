package com.ajudaqui.pagueiquanto.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.ajudaqui.pagueiquanto.model.MockAccount
import com.ajudaqui.pagueiquanto.model.MockPriceRecord
import com.ajudaqui.pagueiquanto.model.MockProduct
import com.ajudaqui.pagueiquanto.repository.ShoppingRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Locale

data class PurchaseItemState(
    val productId: String,
    val productName: String,
    val unit: String,
    val lastUnitPrice: Double?,
    val lastQuantity: Double?,
    val lastDate: String?,
    val requestedQty: Double,
    val actualQty: String = "",
    val currentPrice: String = "",
    val isEditing: Boolean = false
)

class ShoppingViewModel(private val repository: ShoppingRepository) : ViewModel() {
    
    private val _accounts = MutableStateFlow<List<MockAccount>>(
        listOf(
            createMockAccount("feira", "Feira do mês", "cart", true),
            createMockAccount("limpeza", "Produtos de Limpeza", "cleaning", false),
            createMockAccount("acougue", "Açougue/Carnes", "meat", true),
            createMockAccount("padaria", "Padaria/Café", "bread", false),
            createMockAccount("farmacia", "Farmácia", "health", false),
            createMockAccount("pet", "Pet Shop", "pet", true),
            createMockAccount("bebidas", "Bebidas/Adega", "beer", false),
            createMockAccount("higiene", "Higiene Pessoal", "bath", false),
            createMockAccount("hortifruti", "Hortifruti", "leaf", true),
            createMockAccount("escritorio", "Papelaria/Escritório", "school", false)
        )
    )
    val accounts = _accounts.asStateFlow()

    private val _selectedAccountId = MutableStateFlow<String?>(null)
    val selectedAccountId = _selectedAccountId.asStateFlow()

    private val _items = MutableStateFlow<List<PurchaseItemState>>(emptyList())
    val items = _items.asStateFlow()

    // --- MÉTODOS DE DADOS GLOBAIS ---

    fun getLatestPurchaseGlobal(): Pair<MockAccount, String>? {
        return _accounts.value
            .filter { it.lastPurchaseDate != null }
            .map { it to it.lastPurchaseDate!! }
            .maxByOrNull { it.second }
    }

    fun getGlobalHistory(): List<Triple<MockAccount, String, Double>> {
        return _accounts.value.flatMap { acc ->
            acc.products.flatMap { p -> p.history.map { it.date to acc } }
                .distinctBy { it.first + it.second.id }
                .map { (date, account) ->
                    val total = account.products.sumOf { p -> 
                        p.history.find { it.date == date }?.let { it.unitPrice * it.quantity } ?: 0.0 
                    }
                    Triple(account, date, total)
                }
        }.sortedByDescending { it.second }
    }

    fun getGlobalRecentItems(): List<MockProduct> {
        val latest = getLatestPurchaseGlobal()?.second ?: return emptyList()
        return _accounts.value.flatMap { it.products }
            .filter { it.history.any { h -> h.date == latest } }
    }

    fun getTotalSpentThisMonth(): Double {
        return _accounts.value.sumOf { it.lastPurchaseTotal }
    }

    // --- MÉTODOS DE CONTEXTO ---

    fun selectAccount(accountId: String?) {
        _selectedAccountId.value = accountId
        if (accountId != null) {
            loadItemsForAccount(_accounts.value.find { it.id == accountId })
        }
    }

    private fun loadItemsForAccount(account: MockAccount?) {
        if (account == null) {
            _items.value = emptyList()
            return
        }
        _items.value = account.products.map { product ->
            val last = product.lastEntry
            PurchaseItemState(
                productId = product.id,
                productName = product.name,
                unit = product.unit,
                lastUnitPrice = last?.unitPrice,
                lastQuantity = last?.quantity,
                lastDate = last?.date,
                requestedQty = last?.quantity ?: 1.0,
                actualQty = "",
                currentPrice = ""
            )
        }
    }

    fun getPurchaseItems(date: String, nickname: String?): List<Pair<MockProduct, MockPriceRecord>> {
        val account = _accounts.value.find { acc -> 
            acc.products.any { p -> p.history.any { h -> h.date == date && h.nickname == nickname } } 
        } ?: return emptyList()
        
        return account.products.flatMap { product ->
            product.history
                .filter { it.date == date && it.nickname == nickname }
                .map { product to it }
        }
    }

    fun createAccount(name: String, icon: String) {
        val newAccount = MockAccount(
            id = "acc_${System.currentTimeMillis()}",
            name = name,
            icon = icon,
            products = emptyList(),
            nextPurchasePrediction = "Sem previsão",
            predictionProgress = 0f
        )
        _accounts.value = _accounts.value + newAccount
    }

    fun toggleEdit(productId: String) {
        _items.value = _items.value.map {
            if (it.productId == productId) it.copy(isEditing = !it.isEditing)
            else it.copy(isEditing = false)
        }
    }

    fun updateItem(productId: String, price: String, qty: String, reqQty: String? = null) {
        _items.value = _items.value.map {
            if (it.productId == productId) {
                it.copy(
                    currentPrice = price, 
                    actualQty = qty,
                    requestedQty = reqQty?.toDoubleOrNull() ?: it.requestedQty
                )
            } else it
        }
    }

    fun addNewItem(name: String, unit: String) {
        val newItem = PurchaseItemState(
            productId = "new_${System.currentTimeMillis()}",
            productName = name,
            unit = unit,
            lastUnitPrice = null,
            lastQuantity = null,
            lastDate = null,
            requestedQty = 1.0,
            actualQty = "1",
            currentPrice = "",
            isEditing = true
        )
        _items.value = _items.value + newItem
    }

    fun calculateDelta(item: PurchaseItemState): Double? {
        val current = item.currentPrice.toDoubleOrNull() ?: return null
        val last = item.lastUnitPrice ?: return null
        if (last == 0.0) return null
        return ((current - last) / last) * 100
    }

    fun savePurchase(store: String, nickname: String?) {
        val accountId = _selectedAccountId.value ?: return
        val currentItems = _items.value.filter { it.actualQty.isNotEmpty() && it.currentPrice.isNotEmpty() }
        if (currentItems.isEmpty()) return

        val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(java.util.Date())
        
        _accounts.value = _accounts.value.map { acc ->
            if (acc.id == accountId) {
                val updatedProducts = acc.products.map { product ->
                    val newItem = currentItems.find { it.productId == product.id }
                    if (newItem != null) {
                        product.copy(history = product.history + MockPriceRecord(
                            id = "rec_${System.currentTimeMillis()}_${product.id}",
                            date = date, store = store, nickname = nickname,
                            unitPrice = newItem.currentPrice.toDoubleOrNull() ?: 0.0,
                            quantity = newItem.actualQty.toDoubleOrNull() ?: 0.0
                        ))
                    } else product
                }.toMutableList()

                currentItems.filter { it.productId.startsWith("new_") }.forEach { newItem ->
                    updatedProducts.add(MockProduct(
                        id = newItem.productId, name = newItem.productName, unit = newItem.unit,
                        history = listOf(MockPriceRecord(
                            id = "rec_${System.currentTimeMillis()}",
                            date = date, store = store, nickname = nickname,
                            unitPrice = newItem.currentPrice.toDoubleOrNull() ?: 0.0,
                            quantity = newItem.actualQty.toDoubleOrNull() ?: 0.0
                        ))
                    ))
                }
                acc.copy(products = updatedProducts)
            } else acc
        }
    }

    private fun createMockAccount(id: String, name: String, icon: String, hasHistory: Boolean): MockAccount {
        val products = if (!hasHistory) emptyList() else listOf(
            MockProduct(id + "_p1", "Item A de $name", "un", listOf(MockPriceRecord("r1", "2026-06-10", "Loja", 10.0, 1.0))),
            MockProduct(id + "_p2", "Item B de $name", "un", listOf(MockPriceRecord("r2", "2026-06-10", "Loja", 20.0, 2.0)))
        )
        return MockAccount(
            id = id, 
            name = name, 
            icon = icon, 
            products = products,
            nextPurchasePrediction = if (hasHistory) "Esta semana" else "Sem previsão",
            predictionProgress = if (hasHistory) 0.7f else 0f
        )
    }
}

class ShoppingViewModelFactory(private val repository: ShoppingRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ShoppingViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ShoppingViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
