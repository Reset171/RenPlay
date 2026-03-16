package ru.reset.renplay.ui.oneui.library

import android.graphics.Bitmap
import android.graphics.Outline
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import dev.oneuiproject.oneui.utils.SearchHighlighter
import ru.reset.renplay.R
import ru.reset.renplay.domain.models.Project

class OneUiProjectsAdapter(
    private val onGameClick: (Project) -> Unit,
    private val onInfoClick: (Project) -> Unit
) : ListAdapter<Project, OneUiProjectsAdapter.ViewHolder>(ProjectDiffCallback()) {

    var isGridView = false
    var highlightWord = ""
        set(value) {
            field = value
            notifyDataSetChanged()
        }
        
    var iconCache: Map<String, Bitmap> = emptyMap()
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    companion object {
        const val TYPE_LIST = 0
        const val TYPE_GRID = 1
    }

    override fun getItemViewType(position: Int): Int {
        return if (isGridView) TYPE_GRID else TYPE_LIST
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val layoutId = if (viewType == TYPE_GRID) R.layout.oneui_item_project_grid else R.layout.oneui_item_project_list
        val view = LayoutInflater.from(parent.context).inflate(layoutId, parent, false)
        return ViewHolder(view, viewType)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(itemView: View, private val viewType: Int) : RecyclerView.ViewHolder(itemView) {
        private val iconView: ImageView = itemView.findViewById(R.id.item_icon)
        private val titleView: TextView = itemView.findViewById(R.id.item_title)
        private val versionView: TextView = itemView.findViewById(R.id.item_version)
        private val infoBtn: View? = itemView.findViewById(R.id.item_info_container)
        private val searchHighlighter = SearchHighlighter(itemView.context)

        init {
            val dpToPx = itemView.context.resources.displayMetrics.density
            if (viewType == TYPE_GRID) {
                itemView.outlineProvider = object : ViewOutlineProvider() {
                    override fun getOutline(view: View, outline: Outline) {
                        outline.setRoundRect(0, 0, view.width, view.height, 24f * dpToPx)
                    }
                }
                itemView.clipToOutline = true
                searchHighlighter.highlightColor = android.graphics.Color.WHITE
            } else {
                itemView.outlineProvider = object : ViewOutlineProvider() {
                    override fun getOutline(view: View, outline: android.graphics.Outline) {
                        outline.setRoundRect(0, 0, view.width, view.height, 20f * dpToPx)
                    }
                }
                itemView.clipToOutline = true
                
                iconView.outlineProvider = object : ViewOutlineProvider() {
                    override fun getOutline(view: View, outline: android.graphics.Outline) {
                        outline.setRoundRect(0, 0, view.width, view.height, 14f * dpToPx)
                    }
                }
                iconView.clipToOutline = true
            }
        }

        fun bind(project: Project) {
            titleView.text = searchHighlighter(project.name, highlightWord)
            
            if (project.version.isNotEmpty()) {
                versionView.visibility = View.VISIBLE
                versionView.text = searchHighlighter("Версия: ${project.version}", highlightWord)
            } else {
                versionView.visibility = View.GONE
            }

            val bitmap = iconCache[project.customIconPath ?: project.iconPath ?: ""]
            if (bitmap != null) {
                iconView.setImageBitmap(bitmap)
                iconView.scaleType = ImageView.ScaleType.CENTER_CROP
                iconView.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                iconView.imageTintList = null
            } else {
                iconView.setImageResource(R.drawable.ic_no_icon)
                iconView.scaleType = ImageView.ScaleType.CENTER
                if (viewType == TYPE_GRID) {
                    iconView.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                } else {
                    iconView.setBackgroundColor(android.graphics.Color.parseColor("#1A888888"))
                }
                iconView.imageTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#888888"))
            }

            itemView.setOnClickListener { onGameClick(project) }
            infoBtn?.setOnClickListener { onInfoClick(project) }
        }
    }

    class ProjectDiffCallback : DiffUtil.ItemCallback<Project>() {
        override fun areItemsTheSame(oldItem: Project, newItem: Project): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Project, newItem: Project): Boolean {
            return oldItem == newItem
        }
    }
}
