package ru.reset.renplay.ui.oneui.library

import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.content.Context
import android.content.Intent
import android.view.MenuItem
import android.view.View
import android.widget.LinearLayout
import android.app.Activity
import android.graphics.Color
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import ru.reset.renplay.utils.PREFS_NAME
import ru.reset.renplay.ui.oneui.picker.OneUiFolderPickerActivity
import ru.reset.renplay.ui.oneui.details.OneUiGameDetailsActivity
import dev.oneuiproject.oneui.recyclerview.ktx.configureItemSwipeAnimator
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import dev.oneuiproject.oneui.layout.ToolbarLayout
import dev.oneuiproject.oneui.layout.startSearchMode
import dev.oneuiproject.oneui.recyclerview.ktx.enableCoreSeslFeatures
import dev.oneuiproject.oneui.utils.ItemDecorRule
import dev.oneuiproject.oneui.utils.SemItemDecoration
import dev.oneuiproject.oneui.widget.RoundedRecyclerView
import dev.oneuiproject.oneui.widget.ScrollAwareFloatingActionButton
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import ru.reset.renplay.R
import ru.reset.renplay.di.AppViewModelProvider
import ru.reset.renplay.ui.library.LibraryViewModel
import ru.reset.renplay.utils.applyCrossWindowBlur

class OneUiLibraryFragment : Fragment(R.layout.oneui_fragment_library) {

    private lateinit var viewModel: LibraryViewModel
    private lateinit var adapter: OneUiProjectsAdapter
    private lateinit var recyclerView: RoundedRecyclerView
    private lateinit var emptyStateLayout: LinearLayout
    private lateinit var toolbarLayout: ToolbarLayout
    private lateinit var folderPickerLauncher: androidx.activity.result.ActivityResultLauncher<Intent>

