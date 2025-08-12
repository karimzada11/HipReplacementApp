package com.example.hipreplacementapp

import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FirebaseFirestore

class PatientDetailActivity : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var adapter: SessionAdapter
    private lateinit var header: TextView
    private var patientUid: String = ""
    private var patientEmail: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_patient_detail)

        db = FirebaseFirestore.getInstance()

        header = findViewById(R.id.header)
        val list = findViewById<RecyclerView>(R.id.sessionList)

        patientUid = intent.getStringExtra("patientUid") ?: ""
        patientEmail = intent.getStringExtra("patientEmail") ?: ""
        header.text = "Patient: $patientEmail"

        adapter = SessionAdapter(emptyList())
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = adapter

        if (patientUid.isBlank()) {
            Toast.makeText(this, "Missing patient id", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        loadSessions()
    }

    private fun loadSessions() {
        db.collection("users").document(patientUid).collection("sessions")
            .orderBy("ts") // optional if you add a timestamp
            .get()
            .addOnSuccessListener { snap ->
                val sessions = snap.documents.mapNotNull { d ->
                    Session(
                        exerciseType = d.getString("exerciseType") ?: return@mapNotNull null,
                        leftReps = d.getLong("leftReps") ?: 0L,
                        rightReps = d.getLong("rightReps") ?: 0L,
                        ts = d.getTimestamp("ts")
                    )
                }.sortedByDescending { it.ts?.toDate() }
                adapter.update(sessions)
                if (sessions.isEmpty()) {
                    Toast.makeText(this, "No sessions yet.", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load sessions: ${it.message}", Toast.LENGTH_LONG).show()
            }
    }
}
