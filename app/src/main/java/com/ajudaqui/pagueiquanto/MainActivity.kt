package com.ajudaqui.pagueiquanto

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.*
import androidx.room.Room
import com.ajudaqui.pagueiquanto.data.AppDatabase
import com.ajudaqui.pagueiquanto.repository.ShoppingRepository
import com.ajudaqui.pagueiquanto.ui.*
import com.ajudaqui.pagueiquanto.ui.theme.PagueiQuantoTheme
import com.ajudaqui.pagueiquanto.viewmodel.ShoppingViewModel
import com.ajudaqui.pagueiquanto.viewmodel.ShoppingViewModelFactory

sealed class Screen {
    object Accounts : Screen()
    data class AccountDetail(val accountId: String) : Screen()
    data class PurchaseDetail(val date: String, val nickname: String?) : Screen()
    data class ProductHistory(val productId: String) : Screen()
    object NewPurchase : Screen()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // --- INJEÇÃO DE DEPENDÊNCIA MANUAL ---
        val db = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java, 
            "shopping-db"
        ).build()
        
        val repository = ShoppingRepository(db.shoppingDao())
        val factory = ShoppingViewModelFactory(repository)
        val viewModel: ShoppingViewModel by viewModels { factory }
        
        setContent {
            PagueiQuantoTheme {
                val accounts by viewModel.accounts.collectAsState()
                val selectedAccount by viewModel.selectedAccount.collectAsState()
                var currentScreen by remember { mutableStateOf<Screen>(Screen.Accounts) }

                when (val screen = currentScreen) {
                    is Screen.Accounts -> {
                        AccountsScreen(
                            accounts = accounts,
                            onOpenAccount = { accountId -> 
                                viewModel.selectAccount(accountId)
                                currentScreen = Screen.AccountDetail(accountId) 
                            },
                            onAddAccount = { name, icon ->
                                viewModel.createAccount(name, icon)
                            },
                            totalSpent = viewModel.getTotalSpentThisMonth()
                        )
                    }
                    is Screen.AccountDetail -> {
                        selectedAccount?.let { account ->
                            AccountDetailScreen(
                                account = account,
                                onBack = { currentScreen = Screen.Accounts },
                                onNewPurchase = { currentScreen = Screen.NewPurchase },
                                onOpenProduct = { productId -> 
                                    currentScreen = Screen.ProductHistory(productId)
                                },
                                onOpenPurchase = { date, nickname ->
                                    currentScreen = Screen.PurchaseDetail(date, nickname)
                                }
                            )
                        }
                    }
                    is Screen.PurchaseDetail -> {
                        val items = viewModel.getPurchaseItems(screen.date, screen.nickname)
                        PurchaseDetailScreen(
                            date = screen.date,
                            nickname = screen.nickname,
                            items = items,
                            onBack = { currentScreen = Screen.AccountDetail(selectedAccount?.id ?: "") }
                        )
                    }
                    is Screen.ProductHistory -> {
                        val product = selectedAccount?.products?.find { it.id == screen.productId }
                        product?.let { 
                            ProductHistoryScreen(
                                product = it,
                                onBack = { currentScreen = Screen.AccountDetail(selectedAccount?.id ?: "") }
                            )
                        }
                    }
                    is Screen.NewPurchase -> {
                        NewPurchaseScreen(
                            viewModel = viewModel,
                            onBack = { currentScreen = Screen.AccountDetail(selectedAccount?.id ?: "") }
                        )
                    }
                }
            }
        }
    }
}
