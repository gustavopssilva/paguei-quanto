package com.ajudaqui.pagueiquanto

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.room.Room
import com.ajudaqui.pagueiquanto.data.AppDatabase
import com.ajudaqui.pagueiquanto.repository.ShoppingRepository
import com.ajudaqui.pagueiquanto.ui.*
import com.ajudaqui.pagueiquanto.ui.theme.PagueiQuantoTheme
import com.ajudaqui.pagueiquanto.viewmodel.ShoppingViewModel
import com.ajudaqui.pagueiquanto.viewmodel.ShoppingViewModelFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val db = Room.databaseBuilder(applicationContext, AppDatabase::class.java, "shopping-db").build()
        val repository = ShoppingRepository(db.shoppingDao())
        val factory = ShoppingViewModelFactory(repository)
        val viewModel: ShoppingViewModel by viewModels { factory }
        
        setContent {
            PagueiQuantoTheme {
                val accounts by viewModel.accounts.collectAsState()
                val selectedAccountId by viewModel.selectedAccountId.collectAsState()
                val selectedAccount = accounts.find { it.id == selectedAccountId }
                
                var currentTab by remember { mutableStateOf(0) }
                var showNewPurchase by remember { mutableStateOf(false) }
                var showProductHistoryId by remember { mutableStateOf<String?>(null) }
                var showPurchaseDetail by remember { mutableStateOf<Pair<String, String?>?>(null) }

                Scaffold(
                    bottomBar = {
                        if (!showNewPurchase && showProductHistoryId == null && showPurchaseDetail == null) {
                            NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 8.dp) {
                                NavigationBarItem(
                                    selected = currentTab == 0,
                                    onClick = { currentTab = 0; viewModel.selectAccount(null) },
                                    icon = { Icon(if (currentTab == 0) Icons.Default.Home else Icons.Outlined.Home, null) },
                                    label = { Text("Início") }
                                )
                                NavigationBarItem(
                                    selected = currentTab == 1,
                                    onClick = { currentTab = 1 },
                                    icon = { Icon(if (currentTab == 1) Icons.Default.List else Icons.Outlined.List, null) },
                                    label = { Text("Listas") }
                                )
                                NavigationBarItem(
                                    selected = currentTab == 2,
                                    onClick = { currentTab = 2 },
                                    icon = { Icon(if (currentTab == 2) Icons.Default.Inventory2 else Icons.Outlined.Inventory2, null) },
                                    label = { Text("Itens") }
                                )
                                NavigationBarItem(
                                    selected = currentTab == 3,
                                    onClick = { currentTab = 3 },
                                    icon = { Icon(if (currentTab == 3) Icons.Default.History else Icons.Outlined.History, null) },
                                    label = { Text("Histórico") }
                                )
                                NavigationBarItem(
                                    selected = currentTab == 4,
                                    onClick = { currentTab = 4 },
                                    icon = { Icon(if (currentTab == 4) Icons.Default.Person else Icons.Outlined.Person, null) },
                                    label = { Text("Perfil") }
                                )
                            }
                        }
                    }
                ) { padding ->
                    Box(modifier = Modifier.padding(padding)) {
                        when {
                            showNewPurchase -> {
                                NewPurchaseScreen(
                                    viewModel = viewModel,
                                    onBack = { showNewPurchase = false }
                                )
                            }
                            showProductHistoryId != null -> {
                                val product = accounts.flatMap { it.products }.find { it.id == showProductHistoryId }
                                product?.let {
                                    ProductHistoryScreen(product = it, onBack = { showProductHistoryId = null })
                                }
                            }
                            showPurchaseDetail != null -> {
                                val (date, nick) = showPurchaseDetail!!
                                val items = viewModel.getPurchaseItems(date, nick)
                                PurchaseDetailScreen(date = date, nickname = nick, items = items, onBack = { showPurchaseDetail = null })
                            }
                            else -> {
                                when (currentTab) {
                                    0 -> HomeScreen(
                                        accounts = accounts,
                                        latestPurchase = viewModel.getLatestPurchaseGlobal(),
                                        onOpenAccount = { id -> viewModel.selectAccount(id); currentTab = 1 },
                                        onOpenPurchase = { date, nick -> showPurchaseDetail = date to nick },
                                        onAddAccount = { name, icon -> viewModel.createAccount(name, icon) }
                                    )
                                    1 -> {
                                        if (selectedAccount != null) {
                                            CategoryHistoryScreen(
                                                account = selectedAccount,
                                                onNewPurchase = { showNewPurchase = true },
                                                onOpenPurchase = { date, nick -> showPurchaseDetail = date to nick }
                                            )
                                        } else {
                                            AllListsScreen(accounts = accounts, onOpenAccount = { id -> viewModel.selectAccount(id) })
                                        }
                                    }
                                    2 -> {
                                        if (selectedAccount != null) {
                                            CategoryProductsScreen(
                                                account = selectedAccount,
                                                onOpenProduct = { id -> showProductHistoryId = id }
                                            )
                                        } else {
                                            GlobalItemsScreen(items = viewModel.getGlobalRecentItems())
                                        }
                                    }
                                    3 -> GlobalHistoryScreen(history = viewModel.getGlobalHistory(), onOpenPurchase = { date, nick -> showPurchaseDetail = date to nick })
                                    4 -> ProfileScreen()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
