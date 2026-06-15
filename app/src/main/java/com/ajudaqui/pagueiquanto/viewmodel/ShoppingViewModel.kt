package com.ajudaqui.pagueiquanto.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ajudaqui.pagueiquanto.model.MockAccount
import com.ajudaqui.pagueiquanto.model.MockPriceRecord
import com.ajudaqui.pagueiquanto.model.MockProduct
import com.ajudaqui.pagueiquanto.repository.ShoppingRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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
    
    // --- DADOS MOCKADOS EM MEMÓRIA ---
    private val _accounts = MutableStateFlow<List<MockAccount>>(
        listOf(
            MockAccount(
                id = "feira",
                name = "Feira do mês",
                icon = "cart",
                products = listOf(
                    MockProduct("arroz", "Arroz Branco 5kg", "pct", listOf(
                        MockPriceRecord("a1", "2026-01-08", "Atacadão", 24.9, 1.0, "Compra de início de ano"),
                        MockPriceRecord("a2", "2026-03-12", "Carrefour", 27.5, 1.0, "Compra mensal"),
                        MockPriceRecord("a3", "2026-05-20", "Pão de Açúcar", 29.9, 1.0, "Reposição"),
                        MockPriceRecord("a4", "2026-06-10", "Atacadão", 26.9, 2.0, "Promoção")
                    )),
                    MockProduct("leite", "Leite Integral 1L", "un", listOf(
                        MockPriceRecord("l1", "2026-01-08", "Atacadão", 5.49, 6.0),
                        MockPriceRecord("l2", "2026-03-12", "Carrefour", 4.89, 12.0),
                        MockPriceRecord("l3", "2026-06-10", "Atacadão", 5.99, 6.0)
                    )),
                    MockProduct("cafe", "Café Torrado 500g", "pct", listOf(
                        MockPriceRecord("c1", "2026-03-12", "Carrefour", 18.9, 1.0),
                        MockPriceRecord("c2", "2026-05-20", "Pão de Açúcar", 21.9, 2.0),
                        MockPriceRecord("c3", "2026-06-10", "Atacadão", 19.5, 2.0)
                    )),
                    MockProduct("feijao", "Feijão Carioca 1kg", "pct", listOf(
                        MockPriceRecord("f1", "2026-05-20", "Pão de Açúcar", 8.99, 3.0),
                        MockPriceRecord("f2", "2026-06-10", "Atacadão", 7.49, 3.0)
                    ))
                )
            ),
            MockAccount("material-escolar", "Material escolar", "school", emptyList()),
            MockAccount("aniversario", "Aniversário", "cake", emptyList())
        )
    )
    val accounts = _accounts.asStateFlow()

    private val _selectedAccount = MutableStateFlow<MockAccount?>(null)
    val selectedAccount = _selectedAccount.asStateFlow()

    private val _items = MutableStateFlow<List<PurchaseItemState>>(emptyList())
    val items = _items.asStateFlow()

    fun selectAccount(accountId: String) {
        val account = _accounts.value.find { it.id == accountId }
        _selectedAccount.value = account
        loadItemsForAccount(account)
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

    fun createAccount(name: String, icon: String) {
        val newAccount = MockAccount(
            id = "acc_${System.currentTimeMillis()}",
            name = name,
            icon = icon,
            products = emptyList()
        )
        _accounts.value = _accounts.value + newAccount
    }

    // Retorna o gasto total de todas as listas (mockado para o mês atual)
    fun getTotalSpentThisMonth(): Double {
        return _accounts.value.sumOf { it.lastPurchaseTotal }
    }

    // Retorna os produtos da conta com os dados da última vez que foram comprados
    fun getProductsWithLatestData(): List<MockProduct> {
        return _selectedAccount.value?.products ?: emptyList()
    }

    // Retorna os itens de uma compra específica (agrupados por data/nickname)
    fun getPurchaseItems(date: String, nickname: String?): List<Pair<MockProduct, MockPriceRecord>> {
        val account = _selectedAccount.value ?: return emptyList()
        return account.products.flatMap { product ->
            product.history
                .filter { it.date == date && it.nickname == nickname }
                .map { product to it }
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
        val selected = _selectedAccount.value ?: return
        val currentItems = _items.value.filter { it.actualQty.isNotEmpty() && it.currentPrice.isNotEmpty() }
        
        if (currentItems.isEmpty()) return

        val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(java.util.Date())
        
        val updatedProducts = selected.products.map { product ->
            val newItem = currentItems.find { it.productId == product.id }
            if (newItem != null) {
                product.copy(history = product.history + MockPriceRecord(
                    id = "rec_${System.currentTimeMillis()}_${product.id}",
                    date = date,
                    store = store,
                    unitPrice = newItem.currentPrice.toDoubleOrNull() ?: 0.0,
                    quantity = newItem.actualQty.toDoubleOrNull() ?: 0.0,
                    nickname = nickname
                ))
            } else product
        }.toMutableList()

        // Adiciona produtos que foram criados na hora (novos itens)
        currentItems.filter { it.productId.startsWith("new_") }.forEach { newItem ->
            updatedProducts.add(
                MockProduct(
                    id = newItem.productId,
                    name = newItem.productName,
                    unit = newItem.unit,
                    history = listOf(
                        MockPriceRecord(
                            id = "rec_${System.currentTimeMillis()}",
                            date = date,
                            store = store,
                            unitPrice = newItem.currentPrice.toDoubleOrNull() ?: 0.0,
                            quantity = newItem.actualQty.toDoubleOrNull() ?: 0.0,
                            nickname = nickname
                        )
                    )
                )
            )
        }

        val updatedAccount = selected.copy(products = updatedProducts)
        
        // Atualiza a lista global de contas
        _accounts.value = _accounts.value.map {
            if (it.id == updatedAccount.id) updatedAccount else it
        }
        _selectedAccount.value = updatedAccount
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
