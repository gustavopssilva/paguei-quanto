package com.ajudaqui.pagueiquanto.ui

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ajudaqui.pagueiquanto.model.MockAccount
import com.ajudaqui.pagueiquanto.model.MockProduct
import com.ajudaqui.pagueiquanto.model.MockPriceRecord
import com.ajudaqui.pagueiquanto.ui.theme.Slate100
import com.ajudaqui.pagueiquanto.ui.theme.Slate500
import com.ajudaqui.pagueiquanto.ui.theme.Slate900
import com.ajudaqui.pagueiquanto.viewmodel.PurchaseItemState
import com.ajudaqui.pagueiquanto.viewmodel.ShoppingViewModel
import java.text.SimpleDateFormat
import java.util.Locale

// --- COMPONENTES BASE ---

fun formatDate(dateStr: String?): String {
    if (dateStr == null) return "--/--"
    return try {
        val parser = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val formatter = SimpleDateFormat("dd/MM/yy", Locale.getDefault())
        val date = parser.parse(dateStr)
        formatter.format(date!!)
    } catch (e: Exception) {
        dateStr ?: "--/--"
    }
}

@Composable
fun PagueiQuantoTopBar(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onBack != null) {
                    IconButton(onClick = onBack, modifier = Modifier.size(40.dp).clip(CircleShape)) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Voltar")
                    }
                    Spacer(Modifier.width(8.dp))
                } else {
                    Spacer(Modifier.width(8.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Row(verticalAlignment = Alignment.CenterVertically) { actions() }
            }
            Divider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
        }
    }
}

@Composable
fun PriceVariationBadge(delta: Double?, size: String = "md") {
    if (delta == null) {
        Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(100.dp)) {
            Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = if (size == "sm") 2.dp else 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Remove, contentDescription = null, modifier = Modifier.size(if (size == "sm") 10.dp else 12.dp))
                Spacer(Modifier.width(4.dp))
                Text("Novo", style = if (size == "sm") MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp) else MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium)
            }
        }
        return
    }
    val isUp = delta > 0.1
    val isDown = delta < -0.1
    val color = when { isUp -> MaterialTheme.colorScheme.error; isDown -> Color(0xFF10B981); else -> MaterialTheme.colorScheme.onSurfaceVariant }
    Surface(color = color.copy(alpha = 0.1f), shape = RoundedCornerShape(100.dp)) {
        Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = if (size == "sm") 2.dp else 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = when { isUp -> Icons.Default.ArrowUpward; isDown -> Icons.Default.ArrowDownward; else -> Icons.Default.Remove }, contentDescription = null, modifier = Modifier.size(if (size == "sm") 10.dp else 12.dp), tint = color)
            Spacer(Modifier.width(4.dp))
            Text("${if (isUp) "+" else ""}${String.format("%.1f", delta).replace(".", ",")}%", style = if (size == "sm") MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp) else MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold)
        }
    }
}

// --- TELAS ---

@Composable
fun HomeScreen(
    accounts: List<MockAccount>,
    latestPurchase: Pair<MockAccount, String>?,
    onOpenAccount: (String) -> Unit,
    onOpenPurchase: (String, String?) -> Unit,
    onAddAccount: (String, String) -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { PagueiQuantoTopBar(title = "Meus Gastos", subtitle = "Lembretes de compra", actions = { IconButton(onClick = {}) { Icon(Icons.Outlined.Notifications, null) } }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { showAddDialog = true }, containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary, shape = RoundedCornerShape(100.dp), icon = { Icon(Icons.Default.Add, null) }, text = { Text("Nova lista", fontWeight = FontWeight.Bold) })
        },
        floatingActionButtonPosition = FabPosition.Center
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize().background(MaterialTheme.colorScheme.background)
        ) {
            // Hero Card Fixo no topo
            latestPurchase?.let { (acc, date) -> 
                HeroCard(account = acc, date = date, onOpen = { onOpenAccount(acc.id) }) 
            }

            Text(
                "MINHAS LISTAS", 
                style = MaterialTheme.typography.labelLarge, 
                fontWeight = FontWeight.Bold, 
                color = Slate500, 
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )

            // Lista com scroll independente (LazyColumn com weight(1f))
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                items(accounts) { account -> 
                    ReminderListCard(account = account, onClick = { onOpenAccount(account.id) }) 
                }
            }
        }
    }

    if (showAddDialog) {
        var name by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { showAddDialog = false }, title = { Text("Nova Lista") }, text = { OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nome da Lista") }, modifier = Modifier.fillMaxWidth()) }, confirmButton = { Button(onClick = { if (name.isNotBlank()) { onAddAccount(name, "cart"); showAddDialog = false } }) { Text("Criar") } })
    }
}

