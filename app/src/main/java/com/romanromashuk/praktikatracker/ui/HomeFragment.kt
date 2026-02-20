package com.romanromashuk.praktikatracker.ui

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.romanromashuk.praktikatracker.ExpenseAdapter
import com.romanromashuk.praktikatracker.R
import com.romanromashuk.praktikatracker.data.AppDatabase
import com.romanromashuk.praktikatracker.model.Expense
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class HomeFragment : Fragment() {

    private lateinit var adapter: ExpenseAdapter
    private var allExpenses = mutableListOf<Expense>()
    private var displayedExpenses = mutableListOf<Expense>()

    private lateinit var tvTotalAmount: TextView
    private lateinit var btnFilterCategory: Button
    private lateinit var btnFilterDate: Button
    private lateinit var db: AppDatabase

    // зберегти фільтри (для класу)
    private var currentCategory: String? = null
    private var currentDateMillis: Long? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_home, container, false)

        db = AppDatabase.getDatabase(requireContext())
        tvTotalAmount = view.findViewById(R.id.tvTotalAmount)
        btnFilterCategory = view.findViewById(R.id.btnFilterCategory)
        btnFilterDate = view.findViewById(R.id.btnFilterDate)

        val recyclerView = view.findViewById<RecyclerView>(R.id.rvExpenses)
        adapter = ExpenseAdapter(displayedExpenses) { expenseToDelete ->
            deleteExpenseFromDb(expenseToDelete)
        }
        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        view.findViewById<View>(R.id.btnAddExpense).setOnClickListener { showAddDialog() }
        view.findViewById<Button>(R.id.btnStats).setOnClickListener { showStatistics() }
        btnFilterCategory.setOnClickListener { showCategoryFilterDialog() }
        btnFilterDate.setOnClickListener { showDateFilterDialog() }

        loadExpenses()
        return view
    }

    private fun loadExpenses() {
        lifecycleScope.launch {
            val list = db.expenseDao().getAllExpenses()
            allExpenses.clear()
            allExpenses.addAll(list)
            applyFilters() // Застосовуємо збережені фільтри до нових даних
        }
    }

    private fun deleteExpenseFromDb(expense: Expense) {
        lifecycleScope.launch {
            db.expenseDao().deleteExpense(expense)
            loadExpenses()
        }
    }

    private fun showAddDialog() {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_add_expense, null)
        val etAmount = dialogView.findViewById<EditText>(R.id.etAmount)
        val etComment = dialogView.findViewById<EditText>(R.id.etComment)
        val spinner = dialogView.findViewById<Spinner>(R.id.spinnerCategory)
        val tvPickedDate = dialogView.findViewById<TextView>(R.id.tvPickedDate)

        val categories = resources.getStringArray(R.array.categories_array)
        val spinnerAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, categories)
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = spinnerAdapter

        var selectedDate = System.currentTimeMillis()
        val sdf = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
        tvPickedDate.text = sdf.format(Date(selectedDate))

        tvPickedDate.setOnClickListener {
            val cal = Calendar.getInstance()
            DatePickerDialog(requireContext(), { _, y, m, d ->
                val newCal = Calendar.getInstance()
                newCal.set(y, m, d)
                selectedDate = newCal.timeInMillis
                tvPickedDate.text = sdf.format(newCal.time)
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Нова витрата")
            .setView(dialogView)
            .setPositiveButton("Зберегти") { _, _ ->
                val amount = etAmount.text.toString().toDoubleOrNull() ?: 0.0
                if (amount > 0) {
                    val newExpense = Expense(
                        title = etComment.text.toString().ifEmpty { spinner.selectedItem.toString() },
                        amount = amount,
                        category = spinner.selectedItem.toString(),
                        date = selectedDate,
                        comment = etComment.text.toString()
                    )
                    lifecycleScope.launch {
                        db.expenseDao().insertExpense(newExpense)
                        loadExpenses()
                    }
                }
            }
            .setNegativeButton("Скасувати", null).show()
    }

    // фільтр категорія + дата одночасно
    private fun applyFilters() {
        displayedExpenses.clear()

        val sdf = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
        val filterDateStr = currentDateMillis?.let { sdf.format(Date(it)) }

        val filtered = allExpenses.filter { expense ->
            val matchesCategory = currentCategory == null || expense.category == currentCategory
            val matchesDate = filterDateStr == null || sdf.format(Date(expense.date)) == filterDateStr
            matchesCategory && matchesDate
        }

        displayedExpenses.addAll(filtered)
        adapter.notifyDataSetChanged()
        updateTotal()
    }

    private fun showCategoryFilterDialog() {
        val categories = resources.getStringArray(R.array.categories_array)
        AlertDialog.Builder(requireContext())
            .setTitle("Фільтр категорій")
            .setItems(categories) { _, which ->
                val selected = categories[which]
                currentCategory = if (selected == "Всі") null else selected
                applyFilters()
            }
            .show()
    }

    private fun showDateFilterDialog() {
        val cal = Calendar.getInstance()
        val dialog = DatePickerDialog(requireContext(), { _, y, m, d ->
            val filterCal = Calendar.getInstance()
            filterCal.set(y, m, d)
            currentDateMillis = filterCal.timeInMillis
            applyFilters()
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH))

        // кнопка скидання дати
        dialog.setButton(AlertDialog.BUTTON_NEUTRAL, "Скинути") { _, _ ->
            currentDateMillis = null
            applyFilters()
        }
        dialog.show()
    }

    private fun showStatistics() {
        if (allExpenses.isEmpty()) return
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_statistics, null)
        val container = dialogView.findViewById<LinearLayout>(R.id.statsContainer)
        val totalAll = allExpenses.sumOf { it.amount }

        allExpenses.groupBy { it.category }.forEach { (cat, list) ->
            val sum = list.sumOf { it.amount }
            val percent = if (totalAll > 0) (sum / totalAll * 100).toInt() else 0
            val row = LayoutInflater.from(context).inflate(R.layout.item_stat_row, container, false)
            row.findViewById<TextView>(R.id.tvStatLabel).text = "$cat: $sum грн ($percent%)"
            val pb = row.findViewById<ProgressBar>(R.id.pbStat)
            pb.progress = percent
            pb.progressTintList = ColorStateList.valueOf(adapter.getCategoryColor(cat))
            container.addView(row)
        }

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }

    private fun updateTotal() {
        val total = displayedExpenses.sumOf { it.amount }
        val filtersList = mutableListOf<String>()

        // дата і категорія в дужках
        currentCategory?.let { filtersList.add(it) }
        currentDateMillis?.let {
            val sdf = SimpleDateFormat("dd.MM", Locale.getDefault())
            filtersList.add(sdf.format(Date(it)))
        }

        val filterInfo = if (filtersList.isNotEmpty()) {
            " (${filtersList.joinToString(", ")})"
        } else ""

        tvTotalAmount.text = "$total грн$filterInfo"
    }
}