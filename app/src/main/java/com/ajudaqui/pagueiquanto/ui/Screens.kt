package com.ajudaqui.pagueiquanto.ui

import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
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
import com.ajudaqui.pagueiquanto.model.AccountState
import com.ajudaqui.pagueiquanto.model.ProductState
import com.ajudaqui.pagueiquanto.model.PriceRecordState
import com.ajudaqui.pagueiquanto.ui.theme.Slate100
import com.ajudaqui.pagueiquanto.ui.theme.Slate500
import com.ajudaqui.pagueiquanto.ui.theme.Slate900
import com.ajudaqui.pagueiquanto.viewmodel.PurchaseItemState
import com.ajudaqui.pagueiquanto.viewmodel.ShoppingViewModel
import java.text.SimpleDateFormat
import java.util.Locale
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import kotlinx.coroutines.launch
import com.ajudaqui.pagueiquanto.PagueiQuantoApplication

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
    accounts: List<AccountState>,
    latestPurchase: Pair<AccountState, String>?,
    onOpenAccount: (String) -> Unit,
    onOpenPurchase: (String, String?) -> Unit,
    onAddAccount: (String, String) -> Unit,
    onDeleteAccount: ((String) -> Unit)? = null
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var accountToDelete by remember { mutableStateOf<AccountState?>(null) }

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
                    ReminderListCard(
                        account = account, 
                        onClick = { onOpenAccount(account.id) },
                        onLongClick = { accountToDelete = account }
                    ) 
                }
            }
        }
    }
    if (showAddDialog) {
        var name by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { showAddDialog = false }, title = { Text("Nova Lista") }, text = { OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nome da Lista") }, modifier = Modifier.fillMaxWidth()) }, confirmButton = { Button(onClick = { if (name.isNotBlank()) { onAddAccount(name, "cart"); showAddDialog = false } }) { Text("Criar") } })
    }
    if (accountToDelete != null && onDeleteAccount != null) {
        AlertDialog(
            onDismissRequest = { accountToDelete = null },
            title = { Text("Excluir Categoria?") },
            text = { Text("Deseja realmente excluir permanentemente a categoria \"${accountToDelete!!.name}\" e todas as compras vinculadas?") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteAccount(accountToDelete!!.id)
                        accountToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Excluir")
                }
            },
            dismissButton = {
                TextButton(onClick = { accountToDelete = null }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
fun HeroCard(account: AccountState, date: String, onOpen: () -> Unit) {
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ReminderListCard(account: AccountState, onClick: () -> Unit, onLongClick: (() -> Unit)? = null) {
    Card(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, Slate100)) {
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
        }
    }
}

@Composable
fun LatestItemRow(product: ProductState) {
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CategoryHistoryScreen(
    account: AccountState, 
    onNewPurchase: () -> Unit, 
    onOpenPurchase: (String, String?) -> Unit, 
    onDeletePurchase: ((String, String?) -> Unit)? = null
) {
    var purchaseToDelete by remember { mutableStateOf<Pair<String, String?>?>(null) }

    Scaffold(topBar = { PagueiQuantoTopBar(title = account.name, subtitle = "Histórico de compras") }) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {


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
                    PurchaseHistoryItem(
                        date = first.date, 
                        nickname = first.nickname, 
                        total = records.sumOf { it.first.unitPrice * it.first.quantity }, 
                        qty = records.size, 
                        onClick = { onOpenPurchase(first.date, first.nickname) },
                        onLongClick = { purchaseToDelete = first.date to first.nickname }
                    )
                }
            }
        }
    }

    if (purchaseToDelete != null && onDeletePurchase != null) {
        val (date, nickname) = purchaseToDelete!!
        AlertDialog(
            onDismissRequest = { purchaseToDelete = null },
            title = { Text("Excluir Compra?") },
            text = { Text("Deseja realmente excluir permanentemente esta compra do dia ${formatDate(date)}?") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeletePurchase(date, nickname)
                        purchaseToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Excluir")
                }
            },
            dismissButton = {
                TextButton(onClick = { purchaseToDelete = null }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
fun CategoryProductsScreen(account: AccountState, onOpenProduct: (String) -> Unit) {
    Scaffold(topBar = { PagueiQuantoTopBar(title = account.name, subtitle = "Itens cadastrados") }) { padding ->
        if (account.products.isEmpty()) EmptyProductsPlaceholder()
        else LazyColumn(modifier = Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(account.products) { product -> ProductCard(product = product, onClick = { onOpenProduct(product.id) }) }
        }
    }
}

@Composable
fun AllListsScreen(accounts: List<AccountState>, onOpenAccount: (String) -> Unit, onDeleteAccount: ((String) -> Unit)? = null) {
    var accountToDelete by remember { mutableStateOf<AccountState?>(null) }

    Scaffold(topBar = { PagueiQuantoTopBar(title = "Minhas Listas", subtitle = "Categorias de compra") }) { padding ->
        LazyColumn(modifier = Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(accounts) { account -> 
                ReminderListCard(
                    account = account, 
                    onClick = { onOpenAccount(account.id) },
                    onLongClick = { accountToDelete = account }
                ) 
            }
        }
    }

    if (accountToDelete != null && onDeleteAccount != null) {
        AlertDialog(
            onDismissRequest = { accountToDelete = null },
            title = { Text("Excluir Categoria?") },
            text = { Text("Deseja realmente excluir permanentemente a categoria \"${accountToDelete!!.name}\" e todas as compras vinculadas?") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteAccount(accountToDelete!!.id)
                        accountToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Excluir")
                }
            },
            dismissButton = {
                TextButton(onClick = { accountToDelete = null }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
fun GlobalItemsScreen(items: List<ProductState>) {
    Scaffold(topBar = { PagueiQuantoTopBar(title = "Últimos Itens", subtitle = "Comprados recentemente") }) { padding ->
        LazyColumn(modifier = Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            items(items) { product -> LatestItemRow(product = product) }
        }
    }
}

@Composable
fun GlobalHistoryScreen(history: List<Triple<AccountState, String, Double>>, onOpenPurchase: (String, String?) -> Unit) {
    Scaffold(topBar = { PagueiQuantoTopBar(title = "Histórico Global", subtitle = "Todas as compras") }) { padding ->
        LazyColumn(modifier = Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(history) { (acc, date, total) -> PurchaseHistoryItem(date = date, nickname = acc.name, total = total, qty = 0, onClick = { onOpenPurchase(date, null) }) }
        }
    }
}

fun exportDatabase(context: android.content.Context) {
    try {
        val dbNames = listOf("pagueiquanto-db", "pagueiquanto-db-wal", "pagueiquanto-db-shm")
        val targetDir = context.getExternalFilesDir(null) ?: return
        var exportedAny = false
        dbNames.forEach { name ->
            val dbFile = context.getDatabasePath(name)
            if (dbFile.exists()) {
                val targetFile = java.io.File(targetDir, "$name-export")
                dbFile.inputStream().use { input ->
                    targetFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                exportedAny = true
            }
        }
        if (exportedAny) {
            android.widget.Toast.makeText(context, "Banco exportado para:\n${targetDir.absolutePath}/pagueiquanto-db-export", android.widget.Toast.LENGTH_LONG).show()
        } else {
            android.widget.Toast.makeText(context, "Banco de dados vazio ou inexistente!", android.widget.Toast.LENGTH_SHORT).show()
        }
    } catch (e: Exception) {
        android.widget.Toast.makeText(context, "Erro ao exportar: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun ProfileScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val app = context.applicationContext as PagueiQuantoApplication
    val repository = app.repository

    var hasCredentials by remember { mutableStateOf(repository.hasBackupCredentials()) }
    var configuredEmail by remember { mutableStateOf(repository.getBackupEmail() ?: "") }

    var showConfigDialog by remember { mutableStateOf(false) }
    var showRestoreDialog by remember { mutableStateOf(false) }
    var showBackupConfirmDialog by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }

    Scaffold(topBar = { PagueiQuantoTopBar(title = "Meu Perfil") }) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, Slate100)
            ) {
                Row(
                    modifier = Modifier.padding(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (configuredEmail.isNotEmpty()) configuredEmail.take(1).uppercase() else "U",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 24.sp
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text("Usuário", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(
                            text = if (configuredEmail.isNotEmpty()) configuredEmail else "Sem backup em nuvem",
                            color = Slate500
                        )
                    }
                }
            }

            // Seção de Nuvem
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, Slate100),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Sincronização em Nuvem",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    if (!hasCredentials) {
                        Text(
                            "Ative o backup automático diário para proteger seus dados em caso de perda do aparelho.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Slate500
                        )
                        Button(
                            onClick = { showConfigDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.CloudQueue, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Configurar Backup")
                        }
                    } else {
                        Text(
                            "O backup diário automático está ativado para esta conta.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Slate500
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { showConfigDialog = true },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Alterar Conta")
                            }

                            Button(
                                onClick = {
                                    showBackupConfirmDialog = true
                                },
                                modifier = Modifier.weight(1f),
                                enabled = !isLoading,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                if (isLoading) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                } else {
                                    Text("Fazer Backup")
                                }
                            }
                        }
                    }

                    Divider(color = Slate100, thickness = 0.5.dp)

                    OutlinedButton(
                        onClick = { showRestoreDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.SettingsBackupRestore, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Restaurar Dados da Nuvem")
                    }
                }
            }

            Button(
                onClick = { exportDatabase(context) },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Share, null)
                Spacer(Modifier.width(8.dp))
                Text("Exportar Banco de Dados (SQLite)", fontWeight = FontWeight.Bold)
            }
        }
    }

    // Dialog de Configuração de Backup
    if (showConfigDialog) {
        var emailInput by remember { mutableStateOf(configuredEmail) }
        var passwordInput by remember { mutableStateOf("") }
        var passwordVisible by remember { mutableStateOf(false) }
        var errorText by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showConfigDialog = false },
            title = { Text("Configurar Backup") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Informe o e-mail e uma senha de proteção para ativar e gerenciar seus backups.")
                    OutlinedTextField(
                        value = emailInput,
                        onValueChange = { emailInput = it },
                        label = { Text("E-mail") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                    )
                    OutlinedTextField(
                        value = passwordInput,
                        onValueChange = { passwordInput = it },
                        label = { Text("Senha (mínimo 6 caracteres)") },
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                    contentDescription = if (passwordVisible) "Ocultar" else "Mostrar"
                                )
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                    )
                    if (errorText.isNotEmpty()) {
                        Text(errorText, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(emailInput).matches()) {
                            errorText = "E-mail inválido!"
                            return@Button
                        }
                        if (passwordInput.length < 6) {
                            errorText = "A senha deve ter no mínimo 6 caracteres!"
                            return@Button
                        }
                        val hash = repository.hashPassword(passwordInput)
                        repository.saveBackupCredentials(emailInput, hash)
                        configuredEmail = emailInput
                        hasCredentials = true
                        showConfigDialog = false
                        android.widget.Toast.makeText(context, "Configurações salvas!", android.widget.Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("Salvar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfigDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Dialog de Restauração de Dados
    if (showRestoreDialog) {
        var emailInput by remember { mutableStateOf(configuredEmail) }
        var passwordInput by remember { mutableStateOf("") }
        var passwordVisible by remember { mutableStateOf(false) }
        var errorText by remember { mutableStateOf("") }
        var isRestoring by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { if (!isRestoring) showRestoreDialog = false },
            title = { Text("Restaurar Dados") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("ATENÇÃO: Este processo irá trazer seus dados da nuvem. Dados com conflito local serão substituídos.")
                    OutlinedTextField(
                        value = emailInput,
                        onValueChange = { emailInput = it },
                        label = { Text("E-mail do Backup") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isRestoring
                    )
                    OutlinedTextField(
                        value = passwordInput,
                        onValueChange = { passwordInput = it },
                        label = { Text("Senha") },
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }, enabled = !isRestoring) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                    contentDescription = if (passwordVisible) "Ocultar" else "Mostrar"
                                )
                            }
                        },
                        enabled = !isRestoring
                    )
                    if (errorText.isNotEmpty()) {
                        Text(errorText, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    if (isRestoring) {
                        Row(
                             verticalAlignment = Alignment.CenterVertically,
                             horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            Text("Restaurando e mesclando banco de dados...")
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = !isRestoring,
                    onClick = {
                        if (emailInput.isBlank() || passwordInput.isBlank()) {
                            errorText = "E-mail e senha são obrigatórios!"
                            return@Button
                        }
                        isRestoring = true
                        val hash = repository.hashPassword(passwordInput)
                        // Define temporariamente para fazer a requisição de restore
                        repository.saveBackupCredentials(emailInput, hash)

                        coroutineScope.launch {
                            try {
                                val backupJson = repository.fetchBackupFromLambda()
                                if (backupJson != null) {
                                    repository.restoreAndMergeBackup(backupJson)
                                    configuredEmail = emailInput
                                    hasCredentials = true
                                    showRestoreDialog = false
                                    android.widget.Toast.makeText(context, "Dados restaurados e mesclados com sucesso!", android.widget.Toast.LENGTH_LONG).show()
                                } else {
                                    errorText = "Nenhum backup encontrado para este e-mail!"
                                }
                            } catch (e: Exception) {
                                errorText = "Falha ao restaurar: ${e.message}"
                            } finally {
                                isRestoring = false
                            }
                        }
                    }
                ) {
                    Text("Restaurar")
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !isRestoring,
                    onClick = { showRestoreDialog = false }
                ) {
                    Text("Cancelar")
                }
            }
        )
    }

    if (showBackupConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showBackupConfirmDialog = false },
            title = { Text("Fazer Backup") },
            text = { Text("Deseja realmente realizar o backup manual dos seus dados para a nuvem?") },
            confirmButton = {
                Button(
                    onClick = {
                        showBackupConfirmDialog = false
                        isLoading = true
                        coroutineScope.launch {
                            try {
                                val data = repository.exportAllData()
                                val compressed = repository.compress(data)
                                repository.sendBackupToLambda(compressed)
                                repository.markBackupSynced()
                                android.widget.Toast.makeText(context, "Backup realizado com sucesso!", android.widget.Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                android.widget.Toast.makeText(context, "Erro ao fazer backup: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                            } finally {
                                isLoading = false
                            }
                        }
                    }
                ) {
                    Text("Sim, fazer backup")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBackupConfirmDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PurchaseHistoryItem(date: String, nickname: String?, total: Double, qty: Int, onClick: () -> Unit, onLongClick: (() -> Unit)? = null) {
    Card(modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick), colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PurchaseDetailScreen(
    date: String, 
    nickname: String?, 
    items: List<Pair<ProductState, PriceRecordState>>, 
    onBack: () -> Unit, 
    onDeleteItem: ((PriceRecordState) -> Unit)? = null,
    onUpdateItem: ((PriceRecordState, Double, Double) -> Unit)? = null,
    onAddItem: ((String, String, Double, Double, String) -> Unit)? = null
) {
    var editingRecord by remember { mutableStateOf<Pair<ProductState, PriceRecordState>?>(null) }
    var activeItemActions by remember { mutableStateOf<Pair<ProductState, PriceRecordState>?>(null) }
    var itemToDelete by remember { mutableStateOf<PriceRecordState?>(null) }
    var showAddItemDialog by remember { mutableStateOf(false) }
    val visibleItems = items.filter { it.second.unitPrice > 0.0 }
    val context = androidx.compose.ui.platform.LocalContext.current
    
    Scaffold(
        topBar = { 
            PagueiQuantoTopBar(
                title = nickname ?: formatDate(date), 
                subtitle = formatDate(date), 
                onBack = onBack,
                actions = {
                    IconButton(onClick = { showAddItemDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Adicionar Item")
                    }
                }
            ) 
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            val total = visibleItems.sumOf { it.second.unitPrice * it.second.quantity }
            Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)) {
                Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Total da Compra", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Text("R$ ${String.format("%.2f", total).replace(".", ",")}", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                }
            }
            LazyColumn(modifier = Modifier.fillMaxSize().weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(visibleItems) { (product, record) ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .combinedClickable(
                                onClick = { android.widget.Toast.makeText(context, "Segure pressionado para editar ou deletar este item", android.widget.Toast.LENGTH_SHORT).show() },
                                onLongClick = { activeItemActions = product to record }
                            ), 
                        colors = CardDefaults.cardColors(containerColor = Color.White), 
                        shape = RoundedCornerShape(16.dp), 
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(Modifier.weight(1f)) { 
                                Text(product.name + if (!product.brand.isNullOrBlank()) " (${product.brand})" else "", fontWeight = FontWeight.Bold)
                                Text("${record.quantity}${product.unit}", style = MaterialTheme.typography.bodySmall, color = Slate500) 
                            }
                            Text("R$ ${String.format("%.2f", record.unitPrice).replace(".", ",")}", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }
                }
            }
        }
    }

    if (activeItemActions != null) {
        val (product, record) = activeItemActions!!
        AlertDialog(
            onDismissRequest = { activeItemActions = null },
            title = { Text(product.name) },
            text = { Text("Escolha uma opção para este item:") },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            editingRecord = product to record
                            activeItemActions = null
                        }
                    ) {
                        Text("Editar")
                    }
                    Button(
                        onClick = {
                            itemToDelete = record
                            activeItemActions = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Deletar")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { activeItemActions = null }) {
                    Text("Cancelar")
                }
            }
        )
    }

    if (itemToDelete != null && onDeleteItem != null) {
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = { Text("Excluir Item?") },
            text = { Text("Tem certeza que deseja remover este item desta compra?") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteItem(itemToDelete!!)
                        itemToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Excluir")
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToDelete = null }) {
                    Text("Cancelar")
                }
            }
        )
    }

    if (editingRecord != null) {
        val (product, record) = editingRecord!!
        var priceInput by remember { mutableStateOf(record.unitPrice.toString()) }
        var qtyInput by remember { mutableStateOf(record.quantity.toString()) }
        AlertDialog(
            onDismissRequest = { editingRecord = null },
            title = { Text("Editar Item: ${product.name}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = priceInput,
                        onValueChange = { priceInput = it },
                        label = { Text("Valor Unitário") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                    OutlinedTextField(
                        value = qtyInput,
                        onValueChange = { qtyInput = it },
                        label = { Text("Quantidade") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val p = priceInput.replace(",", ".").toDoubleOrNull() ?: 0.0
                        val q = qtyInput.replace(",", ".").toDoubleOrNull() ?: 1.0
                        if (onUpdateItem != null) {
                            onUpdateItem(record, p, q)
                        }
                        editingRecord = null
                    }
                ) {
                    Text("Salvar")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingRecord = null }) {
                    Text("Cancelar")
                }
            }
        )
    }

    if (showAddItemDialog) {
        var name by remember { mutableStateOf("") }
        var unit by remember { mutableStateOf("un") }
        var brand by remember { mutableStateOf("") }
        var priceInput by remember { mutableStateOf("") }
        var qtyInput by remember { mutableStateOf("1") }
        AlertDialog(
            onDismissRequest = { showAddItemDialog = false },
            title = { Text("Adicionar Item à Compra") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nome do Item") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = unit, onValueChange = { unit = it }, label = { Text("Unidade (ex: un, kg, L)") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = brand, onValueChange = { brand = it }, label = { Text("Marca") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = priceInput, onValueChange = { priceInput = it }, label = { Text("Valor Unitário") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    OutlinedTextField(value = qtyInput, onValueChange = { qtyInput = it }, label = { Text("Quantidade") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (name.isNotBlank() && onAddItem != null) {
                            val price = priceInput.replace(",", ".").toDoubleOrNull() ?: 0.0
                            val qty = qtyInput.replace(",", ".").toDoubleOrNull() ?: 1.0
                            onAddItem(name, unit, price, qty, brand)
                        }
                        showAddItemDialog = false
                    }
                ) {
                    Text("Adicionar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddItemDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
fun ProductHistoryScreen(product: ProductState, onBack: () -> Unit) {
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
    val total = items.sumOf { (it.actualQty.replace(",", ".").toDoubleOrNull() ?: 0.0) * (it.currentPrice.replace(",", ".").toDoubleOrNull() ?: 0.0) }
    var showAddItemDialog by remember { mutableStateOf(false) }
    var nickname by remember { mutableStateOf("") }
    var invoiceUrl by remember { mutableStateOf<String?>(null) }
    var isImportingInvoice by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val scanner = remember { GmsBarcodeScanning.getClient(context) }
    // Ao sair sem finalizar, grava o rascunho imediatamente para não perder edições recentes.
    val backWithFlush = { viewModel.flushDraft(); onBack() }
    androidx.activity.compose.BackHandler { backWithFlush() }

    Scaffold(topBar = { PagueiQuantoTopBar(title = "Nova Compra", onBack = backWithFlush, actions = { IconButton(onClick = { showAddItemDialog = true }) { Icon(Icons.Outlined.AddCircleOutline, null) } }) }, bottomBar = { Surface(modifier = Modifier.fillMaxWidth().imePadding(), color = Color.White, shadowElevation = 8.dp) { Button(onClick = { viewModel.savePurchase("Loja Padrão", if (nickname.isBlank()) null else nickname, invoiceUrl); onBack() }, modifier = Modifier.padding(16.dp).fillMaxWidth().height(56.dp), shape = RoundedCornerShape(16.dp)) { Icon(Icons.Default.Check, null); Spacer(Modifier.width(8.dp)); Text("Finalizar Compra", fontWeight = FontWeight.Bold) } } }) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = nickname, 
                        onValueChange = { nickname = it }, 
                        label = { Text(if (!invoiceUrl.isNullOrBlank()) "Nota Escaneada" else "Apelido desta compra") }, 
                        modifier = Modifier.weight(1f), 
                        shape = RoundedCornerShape(12.dp)
                    )
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .background(
                                if (isImportingInvoice) MaterialTheme.colorScheme.surfaceVariant
                                else if (!invoiceUrl.isNullOrBlank()) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                else MaterialTheme.colorScheme.primaryContainer,
                                RoundedCornerShape(12.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isImportingInvoice) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.5.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            IconButton(
                                onClick = {
                                    scanner.startScan()
                                        .addOnSuccessListener { barcode ->
                                            val rawValue = barcode.rawValue
                                            if (!rawValue.isNullOrBlank()) {
                                                invoiceUrl = rawValue
                                                isImportingInvoice = true
                                                viewModel.importFromFiscalInvoice(rawValue) { success, message ->
                                                    isImportingInvoice = false
                                                    if (success) {
                                                        // Preencher nickname com o nome do estabelecimento se estiver vazio
                                                        if (nickname.isBlank()) {
                                                            val storeLine = message.lines().firstOrNull { it.isNotBlank() }?.removePrefix("✅ ")?.trim()
                                                            if (!storeLine.isNullOrBlank() && !storeLine.contains("item")) {
                                                                nickname = storeLine
                                                            }
                                                        }
                                                        android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
                                                    } else {
                                                        android.widget.Toast.makeText(context, "⚠️ $message", android.widget.Toast.LENGTH_LONG).show()
                                                    }
                                                }
                                            }
                                        }
                                        .addOnFailureListener { e ->
                                            android.widget.Toast.makeText(context, "Erro ao escanear: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                },
                                modifier = Modifier.size(56.dp)
                            ) {
                                Icon(
                                    imageVector = if (!invoiceUrl.isNullOrBlank()) Icons.Default.QrCode else Icons.Default.QrCodeScanner,
                                    contentDescription = "Escanear Nota Fiscal",
                                    tint = if (!invoiceUrl.isNullOrBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
                Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), shape = RoundedCornerShape(16.dp)) {
                    Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column { Text("DATA DA COMPRA", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Slate500); Text(SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(java.util.Date()), fontWeight = FontWeight.Bold) }
                        Column(horizontalAlignment = Alignment.End) { Text("TOTAL ATUAL", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Slate500); Text("R$ ${String.format("%.2f", total).replace(".", ",")}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary) }
                    }
                }
            }
            LazyColumn(modifier = Modifier.fillMaxSize().imePadding(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(items) { item -> PurchaseChecklistItem(item = item, onToggleEdit = { viewModel.toggleEdit(item.productId) }, onUpdate = { p: String, q: String, rq: String?, b: String? -> viewModel.updateItem(item.productId, p, q, rq, b) }, delta = viewModel.calculateDelta(item)) }
            }
        }
    }
    if (showAddItemDialog) {
        var name by remember { mutableStateOf("") }
        var unit by remember { mutableStateOf("un") }
        var brand by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddItemDialog = false },
            title = { Text("Novo Item") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Nome do Item") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = unit,
                        onValueChange = { unit = it },
                        label = { Text("Unidade de Medida (ex: un, kg, L)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = brand,
                        onValueChange = { brand = it },
                        label = { Text("Marca") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (name.isNotBlank()) {
                            viewModel.addNewItem(name, unit, brand)
                            showAddItemDialog = false
                        }
                    }
                ) {
                    Text("Adicionar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddItemDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
fun ProductCard(product: ProductState, onClick: () -> Unit) {
    val lastEntry = product.lastEntry
    Card(modifier = Modifier.fillMaxWidth().clickable { onClick() }, colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(modifier = Modifier.size(44.dp), color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(12.dp)) { Box(contentAlignment = Alignment.Center) { Text(product.name.take(1).uppercase(), fontWeight = FontWeight.Bold, color = Slate500) } }
            Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(product.name + if (!product.brand.isNullOrBlank()) " (${product.brand})" else "", fontWeight = FontWeight.Bold); Text(if (lastEntry != null) "${lastEntry.quantity}${product.unit} em ${formatDate(lastEntry.date)}" else "Sem registros", style = MaterialTheme.typography.bodySmall, color = Slate500) }
            Column(horizontalAlignment = Alignment.End) { if (lastEntry != null) Text("R$ ${String.format("%.2f", lastEntry.unitPrice).replace(".", ",")}", fontWeight = FontWeight.Bold) ; PriceVariationBadge(delta = product.historicalDelta(), size = "sm") }
            Spacer(Modifier.width(8.dp)); Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
fun PurchaseChecklistItem(item: PurchaseItemState, onToggleEdit: () -> Unit, onUpdate: (String, String, String?, String?) -> Unit, delta: Double?) {
    val isEditing = item.isEditing; val borderColor = if (isEditing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    Card(modifier = Modifier.fillMaxWidth().clickable { onToggleEdit() }, colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(20.dp), border = BorderStroke(if (isEditing) 2.dp else 1.dp, borderColor)) {
        Column {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(modifier = Modifier.size(40.dp), color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(10.dp)) { Box(contentAlignment = Alignment.Center) { Text(item.productName.take(1).uppercase(), fontWeight = FontWeight.Bold, color = Slate500) } }
                Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(item.productName + if (item.brand.isNotBlank()) " (${item.brand})" else "", fontWeight = FontWeight.Bold); Text("Último: R$ ${String.format("%.2f", item.lastUnitPrice ?: 0.0).replace(".", ",")} (${item.lastQuantity ?: "--"}${item.unit})", style = MaterialTheme.typography.bodySmall, color = Slate500) }
                if (item.actualQty.isNotEmpty() && item.currentPrice.isNotEmpty()) {
                    val currentVal = (item.actualQty.replace(",", ".").toDoubleOrNull() ?: 0.0) * (item.currentPrice.replace(",", ".").toDoubleOrNull() ?: 0.0)
                    Column(horizontalAlignment = Alignment.End) { Text("R$ ${String.format("%.2f", currentVal).replace(".", ",")}", fontWeight = FontWeight.Bold); Text("${item.actualQty}${item.unit}", style = MaterialTheme.typography.labelSmall, color = Slate500) }
                }
                Spacer(Modifier.width(8.dp)); Icon(Icons.Default.ChevronRight, null, modifier = Modifier.size(16.dp).rotate(if (isEditing) 90f else 0f), tint = MaterialTheme.colorScheme.outline)
            }
            AnimatedVisibility(visible = isEditing) {
                Column(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)).padding(16.dp)) {
                    OutlinedTextField(value = item.brand, onValueChange = { onUpdate(item.currentPrice, item.actualQty, null, it) }, modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp), shape = RoundedCornerShape(12.dp), label = { Text("MARCA") })
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1f)) { Text("QTD PRETENDIDA", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Slate500); OutlinedTextField(value = item.requestedQty.toString(), onValueChange = { onUpdate(item.currentPrice, item.actualQty, it, null) }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp), shape = RoundedCornerShape(12.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)) }
                        Column(Modifier.weight(1f)) { Text("QTD COMPRADA", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Slate500); OutlinedTextField(value = item.actualQty, onValueChange = { onUpdate(item.currentPrice, it, null, null) }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp), shape = RoundedCornerShape(12.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)) }
                    }
                    Spacer(Modifier.height(12.dp))
                    Column { Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text("VALOR UNITÁRIO ATUAL", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Slate500); PriceVariationBadge(delta = delta) }; OutlinedTextField(value = item.currentPrice, onValueChange = { onUpdate(it, item.actualQty, null, null) }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp), shape = RoundedCornerShape(12.dp), placeholder = { Text("R$ 0,00") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), textStyle = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)) }
                    Button(onClick = onToggleEdit, modifier = Modifier.padding(top = 16.dp).fillMaxWidth(), shape = RoundedCornerShape(12.dp)) { Text("Confirmar Item", fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

@Composable fun EmptyHistoryPlaceholder() { Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Outlined.History, null, modifier = Modifier.size(64.dp), tint = Slate100); Spacer(Modifier.height(16.dp)); Text("Nenhuma compra registrada", color = Slate500) } }
@Composable fun EmptyProductsPlaceholder() { Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Outlined.Inventory2, null, modifier = Modifier.size(64.dp), tint = Slate100); Spacer(Modifier.height(16.dp)); Text("Nenhum produto cadastrado", color = Slate500) } }
fun Modifier.rotate(degrees: Float): Modifier = this.then(Modifier.drawWithContent { drawContent() }.graphicsLayer { rotationZ = degrees })
