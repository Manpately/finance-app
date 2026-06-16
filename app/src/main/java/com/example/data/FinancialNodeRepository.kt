package com.example.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class FinancialNodeRepository(private val dao: FinancialNodeDao) {
    val allNodes: Flow<List<FinancialNode>> = dao.getAllNodes()

    suspend fun insertNode(node: FinancialNode): Long {
        return dao.insertNode(node)
    }

    suspend fun updateNode(node: FinancialNode) {
        dao.updateNode(node)
    }

    suspend fun deleteNode(node: FinancialNode) {
        if (node.type == "BUDGET" || node.type == "WALLET") {
            // Delete parent and all dependent child nodes cascadingly
            dao.deleteParentAndChildren(node.id)
        } else {
            // It's a single expense, just delete it
            dao.deleteNode(node)
        }
    }

    suspend fun deleteNodeById(id: Long) {
        dao.deleteNodeById(id)
    }

    /**
     * Seeds default template finance map nodes if none exist.
     */
    suspend fun checkAndSeedDefaults() {
        val currentNodes = allNodes.first()
        if (currentNodes.isEmpty()) {
            // Create Wallet Root Node first (x=500f, y=400f)
            val walletId = dao.insertNode(
                FinancialNode(
                    name = "Primary Wallet",
                    amount = 4500.0,
                    type = "WALLET",
                    parentId = null,
                    colorHex = "#D0BCFF", // M3 Lavender Highlight
                    x = 540f,
                    y = 480f
                )
            )

            // Create Budget Categories connected to Wallet
            // Food Budget Node (x=280f, y=650f)
            val foodId = dao.insertNode(
                FinancialNode(
                    name = "Food & Grocery",
                    amount = 600.0,
                    type = "BUDGET",
                    parentId = walletId,
                    colorHex = "#FFB4AB", // Coral Peach
                    x = 240f,
                    y = 700f
                )
            )

            // Fun & Leisure Budget Node (x=800f, y=650f)
            val funId = dao.insertNode(
                FinancialNode(
                    name = "Fun & Leisure",
                    amount = 400.0,
                    type = "BUDGET",
                    parentId = walletId,
                    colorHex = "#C2E7FF", // Sky Blue
                    x = 840f,
                    y = 700f
                )
            )

            // Shopping Budget Node (x=540f, y=800f)
            val transportId = dao.insertNode(
                FinancialNode(
                    name = "Transport & Fuel",
                    amount = 350.0,
                    type = "BUDGET",
                    parentId = walletId,
                    colorHex = "#B4E495", // Sage Green
                    x = 540f,
                    y = 820f
                )
            )

            // Subscriptions & Bills Budget Node (x=540f, y=200f)
            val billsId = dao.insertNode(
                FinancialNode(
                    name = "Bills & Utilities",
                    amount = 1500.0,
                    type = "BUDGET",
                    parentId = walletId,
                    colorHex = "#B39DDB", // Indigo Purple
                    x = 540f,
                    y = 180f
                )
            )

            // Create default Expenses connected to Budgets
            // Expenses under Food Category
            dao.insertNode(
                FinancialNode(
                    name = "Weekly Grocery",
                    amount = 124.50,
                    type = "EXPENSE",
                    parentId = foodId,
                    colorHex = "#FFB4AB",
                    x = 100f,
                    y = 850f
                )
            )
            dao.insertNode(
                FinancialNode(
                    name = "Pizza Night",
                    amount = 32.80,
                    type = "EXPENSE",
                    parentId = foodId,
                    colorHex = "#FFB4AB",
                    x = 300f,
                    y = 900f
                )
            )

            // Expenses under Fun & Leisure Category
            dao.insertNode(
                FinancialNode(
                    name = "Cinema Ticket",
                    amount = 18.50,
                    type = "EXPENSE",
                    parentId = funId,
                    colorHex = "#C2E7FF",
                    x = 940f,
                    y = 880f
                )
            )
            dao.insertNode(
                FinancialNode(
                    name = "Weekend Drinks",
                    amount = 45.00,
                    type = "EXPENSE",
                    parentId = funId,
                    colorHex = "#C2E7FF",
                    x = 760f,
                    y = 900f
                )
            )

            // Expenses under Transport & Fuel Category
            dao.insertNode(
                FinancialNode(
                    name = "Gas Station",
                    amount = 42.00,
                    type = "EXPENSE",
                    parentId = transportId,
                    colorHex = "#B4E495",
                    x = 540f,
                    y = 1040f
                )
            )

            // Expenses under Bills Category
            dao.insertNode(
                FinancialNode(
                    name = "Monthly Rent",
                    amount = 1100.00,
                    type = "EXPENSE",
                    parentId = billsId,
                    colorHex = "#B39DDB",
                    x = 340f,
                    y = 80f
                )
            )
            dao.insertNode(
                FinancialNode(
                    name = "Internet Subscription",
                    amount = 60.00,
                    type = "EXPENSE",
                    parentId = billsId,
                    colorHex = "#B39DDB",
                    x = 740f,
                    y = 80f
                )
            )
        }
    }
}
