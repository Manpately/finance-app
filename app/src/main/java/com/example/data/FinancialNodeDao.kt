package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface FinancialNodeDao {
    @Query("SELECT * FROM financial_nodes ORDER BY timestamp DESC")
    fun getAllNodes(): Flow<List<FinancialNode>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNode(node: FinancialNode): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNodes(nodes: List<FinancialNode>)

    @Update
    suspend fun updateNode(node: FinancialNode)

    @Delete
    suspend fun deleteNode(node: FinancialNode)

    @Query("DELETE FROM financial_nodes WHERE id = :id")
    suspend fun deleteNodeById(id: Long)

    @Query("DELETE FROM financial_nodes WHERE parentId = :parentId")
    suspend fun deleteNodesByParentId(parentId: Long)

    @Transaction
    suspend fun deleteParentAndChildren(parentId: Long) {
        deleteNodesByParentId(parentId)
        deleteNodeById(parentId)
    }
}
