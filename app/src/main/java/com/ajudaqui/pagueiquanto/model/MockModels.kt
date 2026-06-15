package com.ajudaqui.pagueiquanto.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class MockProduct(
    val id: String,
    val name: String,
    val unit: String,
    val history: List<MockPriceRecord>
) {
    val lastEntry: MockPriceRecord? get() = history.maxByOrNull { it.dateMillis }
    
    fun calculateDelta(currentPrice: Double?): Double? {
        val lastPrice = lastEntry?.unitPrice ?: return null
        if (currentPrice == null) return null
        return ((currentPrice - lastPrice) / lastPrice) * 100
    }

    // Variação entre as duas últimas compras do histórico
    fun historicalDelta(): Double? {
        if (history.size < 2) return null
        val sorted = history.sortedByDescending { it.dateMillis }
        val current = sorted[0].unitPrice
        val last = sorted[1].unitPrice
        return ((current - last) / last) * 100
    }
}

data class MockPriceRecord(
    val id: String,
    val date: String, // format yyyy-MM-dd
    val store: String,
    val unitPrice: Double,
    val quantity: Double,
    val nickname: String? = null
) {
    val dateMillis: Long 
        get() = try {
            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(date)?.time ?: 0L
        } catch (e: Exception) {
            0L
        }
}

data class MockAccount(
    val id: String,
    val name: String,
    val icon: String,
    val products: List<MockProduct>,
    val nextPurchasePrediction: String? = null,
    val predictionProgress: Float = 0f
) {
    val lastPurchaseDate: String?
        get() = products.flatMap { it.history }.maxByOrNull { it.dateMillis }?.date

    val lastPurchaseTotal: Double 
        get() {
            val date = lastPurchaseDate ?: return 0.0
            return products.sumOf { p -> 
                p.history.find { it.date == date }?.let { it.unitPrice * it.quantity } ?: 0.0
            }
        }
}
