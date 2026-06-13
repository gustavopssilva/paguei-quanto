package com.ajudaqui.pagueiquanto.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ajudaqui.pagueiquanto.repository.ShoppingRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PurchaseItemState(
    val productId: Long,
    val productName: String,
    val unit: String,
    val lastUnitPrice: Double?,
    val lastQuantity: Double?,
    val requestedQty: Double,
    val actualQty: String = "",
    val currentPrice: String = "",
    val isEditing: Boolean = false
)

class ShoppingViewModel(private val repository: ShoppingRepository) : ViewModel() {
    private val _items = MutableStateFlow<List<PurchaseItemState>>(emptyList())
    val items = _items.asStateFlow()

    fun loadProducts() {
        viewModelScope.launch {
            repository.allProducts.collect { products ->
                _items.value = products.map { product ->
                    val last = repository.getLastEntry(product.id)
                    PurchaseItemState(
                        productId = product.id,
                        productName = product.name,
                        unit = product.unit,
                        lastUnitPrice = last?.unitPrice,
                        lastQuantity = last?.quantity,
                        requestedQty = last?.quantity ?: 1.0
                    )
                }
            }
        }
    }

    fun toggleEdit(productId: Long) {
        _items.value = _items.value.map {
            if (it.productId == productId) it.copy(isEditing = !it.isEditing)
            else it.copy(isEditing = false)
        }
    }

    fun updateItem(productId: Long, price: String, qty: String) {
        _items.value = _items.value.map {
            if (it.productId == productId) it.copy(currentPrice = price, actualQty = qty)
            else it
        }
    }

    fun calculateDelta(item: PurchaseItemState): Double? {
        val current = item.currentPrice.toDoubleOrNull() ?: return null
        val last = item.lastUnitPrice ?: return null
        if (last == 0.0) return null
        return ((current - last) / last) * 100
    }

    fun savePurchase(store: String) {
        viewModelScope.launch {
            _items.value.forEach { item ->
                val price = item.currentPrice.toDoubleOrNull()
                val qty = item.actualQty.toDoubleOrNull()
                if (price != null && qty != null && qty > 0) {
                    // In a real app, we'd use a more robust saving logic
                    // for existing products vs new products
                }
            }
        }
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