@Composable
fun HeroCard(account: MockAccount, date: String, onOpen: () -> Unit) {
    Card(modifier = Modifier.padding(16.dp).fillMaxWidth(), shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)) {
        Row(modifier = Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Última compra registrada", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.8f))
                Text(account.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, color = Color.White)
                Text("${formatDate(date)} • R$ ${String.format("%.2f", account.lastPurchaseTotal).replace(".", ",")}", style = MaterialTheme.typography.bodyMedium, color = Color.White)
                Spacer(Modifier.height(16.dp))
                Button(onClick = onOpen, colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = MaterialTheme.colorScheme.primary), shape = RoundedCornerShape(12.dp)) { Text("Ver detalhes", fontWeight = FontWeight.Bold) }
            }
            Box(modifier = Modifier.size(80.dp).background(Color.White.copy(alpha = 0.2f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.ShoppingBasket, null, tint = Color.White, modifier = Modifier.size(40.dp))
            }
        }
    }
}

@Composable
fun ReminderListCard(account: MockAccount, onClick: () -> Unit) {
    Card(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth().clickable { onClick() }, shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, Slate100)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(modifier = Modifier.size(40.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), shape = RoundedCornerShape(10.dp)) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.ShoppingCart, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)) }
                }
                Spacer(Modifier.width(12.dp)); Column(modifier = Modifier.weight(1f)) { Text(account.name, fontWeight = FontWeight.Bold); Text(if (account.lastPurchaseDate != null) "Última: ${formatDate(account.lastPurchaseDate)}" else "Sem histórico", style = MaterialTheme.typography.bodySmall, color = Slate500) }
                Surface(color = (if (account.lastPurchaseDate == null) Slate500 else Color(0xFFF59E0B)).copy(alpha = 0.1f), shape = RoundedCornerShape(100.dp)) {
                    Text(if (account.lastPurchaseDate == null) "Sem histórico" else "Em breve", modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, color = if (account.lastPurchaseDate == null) Slate500 else Color(0xFFF59E0B), fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(12.dp)); Divider(thickness = 0.5.dp, color = Slate100); Spacer(Modifier.height(12.dp))
            val count = if (account.products.isEmpty()) 0 else 3
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(if (account.nextPurchasePrediction != null) "Previsão: ${account.nextPurchasePrediction}" else "Adicione itens para lembretes", style = MaterialTheme.typography.bodySmall, color = if (account.nextPurchasePrediction != null) Slate900 else Slate500, fontWeight = FontWeight.Bold)
                    Text(if (count > 0) "$count itens costumam acabar este período" else "Nenhum item previsto agora", style = MaterialTheme.typography.labelSmall, color = Slate500)
                }
            }
            if (account.predictionProgress > 0) { Spacer(Modifier.height(8.dp)); LinearProgressIndicator(progress = account.predictionProgress, modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape), color = if (account.predictionProgress > 0.8f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, trackColor = Slate100) }
        }
    }
}

