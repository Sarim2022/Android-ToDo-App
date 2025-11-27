package com.example.myapplication

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.Gravity
import android.widget.EditText
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import com.example.myapplication.databinding.DialogAddTaskBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Calendar
import java.util.Date
import java.text.SimpleDateFormat
import java.util.Locale

class AddTaskDialogFragment : DialogFragment() {

    private var _binding: DialogAddTaskBinding? = null
    private val binding get() = _binding!!

    // Global state variables
    private var selectedTags: List<String> = emptyList()
    private var selectedDeadline: Date? = null
    private var selectedPriority: String = "Low" // Initialize priority

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        if (dialog != null && dialog!!.window != null) {
            dialog!!.window!!.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            dialog!!.window!!.requestFeature(Window.FEATURE_NO_TITLE)
        }

        _binding = DialogAddTaskBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        // 1. Set Click Listeners
        binding.btnCloseDialog.setOnClickListener { dismiss() }
        binding.btnDone.setOnClickListener { saveTask() }
        binding.tvAddTags.setOnClickListener { showTagInputDialog() }
        binding.tvDeadlineSelected.setOnClickListener { showDatePicker() }
        binding.tvPrioritySelected.setOnClickListener { showPriorityDialog() } // NEW

        // 2. Set Initial Display Values
        updateTagsDisplay()
        updatePriorityDisplay(selectedPriority) // NEW
    }

    // --- Dialog Layout Control ---

    override fun onStart() {
        super.onStart()

        val window = dialog?.window ?: return

        val marginDp = 24
        val marginPx = (marginDp * resources.displayMetrics.density).toInt()

        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels

        // Calculate the final dialog width: Screen Width - (Margin on left + Margin on right)
        val dialogWidth = screenWidth - (2 * marginPx)

        window.setLayout(
            dialogWidth,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        window.setGravity(Gravity.CENTER)
    }

    // --- Priority Logic ---

    private fun showPriorityDialog() {
        if (!isAdded) return // Safety check

        val priorities = arrayOf("Low", "Medium", "High")
        var checkedItem = priorities.indexOf(selectedPriority)

        AlertDialog.Builder(requireContext())
            .setTitle("Select Priority")
            .setSingleChoiceItems(priorities, checkedItem) { dialog, which ->
                checkedItem = which
            }
            .setPositiveButton("OK") { dialog, which ->
                if (checkedItem != -1) {
                    selectedPriority = priorities[checkedItem]
                    updatePriorityDisplay(selectedPriority)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updatePriorityDisplay(priority: String) {
        if (!isAdded) return

        binding.tvPrioritySelected.text = priority

        // Use a when expression to find the appropriate text color resource
        val colorRes = when (priority.lowercase()) {
            "high" -> R.color.red_text_high // Ensure this color exists
            "medium" -> R.color.orange_text_medium // Ensure this color exists
            "low" -> R.color.green_text_low // Ensure this color exists
            else -> R.color.gray_text_default // Ensure this color exists
        }
        binding.tvPrioritySelected.setTextColor(ContextCompat.getColor(requireContext(), colorRes))
    }

    // --- Date Logic ---

    private fun showDatePicker() {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        val datePickerDialog = DatePickerDialog(
            requireContext(),
            { _, selectedYear, selectedMonth, selectedDay ->
                val cal = Calendar.getInstance()
                cal.set(selectedYear, selectedMonth, selectedDay)

                selectedDeadline = cal.time

                val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                binding.tvDeadlineSelected.text = dateFormat.format(selectedDeadline!!)
            },
            year,
            month,
            day
        )
        datePickerDialog.show()
    }

    // --- Tags Logic ---

    private fun showTagInputDialog() {
        if (!isAdded) return // Safety check

        val builder: AlertDialog.Builder = AlertDialog.Builder(requireContext())
        builder.setTitle("Add Tags (Comma separated)")

        val input = EditText(requireContext())
        val padding = (16 * resources.displayMetrics.density).toInt()
        input.setPadding(padding, padding, padding, padding)

        input.hint = "e.g., urgent, work, home"
        input.setText(selectedTags.joinToString(", "))

        builder.setView(input)

        builder.setPositiveButton("OK") { dialog, which ->
            val tagString = input.text.toString().trim()
            if (tagString.isNotEmpty()) {
                selectedTags = tagString.split(",")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
            } else {
                selectedTags = emptyList()
            }

            updateTagsDisplay()
        }

        builder.setNegativeButton("CANCEL") { dialog, which ->
            dialog.cancel()
        }

        builder.show()
    }

    private fun updateTagsDisplay() {
        if (!isAdded) return

        if (selectedTags.isNotEmpty()) {
            binding.tvAddTags.text = "${selectedTags.size} tags selected"
            // Ensure R.color.blue_primary exists
            binding.tvAddTags.setTextColor(ContextCompat.getColor(requireContext(), R.color.blue_primary))
        } else {
            binding.tvAddTags.text = "+ Add tags"
            // Ensure R.color.gray_text_default exists
            binding.tvAddTags.setTextColor(ContextCompat.getColor(requireContext(), R.color.gray_text_default))
        }
    }

    // --- Submission Logic ---

    private fun saveTask() {
        val title = binding.etTaskTitleFull.text.toString().trim()
        val deadline: Date? = selectedDeadline
        val tags: List<String> = selectedTags
        val priority: String = selectedPriority // Retrieve selected priority

        if (title.isEmpty()) {
            binding.etTaskTitleFull.error = "Task title cannot be empty"
            return
        }

        // Pass all collected data
        addTaskToFirestore(title, deadline, tags, priority)
        dismiss()
    }

    // UPDATED: Removed project field, added tags and priority
    private fun addTaskToFirestore(title: String, deadline: Date?, tags: List<String>, priority: String) {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            if (isAdded) Toast.makeText(requireContext(), "User not logged in.", Toast.LENGTH_SHORT).show()
            return
        }

        // Create a new Task object with all fields
        val newTask = Task(
            title = title,
            tags = tags,
            deadline = deadline,
            priority = priority, // Saving the selected priority
            isCompleted = false,
            timestamp = Date()
        )

        db.collection("users").document(userId)
            .collection("tasks")
            .add(newTask)
            .addOnSuccessListener {
                if (isAdded) {
                    Toast.makeText(requireContext(), "Task added!", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                if (isAdded) {
                    Toast.makeText(requireContext(), "Error adding task: ${e.message}", Toast.LENGTH_LONG).show()
                }
                Log.e("AddTaskDialog", "Error adding document", e)
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}