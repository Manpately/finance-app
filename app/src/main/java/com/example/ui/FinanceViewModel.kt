package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.FinancialNode
import com.example.data.FinancialNodeRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class FinanceViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: FinancialNodeRepository

    init {
        val database = AppDatabase.getDatabase(application)
        repository = FinancialNodeRepository(database.financialNodeDao())
        
        // Seed database with defaults if empty
        viewModelScope.launch {
            repository.checkAndSeedDefaults()
        }
    }

    val nodes: StateFlow<List<FinancialNode>> = repository.allNodes
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Derived states for wallet, budget, and expenses
    val wallets: StateFlow<List<FinancialNode>> = nodes
        .map { list -> list.filter { it.type == "WALLET" } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val budgets: StateFlow<List<FinancialNode>> = nodes
        .map { list -> list.filter { it.type == "BUDGET" } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val expenses: StateFlow<List<FinancialNode>> = nodes
        .map { list -> list.filter { it.type == "EXPENSE" } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Financial reporting helpers
    val totalIncome: StateFlow<Double> = wallets
        .map { list -> list.sumOf { it.amount } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val totalBudgetLimit: StateFlow<Double> = budgets
        .map { list -> list.sumOf { it.amount } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val totalSpent: StateFlow<Double> = expenses
        .map { list -> list.sumOf { it.amount } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    // Maps budget category ID to total expenses recorded under it
    val budgetSpentMap: StateFlow<Map<Long, Double>> = expenses
        .map { expenseList ->
            expenseList
                .filter { it.parentId != null }
                .groupBy { it.parentId!! }
                .mapValues { entry -> entry.value.sumOf { it.amount } }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    // CRUD API
    fun addNode(name: String, amount: Double, type: String, parentId: Long?, colorHex: String, x: Float, y: Float) {
        viewModelScope.launch {
            repository.insertNode(
                FinancialNode(
                    name = name,
                    amount = amount,
                    type = type,
                    parentId = parentId,
                    colorHex = colorHex,
                    x = x,
                    y = y
                )
            )
        }
    }

    fun updateNodePosition(id: Long, x: Float, y: Float) {
        viewModelScope.launch {
            val nodeToUpdate = nodes.value.find { it.id == id }
            if (nodeToUpdate != null) {
                repository.updateNode(nodeToUpdate.copy(x = x, y = y))
            }
        }
    }

    fun updateNodeDetails(id: Long, name: String, amount: Double, colorHex: String) {
        viewModelScope.launch {
            val nodeToUpdate = nodes.value.find { it.id == id }
            if (nodeToUpdate != null) {
                repository.updateNode(nodeToUpdate.copy(name = name, amount = amount, colorHex = colorHex))
            }
        }
    }

    fun deleteNode(node: FinancialNode) {
        viewModelScope.launch {
            repository.deleteNode(node)
        }
    }

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(FinanceViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return FinanceViewModel(application) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