@Composable
fun LatestItemRow(product: MockProduct) {
    val last = product.lastEntry
    Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        val emoji = when {
            product.name.contains("Café", true) -> "☕"
            product.name.contains("Leite", true) -> "🥛"
            product.name.contains("Arroz", true) -> "🍚"
            product.name.contains("Pão", true) -> "🍞"
            product.name.contains("Carne", true) -> "🥩"
            product.name.contains("Cerveja", true) -> "🍺"
            else -> "📦"
        }
        Text(emoji, fontSize = 20.sp); Spacer(Modifier.width(12.dp))
        Text(product.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        Text("....................", color = Slate100, maxLines = 1)
        Text("R$ ${String.format("%.2f", last?.unitPrice ?: 0.0).replace(".", ",")}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = Slate900)
    }
}

@Composable
fun CategoryHistoryScreen(account: MockAccount, onNewPurchase: () -> Unit, onOpenPurchase: (String, String?) -> Unit) {
    Scaffold(topBar = { PagueiQuantoTopBar(title = account.name, subtitle = "Histórico de compras") }) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            // Banner de Previsão na Categoria
            Surface(
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.EventRepeat, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Previsão de Reposição", style = MaterialTheme.typography.labelSmall, color = Slate500, fontWeight = FontWeight.Bold)
                        Text(account.nextPurchasePrediction ?: "Sem previsão disponível", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                        if (account.predictionProgress > 0) {
                            Spacer(Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = account.predictionProgress,
                                modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = Color.White
                            )
                        }
                    }
                }
            }

            Button(onClick = onNewPurchase, modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(56.dp), shape = RoundedCornerShape(16.dp)) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("Iniciar nova compra", fontWeight = FontWeight.Bold) }
            
            val history = account.products.flatMap { p -> p.history.map { it to p.name } }.groupBy { it.first.date + (it.first.nickname ?: "") }.toList().sortedByDescending { it.second.first().first.dateMillis }
            
            if (history.isEmpty()) EmptyHistoryPlaceholder()
            else LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp), 
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(history) { (_, records) ->
                    val first = records.first().first
                    PurchaseHistoryItem(date = first.date, nickname = first.nickname, total = records.sumOf { it.first.unitPrice * it.first.quantity }, qty = records.size, onClick = { onOpenPurchase(first.date, first.nickname) })
                }
            }
        }
    }
}

@Composable
fun CategoryProductsScreen(account: MockAccount, onOpenProduct: (String) -> Unit) {
    Scaffold(topBar = { PagueiQuantoTopBar(title = account.name, subtitle = "Itens cadastrados") }) { padding ->
        if (account.products.isEmpty()) EmptyProductsPlaceholder()
        else LazyColumn(modifier = Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(account.products) { product -> ProductCard(product = product, onClick = { onOpenProduct(product.id) }) }
        }
    }
}

@Composable
fun AllListsScreen(accounts: List<MockAccount>, onOpenAccount: (String) -> Unit) {
    Scaffold(topBar = { PagueiQuantoTopBar(title = "Minhas Listas", subtitle = "Categorias de compra") }) { padding ->
        LazyColumn(modifier = Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(accounts) { account -> ReminderListCard(account = account, onClick = { onOpenAccount(account.id) }) }
        }
    }
}

@Composable
fun GlobalItemsScreen(items: List<MockProduct>) {
    Scaffold(topBar = { PagueiQuantoTopBar(title = "Últimos Itens", subtitle = "Comprados recentemente") }) { padding ->
        LazyColumn(modifier = Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            items(items) { product -> LatestItemRow(product = product) }
        }
    }
}

@Composable
fun GlobalHistoryScreen(history: List<Triple<MockAccount, String, Double>>, onOpenPurchase: (String, String?) -> Unit) {
    Scaffold(topBar = { PagueiQuantoTopBar(title = "Histórico Global", subtitle = "Todas as compras") }) { padding ->
        LazyColumn(modifier = Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(history) { (acc, date, total) -> PurchaseHistoryItem(date = date, nickname = acc.name, total = total, qty = 0, onClick = { onOpenPurchase(date, null) }) }
        }
    }
}

@Composable
fun ProfileScreen() {
    Scaffold(topBar = { PagueiQuantoTopBar(title = "Meu Perfil") }) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp)) {
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, Slate100)) {
                Row(modifier = Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(64.dp).background(MaterialTheme.colorScheme.primary, CircleShape), contentAlignment = Alignment.Center) { Text("U", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 24.sp) }
                    Spacer(Modifier.width(16.dp))
                    Column { Text("Usuário de Teste", fontWeight = FontWeight.Bold, fontSize = 18.sp); Text("usuario@email.com", color = Slate500) }
                }
            }
        }
    }
}

