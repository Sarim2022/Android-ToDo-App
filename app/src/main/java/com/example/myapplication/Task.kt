package com.example.myapplication

import com.google.firebase.firestore.DocumentId
import java.util.Date


data class Task(
    @DocumentId
    val id: String = "",
    val title: String = "",
    val isCompleted: Boolean = false,
    val timestamp: Date = Date(),
    val priority: String = "Default"
,    val tags: List<String> = emptyList(),   // For "Add tags"
    val deadline: Date? = null              // For "Deadline"
)