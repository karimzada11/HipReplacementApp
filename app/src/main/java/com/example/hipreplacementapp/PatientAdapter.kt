package com.example.hipreplacementapp

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class PatientAdapter(
    private var items: List<Patient>,
    private val onClick: (Patient) -> Unit
) : RecyclerView.Adapter<PatientAdapter.VH>() {

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val emailText: TextView = v.findViewById(R.id.emailText)
        val idText: TextView = v.findViewById(R.id.idText)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.patient_row, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val p = items[position]
        holder.emailText.text = p.email
        holder.idText.text = p.uid
        holder.itemView.setOnClickListener { onClick(p) }
    }

    override fun getItemCount() = items.size

    fun update(newItems: List<Patient>) {
        items = newItems
        notifyDataSetChanged()
    }
}