@Composable
fun PurchaseHistoryItem(date: String, nickname: String?, total: Double, qty: Int, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable { onClick() }, colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column(modifier = Modifier.weight(1f)) {
                Text(formatDate(date), fontWeight = FontWeight.Black, fontSize = 16.sp)
                if (nickname != null) Text(nickname, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (qty > 0) Text("$qty itens comprados", style = MaterialTheme.typography.labelSmall, color = Slate500)
            }
            Text("R$ ${String.format("%.2f", total).replace(".", ",")}", fontWeight = FontWeight.Medium, color = Slate900, fontSize = 16.sp)
            Spacer(Modifier.width(8.dp)); Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
fun PurchaseDetailScreen(date: String, nickname: String?, items: List<Pair<MockProduct, MockPriceRecord>>, onBack: () -> Unit) {
    Scaffold(topBar = { PagueiQuantoTopBar(title = nickname ?: formatDate(date), subtitle = formatDate(date), onBack = onBack) }) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            val total = items.sumOf { it.second.unitPrice * it.second.quantity }
            Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)) {
                Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Total da Compra", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Text("R$ ${String.format("%.2f", total).replace(".", ",")}", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                }
            }
            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(items) { (product, record) ->
                    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(Modifier.weight(1f)) { Text(product.name, fontWeight = FontWeight.Bold); Text("${record.quantity}${product.unit}", style = MaterialTheme.typography.bodySmall, color = Slate500) }
                            Text("R$ ${String.format("%.2f", record.unitPrice).replace(".", ",")}", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ProductHistoryScreen(product: MockProduct, onBack: () -> Unit) {
    Scaffold(topBar = { PagueiQuantoTopBar(title = product.name, subtitle = "Histórico de Preços", onBack = onBack) }) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)) {
                Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Preço Médio", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    val avg = product.history.map { it.unitPrice }.average()
                    Text("R$ ${String.format("%.2f", avg).replace(".", ",")}", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                }
            }
            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(product.history.sortedByDescending { it.dateMillis }) { record ->
                    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(Modifier.weight(1f)) { Text(formatDate(record.date), fontWeight = FontWeight.Bold); record.nickname?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary) }; Text(record.store, style = MaterialTheme.typography.bodySmall, color = Slate500) }
                            Column(horizontalAlignment = Alignment.End) { Text("R$ ${String.format("%.2f", record.unitPrice).replace(".", ",")}", fontWeight = FontWeight.Bold, fontSize = 16.sp); Text("${record.quantity}${product.unit}", style = MaterialTheme.typography.labelSmall, color = Slate500) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NewPurchaseScreen(viewModel: ShoppingViewModel, onBack: () -> Unit) {
    val items by viewModel.items.collectAsState()
    val total = items.sumOf { (it.actualQty.toDoubleOrNull() ?: 0.0) * (it.currentPrice.toDoubleOrNull() ?: 0.0) }
    var showAddItemDialog by remember { mutableStateOf(false) }
    var nickname by remember { mutableStateOf("") }
    Scaffold(topBar = { PagueiQuantoTopBar(title = "Nova Compra", onBack = onBack, actions = { IconButton(onClick = { showAddItemDialog = true }) { Icon(Icons.Outlined.AddCircleOutline, null) } }) }, bottomBar = { Surface(modifier = Modifier.fillMaxWidth().imePadding(), color = Color.White, shadowElevation = 8.dp) { Button(onClick = { viewModel.savePurchase("Loja Padrão", if (nickname.isBlank()) null else nickname); onBack() }, modifier = Modifier.padding(16.dp).fillMaxWidth().height(56.dp), shape = RoundedCornerShape(16.dp)) { Icon(Icons.Default.Check, null); Spacer(Modifier.width(8.dp)); Text("Finalizar Compra", fontWeight = FontWeight.Bold) } } }) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = nickname, onValueChange = { nickname = it }, label = { Text("Apelido desta compra") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), shape = RoundedCornerShape(16.dp)) {
                    Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column { Text("DATA DA COMPRA", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Slate500); Text(SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(java.util.Date()), fontWeight = FontWeight.Bold) }
                        Column(horizontalAlignment = Alignment.End) { Text("TOTAL ATUAL", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Slate500); Text("R$ ${String.format("%.2f", total).replace(".", ",")}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary) }
                    }
                }
            }
            LazyColumn(modifier = Modifier.fillMaxSize().imePadding(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(items) { item -> PurchaseChecklistItem(item = item, onToggleEdit = { viewModel.toggleEdit(item.productId) }, onUpdate = { p, q, rq -> viewModel.updateItem(item.productId, p, q, rq) }, delta = viewModel.calculateDelta(item)) }
            }
        }
    }
    if (showAddItemDialog) { /* AlertDialog skip for brevity */ }
}

