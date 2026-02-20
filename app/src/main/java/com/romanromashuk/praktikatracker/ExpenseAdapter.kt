package com.romanromashuk.praktikatracker

import android.app.AlertDialog
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.romanromashuk.praktikatracker.model.Expense
import java.text.SimpleDateFormat
import java.util.*

class ExpenseAdapter(
    private var expenses: MutableList<Expense>,
    private val onDeleteClick: (Expense) -> Unit,
    private val onEditClick: (Expense) -> Unit // НОВЕ: додаємо обробник для редагування
) : RecyclerView.Adapter<ExpenseAdapter.ExpenseViewHolder>() {

    class ExpenseViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvCategory: TextView = view.findViewById(R.id.tvCategory)
        val tvComment: TextView = view.findViewById(R.id.tvComment)
        val tvDate: TextView = view.findViewById(R.id.tvDate)
        val tvAmount: TextView = view.findViewById(R.id.tvAmount)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ExpenseViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_expense, parent, false)
        return ExpenseViewHolder(view)
    }

    override fun onBindViewHolder(holder: ExpenseViewHolder, position: Int) {
        val expense = expenses[position]

        holder.tvCategory.text = expense.category
        holder.tvCategory.setTextColor(getCategoryColor(expense.category))
        holder.tvComment.text = expense.title

        val sdf = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
        holder.tvDate.text = sdf.format(Date(expense.date))

        holder.tvAmount.text = "-${expense.amount} грн"
        holder.tvAmount.setTextColor(Color.parseColor("#E91E63"))

        // НОВЕ: Звичайне натискання відкриває редагування
        holder.itemView.setOnClickListener {
            onEditClick(expense)
        }

        // Довге натискання — видалення (як і було)
        holder.itemView.setOnLongClickListener {
            AlertDialog.Builder(holder.itemView.context)
                .setTitle("Видалити запис?")
                .setMessage("Це видалить витрату назавжди.")
                .setPositiveButton("Так") { _, _ ->
                    onDeleteClick(expense)
                }
                .setNegativeButton("Ні", null)
                .show()
            true
        }
    }

    override fun getItemCount() = expenses.size

    fun getCategoryColor(category: String): Int {
        return when (category) {
            "Здоров'я" -> Color.parseColor("#D32F2F")
            "Їжа" -> Color.parseColor("#388E3C")
            "Розваги" -> Color.parseColor("#FBC02D")
            "Транспорт" -> Color.parseColor("#1976D2")
            else -> Color.parseColor("#7B1FA2")
        }
    }
}