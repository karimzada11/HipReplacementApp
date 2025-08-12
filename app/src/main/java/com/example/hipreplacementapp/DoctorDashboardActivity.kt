package com.example.hipreplacementapp

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

class DoctorDashboardActivity : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var adapter: PatientAdapter
    private var allPatients: List<Patient> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_doctor_dashboard)

        db = FirebaseFirestore.getInstance()

        val logout = findViewById<Button>(R.id.logout_button)
        val list = findViewById<RecyclerView>(R.id.patientList)
        val search = findViewById<EditText>(R.id.searchInput)
        val swipeRefresh = findViewById<SwipeRefreshLayout>(R.id.swipeRefresh)

        adapter = PatientAdapter(emptyList()) { patient ->
            // open detail screen
            val i = Intent(this, PatientDetailActivity::class.java)
            i.putExtra("patientUid", patient.uid)
            i.putExtra("patientEmail", patient.email)
            startActivity(i)
        }
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = adapter

        swipeRefresh.setOnRefreshListener {
            loadPatients {
                swipeRefresh.isRefreshing = false
            }
        }

        search.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {}
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val q = s?.toString()?.trim()?.lowercase() ?: ""
                val filtered = if (q.isEmpty()) allPatients
                else allPatients.filter { it.email.lowercase().contains(q) || it.uid.lowercase().contains(q) }
                adapter.update(filtered)
            }
        })

        logout.setOnClickListener {
            FirebaseAuth.getInstance().signOut()
            startActivity(Intent(this, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
            finish()
        }

        loadPatients()
    }

    private fun loadPatients(done: (() -> Unit)? = null) {
        db.collection("users")
            .whereEqualTo("role", "patient")
            .get()
            .addOnSuccessListener { snap ->
                allPatients = snap.documents.mapNotNull { d ->
                    val email = d.getString("email") ?: return@mapNotNull null
                    Patient(uid = d.id, email = email)
                }.sortedBy { it.email.lowercase() }

                adapter.update(allPatients)
                if (allPatients.isEmpty()) {
                    Toast.makeText(this, "No patients yet.", Toast.LENGTH_SHORT).show()
                }
                done?.invoke()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load patients: ${it.message}", Toast.LENGTH_LONG).show()
                done?.invoke()
            }
    }
}
