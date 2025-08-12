package com.example.hipreplacementapp

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*

class SessionAdapter(
    private var items: List<Session>
) : RecyclerView.Adapter<SessionAdapter.VH>() {

    private val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val exerciseText: TextView = v.findViewById(R.id.exerciseText)
        val repsText: TextView = v.findViewById(R.id.repsText)
        val timeText: TextView = v.findViewById(R.id.timeText)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.session_row, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val s = items[position]
        holder.exerciseText.text = s.exerciseType
        holder.repsText.text = "L: ${s.leftReps}   R: ${s.rightReps}"
        holder.timeText.text = s.ts?.toDate()?.let { fmt.format(it) } ?: "—"
    }

    override fun getItemCount() = items.size

    fun update(newItems: List<Session>) {
        items = newItems
        notifyDataSetChanged()
    }
}
