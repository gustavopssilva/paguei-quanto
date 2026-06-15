package com.ajudaqui.pagueiquanto.repository

import com.ajudaqui.pagueiquanto.data.ShoppingDao
import com.ajudaqui.pagueiquanto.model.MockAccount
import com.ajudaqui.pagueiquanto.model.MockPriceRecord
import com.ajudaqui.pagueiquanto.model.MockProduct
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Locale

class ShoppingRepository(private val dao: ShoppingDao) {

    // Simulando o Banco de Dados em memória dentro do Service por enquanto
    private val _accountsState = MutableStateFlow<List<MockAccount>>(initialMockData())
    val accounts: StateFlow<List<MockAccount>> = _accountsState.asStateFlow()

    // --- LÓGICA DE NEGÓCIO (BUSINESS LOGIC) ---

    fun getLatestPurchaseGlobal(): Pair<MockAccount, String>? {
        return _accountsState.value
            .filter { it.lastPurchaseDate != null }
            .map { it to it.lastPurchaseDate!! }
            .maxByOrNull { it.second }
    }

    fun getGlobalHistory(): List<Triple<MockAccount, String, Double>> {
        return _accountsState.value.flatMap { acc ->
            acc.products.flatMap { p -> p.history.map { it.date to acc } }
                .distinctBy { it.first + it.second.id }
                .map { (date, account) ->
                    val total = calculatePurchaseTotal(account, date)
                    Triple(account, date, total)
                }
        }.sortedByDescending { it.second }
    }

    fun getGlobalRecentItems(): List<MockProduct> {
        val latest = getLatestPurchaseGlobal()?.second ?: return emptyList()
        return _accountsState.value.flatMap { it.products }
            .filter { it.history.any { h -> h.date == latest } }
    }

    fun getPurchaseItems(date: String, nickname: String?): List<Pair<MockProduct, MockPriceRecord>> {
        val account = _accountsState.value.find { acc -> 
            acc.products.any { p -> p.history.any { h -> h.date == date && h.nickname == nickname } } 
        } ?: return emptyList()
        
        return account.products.flatMap { product ->
            product.history
                .filter { it.date == date && it.nickname == nickname }
                .map { product to it }
        }
    }

    fun calculatePurchaseTotal(account: MockAccount, date: String): Double {
        return account.products.sumOf { p -> 
            p.history.find { it.date == date }?.let { it.unitPrice * it.quantity } ?: 0.0 
        }
    }

    // --- AÇÕES DE PERSISTÊNCIA (TRANSACTIONS) ---

    suspend fun createNewAccount(name: String, icon: String) {
        val newAccount = MockAccount(
            id = "acc_${System.currentTimeMillis()}",
            name = name,
            icon = icon,
            products = emptyList(),
            nextPurchasePrediction = "Sem previsão",
            predictionProgress = 0f
        )
        _accountsState.value = _accountsState.value + newAccount
        // dao.insertAccount(...) // Aqui entraria o banco real
    }

    suspend fun savePurchaseTransaction(
        accountId: String, 
        store: String, 
        nickname: String?, 
        items: List<com.ajudaqui.pagueiquanto.viewmodel.PurchaseItemState>
    ) {
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(java.util.Date())
        
        _accountsState.value = _accountsState.value.map { acc ->
            if (acc.id == accountId) {
                val updatedProducts = acc.products.map { product ->
                    val newItem = items.find { it.productId == product.id }
                    if (newItem != null) {
                        product.copy(history = product.history + MockPriceRecord(
                            id = "rec_${System.currentTimeMillis()}_${product.id}",
                            date = date, store = store, nickname = nickname,
                            unitPrice = newItem.currentPrice.toDoubleOrNull() ?: 0.0,
                            quantity = newItem.actualQty.toDoubleOrNull() ?: 0.0
                        ))
                    } else product
                }.toMutableList()

                // Novos produtos criados na hora
                items.filter { it.productId.startsWith("new_") }.forEach { newItem ->
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

    // --- MOCK DATA GENERATOR ---

    private fun initialMockData() = listOf(
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

    private fun createMockAccount(id: String, name: String, icon: String, hasHistory: Boolean): MockAccount {
        val products = if (!hasHistory) emptyList() else listOf(
            MockProduct(id + "_p1", "Item A de $name", "un", listOf(MockPriceRecord("r1", "2026-06-10", "Loja", 10.0, 1.0))),
            MockProduct(id + "_p2", "Item B de $name", "un", listOf(MockPriceRecord("r2", "2026-06-10", "Loja", 20.0, 2.0)))
        )
        return MockAccount(
            id = id, name = name, icon = icon, products = products,
            nextPurchasePrediction = if (hasHistory) "Esta semana" else "Sem previsão",
            predictionProgress = if (hasHistory) 0.7f else 0f
        )
    }
}
