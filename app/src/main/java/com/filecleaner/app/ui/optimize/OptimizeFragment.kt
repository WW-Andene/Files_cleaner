package com.filecleaner.app.ui.optimize

import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.filecleaner.app.R
import com.filecleaner.app.databinding.FragmentOptimizeBinding
import com.filecleaner.app.utils.StorageOptimizer
import com.filecleaner.app.viewmodel.MainViewModel
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class OptimizeFragment : Fragment() {

    private var _binding: FragmentOptimizeBinding? = null
    private val binding get() = _binding!!
    private val vm: MainViewModel by activityViewModels()
    private var suggestions = listOf<StorageOptimizer.Suggestion>()

    @Suppress("DEPRECATION")
    private val storagePath: String by lazy {
        Environment.getExternalStorageDirectory().absolutePath
    }

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _binding = FragmentOptimizeBinding.inflate(i, c, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnBack.setOnClickListener { findNavController().popBackStack() }

        // D3-07: Run analysis off the main thread to prevent jank
        val allFiles = vm.filesByCategory.value?.values?.flatten() ?: emptyList()
        binding.recyclerSuggestions.layoutManager = LinearLayoutManager(requireContext())

        viewLifecycleOwner.lifecycleScope.launch {
            suggestions = withContext(Dispatchers.IO) {
                StorageOptimizer.analyze(allFiles, storagePath)
            }

            if (suggestions.isEmpty()) {
                binding.tvEmpty.visibility = View.VISIBLE
                binding.recyclerSuggestions.visibility = View.GONE
                binding.btnApply.isEnabled = false
            } else {
                binding.tvEmpty.visibility = View.GONE
                binding.recyclerSuggestions.visibility = View.VISIBLE
            }

            binding.tvSummary.text = resources.getQuantityString(
                R.plurals.optimize_summary, suggestions.size, suggestions.size
            )

            binding.recyclerSuggestions.adapter = SuggestionAdapter(suggestions, storagePath)
        }

        binding.btnApply.setOnClickListener { confirmApply() }

        vm.operationResult.observe(viewLifecycleOwner) { result ->
            Snackbar.make(binding.root, result.message, Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun confirmApply() {
        val accepted = suggestions.filter { it.accepted }
        if (accepted.isEmpty()) {
            Snackbar.make(binding.root, getString(R.string.optimize_none_selected), Snackbar.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.optimize_confirm_title))
            .setMessage(resources.getQuantityString(R.plurals.optimize_confirm_message, accepted.size, accepted.size))
            .setPositiveButton(getString(R.string.move)) { _, _ ->
                for (suggestion in accepted) {
                    val targetDir = File(suggestion.suggestedPath).parent ?: continue
                    File(targetDir).mkdirs()
                    vm.moveFile(suggestion.currentPath, targetDir)
                }
                Snackbar.make(binding.root,
                    resources.getQuantityString(R.plurals.optimize_applied, accepted.size, accepted.size),
                    Snackbar.LENGTH_SHORT).show()
                findNavController().popBackStack()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    class SuggestionAdapter(
        private val items: List<StorageOptimizer.Suggestion>,
        private val storagePath: String
    ) : RecyclerView.Adapter<SuggestionAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val checkbox: CheckBox = view.findViewById(R.id.cb_accept)
            val filename: TextView = view.findViewById(R.id.tv_filename)
            val reason: TextView = view.findViewById(R.id.tv_reason)
            val movePath: TextView = view.findViewById(R.id.tv_move_path)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_optimize_suggestion, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.filename.text = item.file.name
            holder.reason.text = item.reason

            val fromRelative = item.currentPath.removePrefix(storagePath)
            val toRelative = item.suggestedPath.removePrefix(storagePath)
            holder.movePath.text = "$fromRelative \u2192 $toRelative"

            holder.checkbox.setOnCheckedChangeListener(null)
            holder.checkbox.isChecked = item.accepted
            holder.checkbox.setOnCheckedChangeListener { _, checked ->
                item.accepted = checked
            }
        }

        override fun getItemCount() = items.size
    }
}
