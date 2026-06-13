package com.ajudaqui.pagueiquanto.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ajudaqui.pagueiquanto.viewmodel.PurchaseItemState
import com.ajudaqui.pagueiquanto.viewmodel.ShoppingViewModel

@Composable
fun PriceVariationBadge(delta: Double?) {
    if (delta == null) return
    
    val isUp = delta > 0.1
    val isDown = delta < -0.1
    val color = when {
        isUp -> Color(0xFFE53935)
        isDown -> Color(0xFF43A047)
        else -> Color.Gray
    }
    
    Surface(
        color = color.copy(alpha = 0.1f),
        shape = RoundedCornerShape(100.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isUp) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = color
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = "${if (delta > 0) "+" else ""}${String.format("%.1f", delta)}%",
                style = MaterialTheme.typography.labelSmall,
                color = color,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun ChecklistItem(
    item: PurchaseItemState,
    onToggleEdit: () -> Unit,
    onUpdate: (String, String) -> Unit,
    delta: Double?
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggleEdit() }
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (item.isEditing) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        item.productName.take(1).uppercase(),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(item.productName, fontWeight = FontWeight.Bold)
                    Text(
                        "Último: R$ ${item.lastUnitPrice ?: "--"} (${item.lastQuantity ?: "--"}${item.unit})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                if (item.actualQty.isNotEmpty() && item.currentPrice.isNotEmpty()) {
                    val currentVal = (item.actualQty.toDoubleOrNull() ?: 0.0) * (item.currentPrice.toDoubleOrNull() ?: 0.0)
                    Text(
                        text = "R$ ${String.format("%.2f", currentVal)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            AnimatedVisibility(visible = item.isEditing) {
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = item.requestedQty.toString(),
                            onValueChange = { },
                            readOnly = true,
                            label = { Text("Pretendida") },
                            modifier = Modifier.weight(1f),
                            textStyle = MaterialTheme.typography.bodyMedium
                        )
                        OutlinedTextField(
                            value = item.actualQty,
                            onValueChange = { onUpdate(item.currentPrice, it) },
                            label = { Text("Comprada") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = item.currentPrice,
                            onValueChange = { onUpdate(it, item.actualQty) },
                            label = { Text("Preço Unitário Hoje") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                        )
                        PriceVariationBadge(delta = delta)
                    }
                }
            }
        }
    }
}

@Composable
fun NewPurchaseScreen(viewModel: ShoppingViewModel, onBack: () -> Unit) {
    val items by viewModel.items.collectAsState()
    val total = items.sumOf { 
        (it.actualQty.toDoubleOrNull() ?: 0.0) * (it.currentPrice.toDoubleOrNull() ?: 0.0)
    }

    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = { Text("Nova Compra") },
                actions = {
                    IconButton(onClick = { viewModel.savePurchase("Loja Padrão") }) {
                        Icon(Icons.Default.Check, contentDescription = "Finalizar")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Total Atual", style = MaterialTheme.typography.labelMedium)
                    Text(
                        "R$ ${String.format("%.2f", total)}",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black
                    )
                }
            }
            
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp)
            ) {
                items(items) { item ->
                    ChecklistItem(
                        item = item,
                        onToggleEdit = { viewModel.toggleEdit(item.productId) },
                        onUpdate = { p, q -> viewModel.updateItem(item.productId, p, q) },
                        delta = viewModel.calculateDelta(item)
                    )
                }
            }
        }
    }
}
