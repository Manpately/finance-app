package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "financial_nodes")
data class FinancialNode(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val amount: Double,
    val type: String, // "WALLET", "BUDGET", "EXPENSE"
    val parentId: Long?, // Links to parent node id
    val colorHex: String, // Visual Hex color e.g., "#E53935"
    val x: Float, // 2D layout canvas X coordinate
    val y: Float, // 2D layout canvas Y coordinate
    val timestamp: Long = System.currentTimeMillis()
)
