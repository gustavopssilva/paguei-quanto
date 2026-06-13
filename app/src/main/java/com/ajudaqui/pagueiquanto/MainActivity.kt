package com.ajudaqui.pagueiquanto

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.room.Room
import com.ajudaqui.pagueiquanto.data.AppDatabase
import com.ajudaqui.pagueiquanto.repository.ShoppingRepository
import com.ajudaqui.pagueiquanto.ui.NewPurchaseScreen
import com.ajudaqui.pagueiquanto.viewmodel.ShoppingViewModel
import com.ajudaqui.pagueiquanto.viewmodel.ShoppingViewModelFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // --- INJEÇÃO DE DEPENDÊNCIA MANUAL ---


        // 1. Instância do Banco de Dados
        val db = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java, 
            "shopping-db"
        ).build()
        
        // 2. Instância do Repository
        val repository = ShoppingRepository(db.shoppingDao())
        
        // 3. Instância do Factory
        val factory = ShoppingViewModelFactory(repository)
        
        // 4. Instância do ViewModel via Factory
        val viewModel: ShoppingViewModel by viewModels { factory }
        
        // Carrega dados iniciais
        viewModel.loadProducts()

        setContent {
            MaterialTheme {
                NewPurchaseScreen(
                    viewModel = viewModel,
                    onBack = { finish() }
                )
            }
        }
    }
}
