package com.ajudaqui.pagueiquanto.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ajudaqui.pagueiquanto.model.AccountState
import com.ajudaqui.pagueiquanto.model.PriceRecordState
import com.ajudaqui.pagueiquanto.model.ProductState
import com.ajudaqui.pagueiquanto.repository.ShoppingRepository
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.ajudaqui.service.FiscalParseService
import com.ajudaqui.model.UrlInput
import java.net.UnknownHostException
import java.net.SocketTimeoutException
import java.net.SocketException
import java.io.IOException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class PurchaseItemState(
    val productId: String,
    val productName: String,
    val unit: String,
    val brand: String = "",
    val lastUnitPrice: Double?,
    val lastQuantity: Double?,
    val lastDate: String?,
    val requestedQty: Double,
    val actualQty: String = "",
    val currentPrice: String = "",
    val isEditing: Boolean = false
)

@OptIn(FlowPreview::class)
class ShoppingViewModel(private val repository: ShoppingRepository) : ViewModel() {

    // O ViewModel agora apenas observa os dados que vêm do Repository
    val accounts: StateFlow<List<AccountState>> = repository.accounts

    private val _selectedAccountId = MutableStateFlow<String?>(null)
    val selectedAccountId = _selectedAccountId.asStateFlow()

    private val _items = MutableStateFlow<List<PurchaseItemState>>(emptyList())
    val items = _items.asStateFlow()

    // Salvamento de rascunho com debounce: cada edição apenas sinaliza; o rascunho é
    // persistido ~700ms depois que o usuário para de digitar, evitando gravações a cada tecla.
    private val draftSaveSignal = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val draftMutex = Mutex()

    init {
        viewModelScope.launch {
            draftSaveSignal.debounce(700).collect { persistDraft() }
        }
    }

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

    private fun loadItemsForAccount(account: AccountState?) {
        if (account == null) {
            _items.value = emptyList()
            return
        }
        viewModelScope.launch {
            val draft = repository.getDraftPurchase(account.id)
            if (draft != null) {
                val draftRecords = repository.getDraftPriceRecords(draft.id)
                val recordMap = draftRecords.associateBy { it.productId }
                
                val draftItems = account.products.map { product ->
                    val last = product.lastEntry
                    val draftRec = recordMap[product.id.toLongOrNull() ?: 0L]
                    PurchaseItemState(
                        productId = product.id,
                        productName = product.name,
                        unit = product.unit,
                        brand = draftRec?.let { product.brand ?: "" } ?: product.brand ?: "",
                        lastUnitPrice = last?.unitPrice,
                        lastQuantity = last?.quantity,
                        lastDate = last?.date,
                        requestedQty = draftRec?.quantity ?: last?.quantity ?: 1.0,
                        actualQty = draftRec?.let { it.quantity.toString() } ?: "",
                        currentPrice = draftRec?.let { if (it.unitPrice > 0.0) it.unitPrice.toString() else "" } ?: "",
                        isEditing = false
                    )
                }

                val existingProductIds = account.products.map { it.id.toLongOrNull() ?: 0L }.toSet()
                val extraItems = draftRecords.filter { !existingProductIds.contains(it.productId) }.mapNotNull { record ->
                    val dbProd = repository.getProductById(record.productId) ?: return@mapNotNull null
                    PurchaseItemState(
                        productId = dbProd.id.toString(),
                        productName = dbProd.name,
                        unit = dbProd.unit,
                        brand = dbProd.brand ?: "",
                        lastUnitPrice = null,
                        lastQuantity = null,
                        lastDate = null,
                        requestedQty = record.quantity,
                        actualQty = record.quantity.toString(),
                        currentPrice = if (record.unitPrice > 0.0) record.unitPrice.toString() else "",
                        isEditing = false
                    )
                }

                _items.value = draftItems + extraItems
            } else {
                _items.value = account.products.map { product ->
                    val last = product.lastEntry
                    PurchaseItemState(
                        productId = product.id, productName = product.name, unit = product.unit,
                        brand = product.brand ?: "",
                        lastUnitPrice = last?.unitPrice, lastQuantity = last?.quantity,
                        lastDate = last?.date, requestedQty = last?.quantity ?: 1.0
                    )
                }
            }
        }
    }

