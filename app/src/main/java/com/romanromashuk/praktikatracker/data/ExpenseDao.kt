package com.romanromashuk.praktikatracker.data

import androidx.lifecycle.LiveData
import androidx.room.*
import com.romanromashuk.praktikatracker.model.Expense

@Dao
interface ExpenseDao {
    // всі витрати
    @Query("SELECT * FROM expenses ORDER BY date DESC")
    suspend fun getAllExpenses(): List<Expense>

    // рахує суму витрат за конкретний проміжок часу
    @Query("SELECT SUM(amount) FROM expenses WHERE date >= :startOfMonth AND date <= :endOfMonth")
    fun getMonthlyTotal(startOfMonth: Long, endOfMonth: Long): LiveData<Double?>

    // додавання нової витрати
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpense(expense: Expense)

    // редагування витрати
    @Update
    suspend fun updateExpense(expense: Expense)

    // видалення
    @Delete
    suspend fun deleteExpense(expense: Expense)
}