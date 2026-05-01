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

import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class OneUiFolderPickerAdapter(
    private val coroutineScope: kotlinx.coroutines.CoroutineScope,
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
        private val bgView: ImageView = itemView.findViewById(R.id.item_background)
        private val overlayView: View = itemView.findViewById(R.id.item_overlay)
        private val defaultTextColors = titleView.textColors

        init {
            itemView.setOnTouchListener { v, event ->
                when (event.action) {
                    android.view.MotionEvent.ACTION_DOWN -> v.animate().scaleX(0.96f).scaleY(0.96f).setDuration(150).start()
                    android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> v.animate().scaleX(1f).scaleY(1f).setDuration(150).start()
                }
                false
            }

            val dpToPx = itemView.context.resources.displayMetrics.density
            bgView.outlineProvider = object : android.view.ViewOutlineProvider() {
                override fun getOutline(view: View, outline: android.graphics.Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, 20f * dpToPx)
                }
            }
            bgView.clipToOutline = true

            iconView.outlineProvider = object : android.view.ViewOutlineProvider() {
                override fun getOutline(view: View, outline: android.graphics.Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, 8f * dpToPx)
                }
            }
            iconView.clipToOutline = true
        }

        fun bind(item: PickerItem) {
            titleView.text = item.name
            val context = itemView.context

            val backgroundRes = if (item.isGame) R.drawable.bg_list_card_game else R.drawable.bg_list_card
            itemView.setBackgroundResource(backgroundRes)

            bgView.setImageDrawable(null)
            overlayView.visibility = View.GONE
            iconView.clearColorFilter()
            iconView.setImageDrawable(null)

            if (item.isGame) {
                if (item.backgroundPath != null) {
                    bgView.tag = item.backgroundPath
                    coroutineScope.launch {
                        val bmp = ru.reset.renplay.utils.GameAssetExtractor.loadBitmap(item.backgroundPath, 600, 200)
                        withContext(kotlinx.coroutines.Dispatchers.Main) {
                            if (bgView.tag == item.backgroundPath && bmp != null) {
                                bgView.setImageBitmap(bmp)
                                overlayView.visibility = View.VISIBLE
                                titleView.setTextColor(android.graphics.Color.WHITE)

                                if (bgView.alpha == 0f) {
                                    bgView.scaleX = 0.8f
                                    bgView.scaleY = 0.8f
                                    bgView.animate()
                                        .alpha(1f)
                                        .scaleX(1f)
                                        .scaleY(1f)
                                        .setDuration(350)
                                        .setInterpolator(android.view.animation.OvershootInterpolator(1.2f))
                                        .start()
                                }
                            }
                        }
                    }
                } else {
                    bgView.alpha = 0f
                    bgView.tag = null
                    titleView.setTextColor(defaultTextColors)
                }

                if (item.iconPath != null) {
                    iconView.tag = item.iconPath
                    coroutineScope.launch {
                        val bmp = ru.reset.renplay.utils.GameAssetExtractor.loadBitmap(item.iconPath, 100, 100)
                        withContext(kotlinx.coroutines.Dispatchers.Main) {
                            if (iconView.tag == item.iconPath && bmp != null) {
                                iconView.setImageBitmap(bmp)
                                iconView.layoutParams.width = (36f * context.resources.displayMetrics.density).toInt()
                                iconView.layoutParams.height = (36f * context.resources.displayMetrics.density).toInt()
                                iconView.requestLayout()
                            }
                        }
                    }
                } else {
                    iconView.tag = null
                    iconView.setImageResource(dev.oneuiproject.oneui.R.drawable.ic_oui_game)
                    val typedValue = android.util.TypedValue()
                    context.theme.resolveAttribute(androidx.appcompat.R.attr.colorPrimaryDark, typedValue, true)
                    iconView.setColorFilter(if (item.backgroundPath != null) android.graphics.Color.WHITE else typedValue.data)
                    iconView.layoutParams.width = (24f * context.resources.displayMetrics.density).toInt()
                    iconView.layoutParams.height = (24f * context.resources.displayMetrics.density).toInt()
                    iconView.requestLayout()
                }
            } else {
                bgView.alpha = 0f
                bgView.tag = null
                titleView.setTextColor(defaultTextColors)

                iconView.layoutParams.width = (24f * context.resources.displayMetrics.density).toInt()
                iconView.layoutParams.height = (24f * context.resources.displayMetrics.density).toInt()
                iconView.requestLayout()

                val isZip = item.file.extension.lowercase() == "zip"
                if (item.isDirectory || isZip) {
                    iconView.setImageResource(if (isZip) R.drawable.ic_folder_zip else dev.oneuiproject.oneui.R.drawable.ic_oui_folder)
                    val tv = android.util.TypedValue()
                    context.theme.resolveAttribute(androidx.appcompat.R.attr.colorPrimaryDark, tv, true)
                    iconView.setColorFilter(tv.data)
                } else {
                    iconView.setImageResource(dev.oneuiproject.oneui.R.drawable.ic_oui_document)
                    val tv = android.util.TypedValue()
                    context.theme.resolveAttribute(android.R.attr.textColorSecondary, tv, true)
                    iconView.setColorFilter(androidx.core.graphics.ColorUtils.setAlphaComponent(tv.data, 128))
                }
            }
            itemView.setOnClickListener { onClick(item) }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<PickerItem>() {
        override fun areItemsTheSame(oldItem: PickerItem, newItem: PickerItem): Boolean = oldItem.file.absolutePath == newItem.file.absolutePath
        override fun areContentsTheSame(oldItem: PickerItem, newItem: PickerItem): Boolean = oldItem == newItem
    }
}
