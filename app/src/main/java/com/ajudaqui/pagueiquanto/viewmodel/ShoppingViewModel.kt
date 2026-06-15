package com.ajudaqui.pagueiquanto.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ajudaqui.pagueiquanto.model.MockAccount
import com.ajudaqui.pagueiquanto.model.MockPriceRecord
import com.ajudaqui.pagueiquanto.model.MockProduct
import com.ajudaqui.pagueiquanto.repository.ShoppingRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

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
    
    // O ViewModel agora apenas observa os dados que vêm do Repository
    val accounts: StateFlow<List<MockAccount>> = repository.accounts

    private val _selectedAccountId = MutableStateFlow<String?>(null)
    val selectedAccountId = _selectedAccountId.asStateFlow()

    private val _items = MutableStateFlow<List<PurchaseItemState>>(emptyList())
    val items = _items.asStateFlow()

    // --- REPASSANDO MÉTODOS DO REPOSITORY (CLEAN CONTROLLER) ---

    fun getLatestPurchaseGlobal() = repository.getLatestPurchaseGlobal()
    fun getGlobalHistory() = repository.getGlobalHistory()
    fun getGlobalRecentItems() = repository.getGlobalRecentItems()
    fun getPurchaseItems(date: String, nickname: String?) = repository.getPurchaseItems(date, nickname)
    fun getTotalSpentThisMonth() = accounts.value.sumOf { it.lastPurchaseTotal }

    // --- AÇÕES DO USUÁRIO ---

    fun selectAccount(accountId: String?) {
        _selectedAccountId.value = accountId
        if (accountId != null) {
            val account = accounts.value.find { it.id == accountId }
            loadItemsForAccount(account)
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
                productId = product.id, productName = product.name, unit = product.unit,
                lastUnitPrice = last?.unitPrice, lastQuantity = last?.quantity,
                lastDate = last?.date, requestedQty = last?.quantity ?: 1.0
            )
        }
    }

    fun createAccount(name: String, icon: String) {
        viewModelScope.launch {
            repository.createNewAccount(name, icon)
        }
    }

    fun savePurchase(store: String, nickname: String?) {
        val accountId = _selectedAccountId.value ?: return
        viewModelScope.launch {
            repository.savePurchaseTransaction(accountId, store, nickname, _items.value)
            selectAccount(accountId) // Refresh nos itens após salvar
        }
    }

    // --- LÓGICA DE ESTADO TEMPORÁRIO (STILL IN VIEWMODEL) ---

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
            productName = name, unit = unit,
            lastUnitPrice = null, lastQuantity = null, lastDate = null,
            requestedQty = 1.0, actualQty = "1", currentPrice = "", isEditing = true
        )
        _items.value = _items.value + newItem
    }

    fun calculateDelta(item: PurchaseItemState): Double? {
        val current = item.currentPrice.toDoubleOrNull() ?: return null
        val last = item.lastUnitPrice ?: return null
        if (last == 0.0) return null
        return ((current - last) / last) * 100
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
