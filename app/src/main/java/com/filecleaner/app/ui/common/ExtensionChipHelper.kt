package com.filecleaner.app.ui.common

import android.content.Context
import com.filecleaner.app.data.FileItem
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup

object ExtensionChipHelper {

    /**
     * Builds extension filter chips from the given file list.
     * Shows the top 15 extensions by count with toggle behavior.
     */
    fun updateChips(
        context: Context,
        chipGroup: ChipGroup,
        files: List<FileItem>,
        selectedExtensions: MutableSet<String>,
        onChanged: () -> Unit
    ) {
        val extCounts = mutableMapOf<String, Int>()
        for (file in files) {
            val ext = file.name.substringAfterLast('.', "").lowercase()
            if (ext.isNotEmpty()) {
                extCounts[ext] = (extCounts[ext] ?: 0) + 1
            }
        }

        val topExtensions = extCounts.entries
            .sortedByDescending { it.value }
            .take(15)

        if (topExtensions.isEmpty()) {
            (chipGroup.parent as? android.view.View)?.visibility = android.view.View.GONE
            return
        }

        (chipGroup.parent as? android.view.View)?.visibility = android.view.View.VISIBLE
        chipGroup.removeAllViews()

        for ((ext, count) in topExtensions) {
            val chip = Chip(context).apply {
                text = ".$ext ($count)"
                isCheckable = true
                isChecked = ext in selectedExtensions
                setOnCheckedChangeListener { _, checked ->
                    if (checked) selectedExtensions.add(ext) else selectedExtensions.remove(ext)
                    onChanged()
                }
            }
            chipGroup.addView(chip)
        }
    }
}
