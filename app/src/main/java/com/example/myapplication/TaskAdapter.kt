package com.example.myapplication

import android.content.res.ColorStateList
import android.graphics.Paint
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.Filter
import android.widget.Filterable
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Locale

class TaskAdapter(
    private val taskList: MutableList<Task>, // The original, full list
    private val emptyTextView: TextView
) :
    RecyclerView.Adapter<TaskAdapter.TaskViewHolder>(), Filterable {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private var filteredTaskList: MutableList<Task> = taskList

    inner class TaskViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val titleTextView: TextView = itemView.findViewById(R.id.text_task_title)
        val completedCheckBox: CheckBox = itemView.findViewById(R.id.checkbox_completed)

        val tagsTextView: TextView = itemView.findViewById(R.id.text_task_category)
        val priorityTextView: TextView = itemView.findViewById(R.id.text_task_priority)
        val deadlineTextView: TextView = itemView.findViewById(R.id.text_task_deadline)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_task, parent, false)
        return TaskViewHolder(view)
    }

    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
        // Use the filtered list for binding
        val task = filteredTaskList[position]

        // --- Core Task Details ---
        holder.titleTextView.text = task.title
        holder.completedCheckBox.isChecked = task.isCompleted

        // --- Data Binding for Tags ---
        if (task.tags.isNotEmpty()) {
            holder.tagsTextView.text = task.tags.first()
            holder.tagsTextView.visibility = View.VISIBLE
        } else {
            holder.tagsTextView.visibility = View.GONE
        }

        holder.priorityTextView.text = task.priority.uppercase()

        task.deadline?.let { date ->
            val dateFormat = SimpleDateFormat("MMM dd", Locale.getDefault())
            holder.deadlineTextView.text = dateFormat.format(date)
            holder.deadlineTextView.visibility = View.VISIBLE
        } ?: run {
            holder.deadlineTextView.text = "No date"
        }

        // --- Styling ---
        applyStrikethrough(holder.titleTextView, task.isCompleted)
        // ✨ FIX: This function now contains the logic to apply the correct color.
        applyPriorityStyle(holder.priorityTextView, task.priority)


        // Handle checkbox logic
        holder.completedCheckBox.setOnCheckedChangeListener(null)
        holder.completedCheckBox.isChecked = task.isCompleted

        holder.completedCheckBox.setOnClickListener {
            if (holder.completedCheckBox.isChecked) {
                // IMPORTANT: Find the task index in the full list for accurate deletion handling
                val originalPosition = taskList.indexOfFirst { it.id == task.id }
                if (originalPosition != -1) {
                    deleteTaskFromFirestore(task.id, originalPosition)
                }
            } else {
                updateTaskCompletionStatus(task.id, false)
            }
        }
    }

    override fun getItemCount(): Int = filteredTaskList.size

    // ---------------------------------------------------------------------------------------------
    // --- HELPER STYLING FUNCTIONS ---
    // ---------------------------------------------------------------------------------------------

    // Placeholder implementation for applyStrikethrough
    private fun applyStrikethrough(textView: TextView, isCompleted: Boolean) {
        if (isCompleted) {
            textView.paintFlags = textView.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            textView.alpha = 0.6f // Dim completed tasks
        } else {
            textView.paintFlags = textView.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
            textView.alpha = 1.0f // Full opacity for incomplete tasks
        }
    }

    /**
     * ✨ FIX: Applies the correct background color based on the priority string.
     * HIGH -> Red
     * MEDIUM -> Green
     * LOW -> Yellow
     */
    private fun applyPriorityStyle(textView: TextView, priority: String) {
        val context = textView.context
        val colorResId: Int

        when (priority.uppercase()) {
            "HIGH" -> {
                colorResId = R.color.priority_high_red
            }
            "MEDIUM" -> {
                colorResId = R.color.priority_low_green // MEDIUM requested to be GREEN
            }
            "LOW" -> {
                colorResId = R.color.priority_medium_yellow // LOW requested to be YELLOW
            }
            else -> {
                colorResId = R.color.default_priority_color
            }
        }

        // Apply the background color tint
        textView.backgroundTintList =
            ColorStateList.valueOf(ContextCompat.getColor(context, colorResId))

        // Ensure text color is visible (e.g., white text on a colored background)
        textView.setTextColor(ContextCompat.getColor(context, R.color.white))
    }

    fun updateData(newTasks: List<Task>) {
        taskList.clear()
        taskList.addAll(newTasks)

        // 1. Apply the chronological sorting immediately
        sortTasksByDeadline(taskList)

        // 2. Re-apply the sorted list to the filtered list to reset any active filter
        filteredTaskList = taskList
        notifyDataSetChanged()

        // 3. Manually trigger a check for the empty state
        updateEmptyViewVisibility()
    }
    private fun updateEmptyViewVisibility() {
        val recyclerView = (emptyTextView.parent as? ViewGroup)?.findViewById<RecyclerView>(R.id.tasks_recycler_view)
        if (taskList.isEmpty()) {
            emptyTextView.text = "You haven't added any tasks yet."
            emptyTextView.visibility = View.VISIBLE
            recyclerView?.visibility = View.GONE
        } else {
            emptyTextView.visibility = View.GONE
            recyclerView?.visibility = View.VISIBLE
        }
    }
    private fun sortTasksByDeadline(list: MutableList<Task>) {
        list.sortWith(compareBy<Task> { task ->
            // 1. Sort by Completion Status: Incomplete tasks (false) come before completed tasks (true)
            task.isCompleted
        }.thenBy { task ->
            // 2. Sort by Deadline Existence: Tasks without a deadline (null) go to the end (true comes after false)
            task.deadline == null
        }.thenBy { task ->
            // 3. Sort by actual Deadline Date: Chronological order (earliest date first)
            task.deadline
        })
    }

    // ---------------------------------------------------------------------------------------------
    // --- FIREBASE AND FILTERING FUNCTIONS ---
    // ---------------------------------------------------------------------------------------------

    // Placeholder implementation for updateTaskCompletionStatus
    private fun updateTaskCompletionStatus(taskId: String, isCompleted: Boolean) {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            Log.e("TaskAdapter", "User ID is null, cannot update task.")
            return
        }

        db.collection("users").document(userId)
            .collection("tasks").document(taskId)
            .update("isCompleted", isCompleted)
            .addOnSuccessListener {
                Log.d("TaskAdapter", "Task $taskId completion status updated to $isCompleted.")
            }
            .addOnFailureListener { e ->
                Log.e("TaskAdapter", "Error updating task completion status", e)
            }
    }

    // --- DELETION FUNCTION (Kept as is) ---
    private fun deleteTaskFromFirestore(taskId: String, position: Int) {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            Log.e("TaskAdapter", "User ID is null, cannot delete task.")
            return
        }

        db.collection("users").document(userId)
            .collection("tasks").document(taskId)
            .delete()
            .addOnSuccessListener {

                val indexInFiltered = filteredTaskList.indexOfFirst { it.id == taskId }
                if (indexInFiltered != -1) {
                    filteredTaskList.removeAt(indexInFiltered)
                    notifyItemRemoved(indexInFiltered)
                }

                Log.d("TaskAdapter", "Task $taskId deleted successfully.")
            }
            .addOnFailureListener { e ->
                Log.e("TaskAdapter", "Error deleting task", e)
                notifyItemChanged(position)
            }
    }

    // --- FILTER IMPLEMENTATION (Kept as is) ---
    override fun getFilter(): Filter {
        return object : Filter() {
            override fun performFiltering(constraint: CharSequence?): FilterResults {
                val charSearch = constraint.toString().lowercase()

                val resultsList = if (charSearch.isEmpty()) {
                    taskList
                } else {
                    taskList.filter { task ->
                        task.title.lowercase().contains(charSearch)
                    }.toMutableList()
                }

                val filterResults = FilterResults()
                filterResults.values = resultsList
                return filterResults
            }

            @Suppress("UNCHECKED_CAST")
            override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                filteredTaskList = results?.values as MutableList<Task>? ?: mutableListOf()
                notifyDataSetChanged()

                val isSearchActive = !constraint.isNullOrEmpty()
                val isListEmpty = filteredTaskList.isEmpty()

                // Logic to control visibility of empty TextView and RecyclerView
                val recyclerView = (emptyTextView.parent as? ViewGroup)?.findViewById<RecyclerView>(R.id.tasks_recycler_view)

                if (isListEmpty) {
                    emptyTextView.text = if (isSearchActive) {
                        "No tasks found matching your search."
                    } else {
                        // Assuming the default message is set elsewhere or a simple default
                        "You haven't added any tasks yet."
                    }
                    emptyTextView.visibility = View.VISIBLE
                    recyclerView?.visibility = View.GONE
                } else {
                    emptyTextView.visibility = View.GONE
                    recyclerView?.visibility = View.VISIBLE
                }
            }
        }
    }
}