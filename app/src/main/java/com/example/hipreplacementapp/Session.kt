package com.example.hipreplacementapp

import com.google.firebase.Timestamp

data class Session(
    val exerciseType: String = "",
    val leftReps: Long = 0,
    val rightReps: Long = 0,
    val ts: Timestamp? = null
)