    fun createAccount(name: String, icon: String) {
        viewModelScope.launch {
            repository.createNewAccount(name, icon)
        }
    }

    fun savePurchase(store: String, nickname: String?, invoiceUrl: String? = null) {
        val accountId = _selectedAccountId.value ?: return
        viewModelScope.launch {
            repository.savePurchaseTransaction(accountId, store, nickname, _items.value, invoiceUrl)
            selectAccount(accountId) // Refresh nos itens após salvar
        }
    }

    fun deleteAccount(accountId: String) {
        viewModelScope.launch { repository.deleteAccount(accountId) }
    }

    fun deleteProduct(productId: String) {
        viewModelScope.launch { repository.deleteProduct(productId) }
    }

    fun deletePriceRecord(recordId: String) {
        viewModelScope.launch { repository.deletePriceRecord(recordId) }
    }

    fun deletePurchaseByDate(date: String, nickname: String?) {
        viewModelScope.launch { repository.deletePurchaseByDate(date, nickname) }
    }

    fun updatePriceRecord(recordId: String, unitPrice: Double, quantity: Double) {
        viewModelScope.launch { repository.updatePriceRecord(recordId, unitPrice, quantity) }
    }

    fun addItemToExistingPurchase(date: String, nickname: String?, productName: String, unit: String, price: Double, quantity: Double, brand: String? = null) {
        viewModelScope.launch {
            repository.addItemToExistingPurchase(date, nickname, productName, unit, price, quantity, brand)
        }
    }

    /** Sinaliza que o rascunho mudou; a gravação real acontece com debounce. */
    private fun saveDraftToDb() {
        if (_selectedAccountId.value == null) return
        draftSaveSignal.tryEmit(Unit)
    }

    /** Persiste imediatamente o rascunho. Chamar ao sair da tela (onBack) para não perder edições recentes. */
    fun flushDraft() {
        if (_selectedAccountId.value == null) return
        viewModelScope.launch { persistDraft() }
    }

    private suspend fun persistDraft() {
        val accountId = _selectedAccountId.value ?: return
        // Mutex serializa os saves: garante que o id real de um item novo seja aplicado ao estado
        // antes do próximo save, evitando recriar o mesmo produto e gerar duplicatas.
        draftMutex.withLock {
            val resolvedIds = repository.saveDraftTransaction(accountId, _items.value)
            if (resolvedIds.isNotEmpty()) {
                _items.value = _items.value.map { item ->
                    resolvedIds[item.productId]?.let { item.copy(productId = it) } ?: item
                }
            }
        }
    }

    // --- LÓGICA DE ESTADO TEMPORÁRIO (STILL IN VIEWMODEL) ---

    fun toggleEdit(productId: String) {
        _items.value = _items.value.map {
            if (it.productId == productId) it.copy(isEditing = !it.isEditing)
            else it.copy(isEditing = false)
        }
    }

    fun updateItem(productId: String, price: String, qty: String, reqQty: String? = null, brand: String? = null) {
        _items.value = _items.value.map {
            if (it.productId == productId) {
                it.copy(
                    currentPrice = price, 
                    actualQty = qty,
                    requestedQty = reqQty?.replace(",", ".")?.toDoubleOrNull() ?: it.requestedQty,
                    brand = brand ?: it.brand
                )
            } else it
        }
        saveDraftToDb()
    }

    fun addNewItem(name: String, unit: String, brand: String) {
        val newItem = PurchaseItemState(
            productId = "new_${System.currentTimeMillis()}",
            productName = name, unit = unit, brand = brand,
            lastUnitPrice = null, lastQuantity = null, lastDate = null,
            requestedQty = 1.0, actualQty = "1", currentPrice = "", isEditing = true
        )
        _items.value = _items.value + newItem
        saveDraftToDb()
    }