    override fun onResume() {
        super.onResume()
        viewModel.refreshProjects()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(requireActivity(), AppViewModelProvider.Factory)[LibraryViewModel::class.java]
        toolbarLayout = requireActivity().findViewById(R.id.main_toolbar_layout)
        toolbarLayout.setTitle(getString(R.string.main_title))
        toolbarLayout.showNavigationButtonAsBack = false
        
        recyclerView = view.findViewById(R.id.recycler_view)
        emptyStateLayout = view.findViewById(R.id.empty_state_layout)
        folderPickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val path = result.data?.getStringExtra("selectedPath") ?: return@registerForActivityResult
                AddGameBottomSheet.newInstance(path).show(parentFragmentManager, null)
            }
        }

        adapter = OneUiProjectsAdapter(
            onGameClick = { project ->
                val prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                if (prefs.getBoolean("use_game_details", false)) {
                    val intent = Intent(requireContext(), OneUiGameDetailsActivity::class.java).apply { putExtra("projectId", project.id) }
                    startActivity(intent)
                } else {
                    val gameFolder = File(project.path, "game")
                    if (!gameFolder.exists() || !gameFolder.isDirectory) {
                        Toast.makeText(requireContext(), getString(R.string.error_no_game_folder), Toast.LENGTH_LONG).show()
                    } else {
                        val engine = viewModel.engineManager.getEngine(project.engineVersion)
                        if (engine != null) {
                            val intent = Intent(requireContext(), org.renpy.android.PythonSDLActivity::class.java).apply {
                                putExtra("GAME_PATH", project.path)
                                putExtra("GAME_NAME", project.name)
                                putExtra("ENGINE_PATH", engine.dirPath)
                                putExtra("ENGINE_VERSION", engine.version)
                                putExtra("ENGINE_ZIP", engine.zipPath)
                                putExtra("ENGINE_LIB", engine.dirPath + "/lib")
                            }
                            startActivity(intent)
                        } else {
                            Toast.makeText(requireContext(), getString(R.string.engine_not_installed_toast), Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            },
            onInfoClick = { project -> 
                val intent = Intent(requireContext(), OneUiGameDetailsActivity::class.java).apply { putExtra("projectId", project.id) }
                startActivity(intent)
            }
        )

        recyclerView.adapter = adapter
        recyclerView.enableCoreSeslFeatures()

        recyclerView.configureItemSwipeAnimator(
            leftToRightLabel = getString(R.string.game_details_play),
            rightToLeftLabel = getString(R.string.swipe_action_details),
            leftToRightColor = android.graphics.Color.parseColor("#11a85f"),
            rightToLeftColor = android.graphics.Color.parseColor("#31a5f3"),
            leftToRightDrawableRes = dev.oneuiproject.oneui.R.drawable.ic_oui_control_play,
            rightToLeftDrawableRes = dev.oneuiproject.oneui.R.drawable.ic_oui_info_outline,
            isLeftSwipeEnabled = { true },
            isRightSwipeEnabled = { true },
            onSwiped = { position, swipeDirection, _ ->
                val project = adapter.currentList[position]
                if (swipeDirection == ItemTouchHelper.START) {
                    val intent = Intent(requireContext(), OneUiGameDetailsActivity::class.java).apply { putExtra("projectId", project.id) }
                    startActivity(intent)
                } else if (swipeDirection == ItemTouchHelper.END) {
                    val gameFolder = File(project.path, "game")
                    if (!gameFolder.exists() || !gameFolder.isDirectory) {
                        Toast.makeText(requireContext(), getString(R.string.error_no_game_folder), Toast.LENGTH_LONG).show()
                    } else {
                        val engine = viewModel.engineManager.getEngine(project.engineVersion)
                        if (engine != null) {
                            val intent = Intent(requireContext(), org.renpy.android.PythonSDLActivity::class.java).apply {
                                putExtra("GAME_PATH", project.path)
                                putExtra("GAME_NAME", project.name)
                                putExtra("ENGINE_PATH", engine.dirPath)
                                putExtra("ENGINE_VERSION", engine.version)
                                putExtra("ENGINE_ZIP", engine.zipPath)
                                putExtra("ENGINE_LIB", engine.dirPath + "/lib")
                            }
                            startActivity(intent)
                        } else {
                            Toast.makeText(requireContext(), getString(R.string.engine_not_installed_toast), Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                adapter.notifyItemChanged(position)
                true
            }
        )

        val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN or ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT, 0
        ) {
            override fun getMovementFlags(rv: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
                if (viewModel.sortOrder.value != LibraryViewModel.SortOrder.MANUAL || viewModel.searchQuery.value.isNotEmpty()) {
                    return makeMovementFlags(0, 0)
                }
                val dragFlags = if (viewModel.isGridView.value) {
                    ItemTouchHelper.UP or ItemTouchHelper.DOWN or ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
                } else {
                    ItemTouchHelper.UP or ItemTouchHelper.DOWN
                }
                return makeMovementFlags(dragFlags, 0)
            }

            override fun onMove(rv: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                val fromPosition = viewHolder.bindingAdapterPosition
                val toPosition = target.bindingAdapterPosition
                if (fromPosition == RecyclerView.NO_POSITION || toPosition == RecyclerView.NO_POSITION) return false

                val fromId = adapter.currentList[fromPosition].id
                val toId = adapter.currentList[toPosition].id
                viewModel.moveProject(fromId, toId)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}
            override fun isLongPressDragEnabled(): Boolean = true
        })
        itemTouchHelper.attachToRecyclerView(recyclerView)

        setupMenu()
        observeViewModel()
    }

    private fun setupMenu() {
        requireActivity().addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.oneui_menu_library, menu)
            }

            override fun onPrepareMenu(menu: Menu) {
                val viewTypeItem = menu.findItem(R.id.menu_view_type)
                viewTypeItem?.setIcon(if (viewModel.isGridView.value) dev.oneuiproject.oneui.R.drawable.ic_oui_list else dev.oneuiproject.oneui.R.drawable.ic_oui_list_grid)
            }

            override fun onMenuItemSelected(item: MenuItem): Boolean {
                return when (item.itemId) {
                    R.id.menu_search -> {
                        toolbarLayout.startSearchMode(
                            onBackBehavior = ToolbarLayout.SearchModeOnBackBehavior.CLEAR_DISMISS,
                            onQuery = { query, _ -> 
                                viewModel.updateSearchQuery(query)
                                true 
                            },
                            onEnd = { viewModel.updateSearchQuery("") }
                        )
                        true
                    }
                    R.id.menu_view_type -> {
                        viewModel.toggleGridView()
                        requireActivity().invalidateOptionsMenu()
                        true
                    }
                    R.id.menu_add_game -> {
                        val intent = Intent(requireContext(), OneUiFolderPickerActivity::class.java).apply {
                            putExtra("mode", "game")
                        }
                        folderPickerLauncher.launch(intent)
                        true
                    }
                    R.id.menu_sort -> {
                        val currentOrder = viewModel.sortOrder.value
                        val selectedIndex = when (currentOrder) {
                            LibraryViewModel.SortOrder.NEWEST_FIRST -> 0
                            LibraryViewModel.SortOrder.ALPHABETICAL -> 1
                            LibraryViewModel.SortOrder.MANUAL -> 2
                        }
                        val dialog = androidx.appcompat.app.AlertDialog.Builder(requireContext())
                            .setTitle(ru.reset.renplay.R.string.action_sort)
                            .setSingleChoiceItems(arrayOf(
                                getString(ru.reset.renplay.R.string.sort_newest),
                                getString(ru.reset.renplay.R.string.sort_alphabetical),
                                getString(ru.reset.renplay.R.string.sort_manual)
                            ), selectedIndex) { dialogInterface, which ->
                                when (which) {
                                    0 -> viewModel.updateSortOrder(LibraryViewModel.SortOrder.NEWEST_FIRST)
                                    1 -> viewModel.updateSortOrder(LibraryViewModel.SortOrder.ALPHABETICAL)
                                    2 -> viewModel.updateSortOrder(LibraryViewModel.SortOrder.MANUAL)
                                }
                                dialogInterface.dismiss()
                            }
                            .show()
                        dialog.window?.decorView?.findViewById<View>(androidx.appcompat.R.id.parentPanel)?.let {
                            dialog.applyCrossWindowBlur(requireContext(), it)
                        }
                        true
                    }
                    R.id.nav_settings -> {
                        findNavController().navigate(R.id.nav_settings)
                        true
                    }
                    R.id.nav_about -> {
                        findNavController().navigate(R.id.nav_about)
                        true
                    }
                    else -> false
                }
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.filteredProjects.collectLatest { projects ->
                emptyStateLayout.visibility = if (projects.isEmpty()) View.VISIBLE else View.GONE
                recyclerView.visibility = if (projects.isEmpty()) View.GONE else View.VISIBLE
                adapter.submitList(projects)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.isGridView.collectLatest { isGrid ->
                if (isGrid) {
                    recyclerView.layoutManager = GridLayoutManager(requireContext(), 2)
                    adapter.isGridView = true
                } else {
                    recyclerView.layoutManager = LinearLayoutManager(requireContext())
                    adapter.isGridView = false
                }
                while (recyclerView.itemDecorationCount > 0) recyclerView.removeItemDecorationAt(0)
                adapter.notifyItemRangeChanged(0, adapter.itemCount)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.iconCache.collectLatest { cache ->
                adapter.iconCache = cache
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.searchQuery.collectLatest { query ->
                adapter.highlightWord = query
            }
        }
    }
}
