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
    private lateinit var tvMonthlyAmount: TextView
    private lateinit var db: AppDatabase

    private var currentCategory: String? = null
    private var startDateMillis: Long? = null
    private var endDateMillis: Long? = null
    private var isMonthMode: Boolean = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_home, container, false)

        db = AppDatabase.getDatabase(requireContext())
        tvTotalAmount = view.findViewById(R.id.tvTotalAmount)
        tvMonthlyAmount = view.findViewById(R.id.tvMonthlyAmount)

        val recyclerView = view.findViewById<RecyclerView>(R.id.rvExpenses)
        adapter = ExpenseAdapter(displayedExpenses,
            onDeleteClick = { expenseToDelete -> deleteExpenseFromDb(expenseToDelete) },
            onEditClick = { expenseToEdit -> showAddDialog(expenseToEdit) }
        )

        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        view.findViewById<View>(R.id.btnAddExpense).setOnClickListener { showAddDialog() }
        view.findViewById<Button>(R.id.btnStats).setOnClickListener { showStatistics() }
        view.findViewById<Button>(R.id.btnFilterCategory).setOnClickListener { showCategoryFilterDialog() }
        view.findViewById<Button>(R.id.btnFilterDate).setOnClickListener { showDateFilterSelectionDialog() }

        loadExpenses()
        return view
    }

    private fun loadExpenses() {
        lifecycleScope.launch {
            val list = db.expenseDao().getAllExpenses()
            allExpenses.clear()
            allExpenses.addAll(list)
            applyFilters()
        }
    }

    private fun deleteExpenseFromDb(expense: Expense) {
        lifecycleScope.launch {
            db.expenseDao().deleteExpense(expense)
            loadExpenses()
        }
    }

    private fun applyFilters() {
        displayedExpenses.clear()
        val sdf = SimpleDateFormat("yyyyMMdd", Locale.getDefault())

        val filtered = allExpenses.filter { expense ->
            val matchesCategory = currentCategory == null || expense.category == currentCategory

            val matchesDate = when {
                startDateMillis == null -> true
                isMonthMode -> expense.date in startDateMillis!!..endDateMillis!!
                else -> sdf.format(Date(expense.date)) == sdf.format(Date(startDateMillis!!))
            }

            matchesCategory && matchesDate
        }

        displayedExpenses.addAll(filtered)
        adapter.notifyDataSetChanged()
        updateTotal()
    }

    private fun showDateFilterSelectionDialog() {
        val options = arrayOf("Обрати конкретний день", "Обрати місяць зі списку", "Скинути фільтр")
        AlertDialog.Builder(requireContext())
            .setTitle("Фільтрація за часом")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> { isMonthMode = false; openCalendarForDay() }
                    1 -> { isMonthMode = true; openMonthPicker() }
                    2 -> { startDateMillis = null; endDateMillis = null; applyFilters() }
                }
            }
            .show()
    }

    private fun openCalendarForDay() {
        val cal = Calendar.getInstance()
        DatePickerDialog(requireContext(), { _, year, month, day ->
            val filterCal = Calendar.getInstance()
            filterCal.set(year, month, day, 0, 0, 0)
            startDateMillis = filterCal.timeInMillis
            endDateMillis = null
            applyFilters()
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun openMonthPicker() {
        val months = arrayOf(
            "Січень", "Лютий", "Березень", "Квітень", "Травень", "Червень",
            "Липень", "Серпень", "Вересень", "Жовтень", "Листопад", "Грудень"
        )
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)

        AlertDialog.Builder(requireContext())
            .setTitle("Оберіть місяць ($currentYear)")
            .setItems(months) { _, which ->
                val filterCal = Calendar.getInstance()
                filterCal.set(Calendar.YEAR, currentYear)
                filterCal.set(Calendar.MONTH, which)
                filterCal.set(Calendar.DAY_OF_MONTH, 1)
                filterCal.set(Calendar.HOUR_OF_DAY, 0)
                startDateMillis = filterCal.timeInMillis

                filterCal.set(Calendar.DAY_OF_MONTH, filterCal.getActualMaximum(Calendar.DAY_OF_MONTH))
                filterCal.set(Calendar.HOUR_OF_DAY, 23)
                endDateMillis = filterCal.timeInMillis

                applyFilters()
            }
            .show()
    }

    // показуэ суми
    private fun updateTotal() {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        val monthStart = cal.timeInMillis

        // верхня сума: цей місяць + категорія
        val monthlySum = allExpenses.filter { expense ->
            expense.date >= monthStart && (currentCategory == null || expense.category == currentCategory)
        }.sumOf { it.amount }

        val monthlyCategorySuffix = currentCategory?.let { " ($it)" } ?: ""
        tvMonthlyAmount.text = "$monthlySum грн$monthlyCategorySuffix"

        // нижня сума: обраний період + категорія
        val periodSum = displayedExpenses.sumOf { it.amount }
        val periodInfo = mutableListOf<String>()
        currentCategory?.let { periodInfo.add(it) }

        startDateMillis?.let {
            val sdf = if (isMonthMode) SimpleDateFormat("MMMM yyyy", Locale("uk"))
            else SimpleDateFormat("dd.MM.yyyy", Locale("uk"))
            periodInfo.add(sdf.format(Date(it)))
        }

        val periodFilterText = if (periodInfo.isNotEmpty()) " (${periodInfo.joinToString(", ")})" else ""
        tvTotalAmount.text = "$periodSum грн$periodFilterText"
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

    private fun showAddDialog(expenseToEdit: Expense? = null) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_add_expense, null)
        val etAmount = dialogView.findViewById<EditText>(R.id.etAmount)
        val etComment = dialogView.findViewById<EditText>(R.id.etComment)
        val spinner = dialogView.findViewById<Spinner>(R.id.spinnerCategory)
        val tvPickedDate = dialogView.findViewById<TextView>(R.id.tvPickedDate)

        val categories = resources.getStringArray(R.array.categories_array)
        val spinnerAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, categories)
        spinner.adapter = spinnerAdapter

        var selectedDate = expenseToEdit?.date ?: System.currentTimeMillis()
        expenseToEdit?.let {
            etAmount.setText(it.amount.toString())
            etComment.setText(it.comment)
            val index = categories.indexOf(it.category)
            if (index >= 0) spinner.setSelection(index)
        }

        val sdf = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
        tvPickedDate.text = sdf.format(Date(selectedDate))

        tvPickedDate.setOnClickListener {
            val cal = Calendar.getInstance().apply { timeInMillis = selectedDate }
            DatePickerDialog(requireContext(), { _, y, m, d ->
                val newCal = Calendar.getInstance()
                newCal.set(y, m, d)
                selectedDate = newCal.timeInMillis
                tvPickedDate.text = sdf.format(newCal.time)
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }

        AlertDialog.Builder(requireContext())
            .setTitle(if (expenseToEdit == null) "Нова витрата" else "Редагувати")
            .setView(dialogView)
            .setPositiveButton("Зберегти") { _, _ ->
                val amount = etAmount.text.toString().toDoubleOrNull() ?: 0.0
                if (amount > 0) {
                    val expense = Expense(
                        id = expenseToEdit?.id ?: 0,
                        title = etComment.text.toString().ifEmpty { spinner.selectedItem.toString() },
                        amount = amount,
                        category = spinner.selectedItem.toString(),
                        date = selectedDate,
                        comment = etComment.text.toString()
                    )
                    lifecycleScope.launch {
                        if (expenseToEdit == null) db.expenseDao().insertExpense(expense)
                        else db.expenseDao().updateExpense(expense)
                        loadExpenses()
                    }
                }
            }
            .setNegativeButton("Скасувати", null).show()
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

        AlertDialog.Builder(requireContext()).setView(dialogView).show()
    }
}