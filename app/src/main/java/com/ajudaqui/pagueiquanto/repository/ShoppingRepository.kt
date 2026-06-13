package com.ajudaqui.pagueiquanto.repository

import com.ajudaqui.pagueiquanto.data.ShoppingDao
import com.ajudaqui.pagueiquanto.model.Product
import com.ajudaqui.pagueiquanto.model.PriceRecord

class ShoppingRepository(private val dao: ShoppingDao) {
    val allProducts = dao.getAllProducts()

    suspend fun savePurchase(productName: String, price: Double, qty: Double, store: String, unit: String) {
        val productId = dao.insertProduct(Product(name = productName, unit = unit))
        dao.insertRecord(PriceRecord(productId = productId, unitPrice = price, quantity = qty, store = store))
    }

    suspend fun getLastEntry(productId: Long) = dao.getLastPriceRecord(productId)
}
