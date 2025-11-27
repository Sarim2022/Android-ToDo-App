package com.example.myapplication

import android.R
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.myapplication.databinding.ActivityHomeScreenBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.appbar.AppBarLayout
import java.util.*

class HomeScreenActivity : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth

    private lateinit var db: FirebaseFirestore

    private val taskList: MutableList<Task> = mutableListOf()
    private lateinit var taskAdapter: TaskAdapter

    private lateinit var binding: ActivityHomeScreenBinding



    private lateinit var tvNoTasks: TextView
    // You should also declare the search EditText as lateinit if it hasn't been done yet:
    private lateinit var etSearch: EditText

    @SuppressLint("SetTextI18n", "ResourceAsColor")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Initialize View Binding
        binding = ActivityHomeScreenBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        tvNoTasks = binding.tvNoTasks // Assuming tvNoTasks is an ID in your main layout
        etSearch = binding.etSearch
        // 2. Setup RecyclerView
        // The adapter is now initialized with the MutableList
        taskAdapter = TaskAdapter(taskList, tvNoTasks)
        binding.tasksRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.tasksRecyclerView.adapter = taskAdapter
        // Check if the user is logged in and has a display name before accessing it
        val displayName = auth.currentUser?.displayName
        val textToShow = if (!displayName.isNullOrBlank()) {
            "Hello, $displayName  ✌🏻"
        } else {
            // Fallback if the display name is null or empty (e.g., if they logged in with email/password only)
            "Hello ✌🏻"
        }
        binding.tvUserName.text = textToShow
        // 3. Load tasks from Firestore
        loadTasksFromFirestore()
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                taskAdapter.filter.filter(s.toString())
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, count: Int, before: Int) {}
        })
        // 4. Setup FAB Click Listener using binding
        binding.fabAddTask.setOnClickListener {
            showAddTaskDialog()
        }

        binding.ivLogout.setOnClickListener {
            performLogout()
        }

    }

    private fun performLogout() {
        auth.signOut()

        // Use requireContext() for safety if this were a Fragment, but Context in Activity is fine.
        Toast.makeText(this, "Signed out successfully.", Toast.LENGTH_SHORT).show()

        val intent = Intent(this, SplashScreenActivity::class.java)

        // Clear the back stack
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK

        startActivity(intent)
        finish()
    }

    // --- Firestore Interaction ---

    private fun loadTasksFromFirestore() {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            Toast.makeText(this, "User not logged in.", Toast.LENGTH_SHORT).show()
            return
        }

        // Listen for real-time updates
        db.collection("users").document(userId)
            .collection("tasks")
            // ✨ REMOVED: Remove Firestore ordering so the adapter can control the final displayed order.
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.w("HomeScreenActivity", "Listen failed.", e)
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    // 1. Convert all documents into a new, temporary List<Task>
                    val newTasks: List<Task> = snapshot.documents.mapNotNull { doc ->
                        doc.toObject(Task::class.java)?.copy(id = doc.id)
                    }

                    // 2. ✨ FIX: Pass the new list to the adapter's updateData function.
                    //    This function will now clear, add, SORT by deadline, and notify the RecyclerView.
                    taskAdapter.updateData(newTasks)

                    // 3. The empty view logic is now handled inside the adapter's updateData function,
                    //    but we can keep a simple log here.
                    Log.d("HomeScreenActivity", "Current tasks: ${newTasks.size}")

                    // The manual empty view logic (binding.tvNoTasks.visibility = ...)
                    // is now handled safely within the adapter's updateData function.

                }
            }
    }

    // --- UI/Dialog ---

    private fun showAddTaskDialog() {
        // Now correctly opens the detailed task dialog fragment
        val dialog = AddTaskDialogFragment()
        dialog.show(supportFragmentManager, "AddTaskDialog")
    }

    // Removed legacy addTaskToFirestore(title: String) as the dialog handles full data submission.

    // Removed legacy dpToPx(context: Context, dp: Int) as it is not used here.
}