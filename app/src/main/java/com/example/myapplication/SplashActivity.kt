package com.example.myapplication

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.content.Intent
import android.graphics.LinearGradient
import android.graphics.Shader
import android.util.Log
import android.util.Patterns
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.example.myapplication.databinding.ActivitySplashBinding
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore



class SplashScreenActivity : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var emailEditText: EditText

    private lateinit var binding : ActivitySplashBinding
    private lateinit var googleSignInClient: GoogleSignInClient
    private val RC_SIGN_IN = 9001 // Request code for Google Sign-In

    // --- Lifecycle and Initialization ---

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Inflate binding
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        applyGradientToText(binding.tvAppName)

        // 1. Initial Authentication Check (Fast forward to Home if already logged in)
        if (auth.currentUser != null) {
            navigateToMain()
            return
        }

        // Setup Google Sign-In Options
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            // MUST use the R.string.default_web_client_id here.
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)


        binding.btnGoogle.setOnClickListener {
            signInWithGoogle()
        }

    }

    // --- Google Sign-In Flow ---

    private fun signInWithGoogle() {
        Log.d("SplashScreenActivity", "Starting Google Sign-In Intent.")
        val signInIntent = googleSignInClient.signInIntent
        startActivityForResult(signInIntent, RC_SIGN_IN)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == RC_SIGN_IN) {
            val result = GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                // Google Sign-In was successful
                val account = result.getResult(com.google.android.gms.common.api.ApiException::class.java)!!
                Log.d("SplashScreenActivity", "Google Sign-In succeeded, ID Token obtained.")
                // Use the token to sign in to Firebase
                firebaseAuthWithGoogle(account.idToken!!)
            } catch (e: com.google.android.gms.common.api.ApiException) {
                // Google Sign-In failed
                Log.w("SplashScreenActivity", "Google sign in failed", e)
                Toast.makeText(this, "Google Sign-In Failed: ${e.statusCode}", Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * Authenticates the user with Firebase using the Google ID token.
     */
    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val firebaseUser = auth.currentUser
                    if (firebaseUser != null) {
                        Log.d("SplashScreenActivity", "Firebase Auth successful.")
                        // Check if user data is in Firestore and save it if missing
                        saveUserToFirestore(firebaseUser.uid, firebaseUser.displayName, firebaseUser.email)
                    }
                } else {
                    Log.w("SplashScreenActivity", "Firebase authentication failed.", task.exception)
                    Toast.makeText(this, "Authentication failed.", Toast.LENGTH_SHORT).show()
                }
            }
    }


    /**
     * Saves user details (UID, name, email) to the /users/{uid} document in Firestore.
     */
    private fun applyGradientToText(textView: TextView) {
        // 1. Get the colors defined in colors.xml
        val startColor = ContextCompat.getColor(this, R.color.gradient_start)
        val endColor = ContextCompat.getColor(this, R.color.gradient_end)

        // 2. Create the LinearGradient object
        val textShader: Shader = LinearGradient(
            0f, // X start position
            0f, // Y start position
            textView.paint.measureText(textView.text.toString()), // X end position (spanning the width of the text)
            textView.textSize, // Y end position (spanning the height of the text)
            intArrayOf(startColor, endColor), // Array of colors
            null, // Positions of the colors (null for uniform distribution)
            Shader.TileMode.CLAMP // Edge behavior
        )

        // 3. Apply the shader to the TextView's paint
        textView.paint.shader = textShader

        // Optional: Since we are using a shader, we should remove the textColor attribute from XML
        // or set it to transparent/white in code, but setting the shader overrides it.
    }
    private fun saveUserToFirestore(uid: String, name: String?, email: String?) {
        val userRef = db.collection("users").document(uid)

        userRef.get().addOnSuccessListener { document ->
            if (document.exists()) {
                // User document already exists in Firestore
                Log.d("SplashScreenActivity", "Existing user found in Firestore. Navigating to Main.")
                navigateToMain()
            } else {
                // New user signed in via Google. Create the initial Firestore document.
                // NOTE: User class must be defined in your project.
                val newUser = User(
                    uid = uid,
                    name = name ?: "User",
                    email = email ?: ""
                )

                userRef.set(newUser)
                    .addOnSuccessListener {
                        Log.d("SplashScreenActivity", "New user data saved to Firestore successfully.")
                        Toast.makeText(this, "Welcome, ${newUser.name}!", Toast.LENGTH_SHORT).show()
                        navigateToMain()
                    }
                    .addOnFailureListener { e ->
                        Log.w("SplashScreenActivity", "Error saving user to Firestore", e)
                        Toast.makeText(this, "Account created, but failed to save data.", Toast.LENGTH_LONG).show()
                        navigateToMain()
                    }
            }
        }.addOnFailureListener { e ->
            Log.e("SplashScreenActivity", "Error checking user existence in Firestore.", e)
            Toast.makeText(this, "Critical error during login. Try again.", Toast.LENGTH_LONG).show()
        }
    }

    private fun navigateToMain() {
        val intent = Intent(this, HomeScreenActivity::class.java)
        startActivity(intent)
        finish()
    }
}