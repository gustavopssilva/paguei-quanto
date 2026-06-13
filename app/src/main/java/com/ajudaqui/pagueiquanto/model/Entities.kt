package com.ajudaqui.pagueiquanto.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey

@Entity(tableName = "products")
data class Product(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val unit: String
)

@Entity(
    tableName = "price_records",
    foreignKeys = [
        ForeignKey(
            entity = Product::class,
            parentColumns = ["id"],
            childColumns = ["productId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class PriceRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val productId: Long,
    val unitPrice: Double,
    val quantity: Double,
    val date: Long = System.currentTimeMillis(),
    val store: String
)
