package ru.reset.renplay.ui.oneui.picker

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import ru.reset.renplay.R
import ru.reset.renplay.ui.picker.PickerItem

class OneUiFolderPickerAdapter(
    private val onClick: (PickerItem) -> Unit
) : ListAdapter<PickerItem, OneUiFolderPickerAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.oneui_item_picker_row, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val iconView: ImageView = itemView.findViewById(R.id.item_icon)
        private val titleView: TextView = itemView.findViewById(R.id.item_title)

        init {
            itemView.setOnTouchListener { v, event ->
                when (event.action) {
                    android.view.MotionEvent.ACTION_DOWN -> v.animate().scaleX(0.96f).scaleY(0.96f).setDuration(150).start()
                    android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> v.animate().scaleX(1f).scaleY(1f).setDuration(150).start()
                }
                false
            }
        }

        fun bind(item: PickerItem) {
            titleView.text = item.name
            val context = itemView.context
            if (item.isDirectory) {
                iconView.setImageResource(dev.oneuiproject.oneui.R.drawable.ic_oui_folder)
                val typedValue = android.util.TypedValue()
                context.theme.resolveAttribute(androidx.appcompat.R.attr.colorPrimaryDark, typedValue, true)
                iconView.setColorFilter(typedValue.data)
            } else {
                iconView.setImageResource(dev.oneuiproject.oneui.R.drawable.ic_oui_document)
                val typedValue = android.util.TypedValue()
                context.theme.resolveAttribute(android.R.attr.textColorSecondary, typedValue, true)
                iconView.setColorFilter(androidx.core.graphics.ColorUtils.setAlphaComponent(typedValue.data, 128))
            }
            itemView.setOnClickListener { onClick(item) }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<PickerItem>() {
        override fun areItemsTheSame(oldItem: PickerItem, newItem: PickerItem): Boolean = oldItem.file.absolutePath == newItem.file.absolutePath
        override fun areContentsTheSame(oldItem: PickerItem, newItem: PickerItem): Boolean = oldItem == newItem
    }
}