    fun calculateDelta(item: PurchaseItemState): Double? {
        val current = item.currentPrice.replace(",", ".").toDoubleOrNull() ?: return null
        val last = item.lastUnitPrice ?: return null
        if (last == 0.0) return null
        return ((current - last) / last) * 100
    }

    /**
     * Chama o serviço local da ajudaqui-fiscal (test-app na porta 8080) com a URL
     * da SEFAZ lida pelo QR Code, parseia o JSON retornado e adiciona os itens
     * da nota fiscal à lista de itens da compra em andamento.
     *
     * O preço unitário é calculado como totalValue/quantity quando unitValue for "0",
     * pois a lib frequentemente retorna zero nesse campo.
     *
     * @param invoiceUrl URL da SEFAZ obtida pelo QR Code
     * @param onResult   Callback com (sucesso: Boolean, mensagem: String)
     */
    fun importFromFiscalInvoice(invoiceUrl: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    FiscalParseService().parse(UrlInput(invoiceUrl))
                }

                if (!result.isSuccess) {
                    onResult(false, result.error ?: "Falha ao processar a nota")
                    return@launch
                }

                val doc   = result.data
                val items = doc?.items
                if (items.isNullOrEmpty()) {
                    onResult(false, "Nenhum item encontrado na nota")
                    return@launch
                }

                val businessName = doc.issuer?.businessName?.trim() ?: ""

                val importedItems = items.mapIndexed { i, item ->
                    val description = item.description?.trim() ?: "Item $i"
                    val unit        = item.unit?.ifBlank { "un" } ?: "un"
                    val qty         = item.quantity?.replace(",", ".")?.toDoubleOrNull() ?: 1.0
                    val unitVal     = item.unitValue?.replace(",", ".")?.toDoubleOrNull() ?: 0.0
                    val totalVal    = item.totalValue?.replace(",", ".")?.toDoubleOrNull() ?: 0.0

                    // Calcular preço unitário: usar unitValue se válido, senão dividir totalValue pela qty
                    val unitPrice = when {
                        unitVal > 0.0 -> unitVal
                        qty > 0.0     -> totalVal / qty
                        else          -> totalVal
                    }

                    PurchaseItemState(
                        productId     = "fiscal_${System.currentTimeMillis()}_$i",
                        productName   = description,
                        unit          = unit,
                        brand         = "",
                        lastUnitPrice = null,
                        lastQuantity  = null,
                        lastDate      = null,
                        requestedQty  = qty,
                        actualQty     = qty.toBigDecimal().stripTrailingZeros().toPlainString(),
                        currentPrice  = String.format("%.2f", unitPrice).replace(",", "."),
                        isEditing     = false
                    )
                }

                // Adicionar itens importados à lista existente (preserva itens já lançados manualmente)
                _items.value = _items.value + importedItems
                saveDraftToDb()

                val storeName = businessName.ifBlank { "Nota Fiscal" }
                val warnings  = result.warnings?.joinToString("; ") ?: ""
                val msg = "✅ $storeName\n${importedItems.size} item(ns) importado(s)"
                onResult(true, if (warnings.isNotBlank()) "$msg\n⚠️ $warnings" else msg)

            } catch (e: UnknownHostException) {
                onResult(false, "Sem conexão com a internet.\nVerifique sua rede e tente novamente.")
            } catch (e: SocketException) {
                onResult(false, "Sem conexão com a internet.\nVerifique sua rede e tente novamente.")
            } catch (e: SocketTimeoutException) {
                onResult(false, "A consulta à SEFAZ demorou demais.\nVerifique sua conexão e tente novamente.")
            } catch (e: IOException) {
                onResult(false, "Falha de rede ao consultar a nota.\nVerifique sua conexão e tente novamente.")
            } catch (e: Exception) {
                onResult(false, "Não foi possível processar a nota.\n${e.message ?: "Erro desconhecido"}")
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