@Composable
fun ProductCard(product: MockProduct, onClick: () -> Unit) {
    val lastEntry = product.lastEntry
    Card(modifier = Modifier.fillMaxWidth().clickable { onClick() }, colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(modifier = Modifier.size(44.dp), color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(12.dp)) { Box(contentAlignment = Alignment.Center) { Text(product.name.take(1).uppercase(), fontWeight = FontWeight.Bold, color = Slate500) } }
            Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(product.name, fontWeight = FontWeight.Bold); Text(if (lastEntry != null) "${lastEntry.quantity}${product.unit} em ${formatDate(lastEntry.date)}" else "Sem registros", style = MaterialTheme.typography.bodySmall, color = Slate500) }
            Column(horizontalAlignment = Alignment.End) { if (lastEntry != null) Text("R$ ${String.format("%.2f", lastEntry.unitPrice).replace(".", ",")}", fontWeight = FontWeight.Bold) ; PriceVariationBadge(delta = product.historicalDelta(), size = "sm") }
            Spacer(Modifier.width(8.dp)); Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
fun PurchaseChecklistItem(item: PurchaseItemState, onToggleEdit: () -> Unit, onUpdate: (String, String, String?) -> Unit, delta: Double?) {
    val isEditing = item.isEditing; val borderColor = if (isEditing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    Card(modifier = Modifier.fillMaxWidth().clickable { onToggleEdit() }, colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(20.dp), border = BorderStroke(if (isEditing) 2.dp else 1.dp, borderColor)) {
        Column {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(modifier = Modifier.size(40.dp), color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(10.dp)) { Box(contentAlignment = Alignment.Center) { Text(item.productName.take(1).uppercase(), fontWeight = FontWeight.Bold, color = Slate500) } }
                Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(item.productName, fontWeight = FontWeight.Bold); Text("Último: R$ ${String.format("%.2f", item.lastUnitPrice ?: 0.0).replace(".", ",")} (${item.lastQuantity ?: "--"}${item.unit})", style = MaterialTheme.typography.bodySmall, color = Slate500) }
                if (item.actualQty.isNotEmpty() && item.currentPrice.isNotEmpty()) {
                    val currentVal = (item.actualQty.toDoubleOrNull() ?: 0.0) * (item.currentPrice.toDoubleOrNull() ?: 0.0)
                    Column(horizontalAlignment = Alignment.End) { Text("R$ ${String.format("%.2f", currentVal).replace(".", ",")}", fontWeight = FontWeight.Bold); Text("${item.actualQty}${item.unit}", style = MaterialTheme.typography.labelSmall, color = Slate500) }
                }
                Spacer(Modifier.width(8.dp)); Icon(Icons.Default.ChevronRight, null, modifier = Modifier.size(16.dp).rotate(if (isEditing) 90f else 0f), tint = MaterialTheme.colorScheme.outline)
            }
            AnimatedVisibility(visible = isEditing) {
                Column(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)).padding(16.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1f)) { Text("QTD PRETENDIDA", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Slate500); OutlinedTextField(value = item.requestedQty.toString(), onValueChange = { onUpdate(item.currentPrice, item.actualQty, it) }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp), shape = RoundedCornerShape(12.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)) }
                        Column(Modifier.weight(1f)) { Text("QTD COMPRADA", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Slate500); OutlinedTextField(value = item.actualQty, onValueChange = { onUpdate(item.currentPrice, it, null) }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp), shape = RoundedCornerShape(12.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)) }
                    }
                    Spacer(Modifier.height(12.dp))
                    Column { Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text("VALOR UNITÁRIO ATUAL", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Slate500); PriceVariationBadge(delta = delta) }; OutlinedTextField(value = item.currentPrice, onValueChange = { onUpdate(it, item.actualQty, null) }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp), shape = RoundedCornerShape(12.dp), placeholder = { Text("R$ 0,00") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), textStyle = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)) }
                    Button(onClick = onToggleEdit, modifier = Modifier.padding(top = 16.dp).fillMaxWidth(), shape = RoundedCornerShape(12.dp)) { Text("Confirmar Item", fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

@Composable fun EmptyHistoryPlaceholder() { Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Outlined.History, null, modifier = Modifier.size(64.dp), tint = Slate100); Spacer(Modifier.height(16.dp)); Text("Nenhuma compra registrada", color = Slate500) } }
@Composable fun EmptyProductsPlaceholder() { Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Outlined.Inventory2, null, modifier = Modifier.size(64.dp), tint = Slate100); Spacer(Modifier.height(16.dp)); Text("Nenhum produto cadastrado", color = Slate500) } }
fun Modifier.rotate(degrees: Float): Modifier = this.then(Modifier.drawWithContent { drawContent() }.graphicsLayer { rotationZ = degrees })
