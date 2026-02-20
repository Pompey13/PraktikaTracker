package com.romanromashuk.praktikatracker.data

import androidx.room.*
import com.romanromashuk.praktikatracker.model.Expense

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expenses ORDER BY date DESC")
    suspend fun getAllExpenses(): List<Expense>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpense(expense: Expense)

    @Delete
    suspend fun deleteExpense(expense: Expense)
}