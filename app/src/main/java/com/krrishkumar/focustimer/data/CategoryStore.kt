package com.krrishkumar.focustimer.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** The live list of categories, shared by every picker so they all stay in step. */
class CategoryStore(
    private val repository: SessionRepository,
    private val scope: CoroutineScope
) {
    private val _categories = MutableStateFlow<List<Category>>(emptyList())
    val categories: StateFlow<List<Category>> = _categories

    /** Told when a category is deleted, so anything currently using it can let go. */
    var onDeleted: (Long) -> Unit = {}

    init {
        refresh()
    }

    fun refresh() {
        scope.launch { _categories.value = repository.getCategories() }
    }

    fun create(name: String, onCreated: (Category) -> Unit = {}) {
        val trimmed = name.trim().take(MAX_NAME_LENGTH)
        if (trimmed.isEmpty()) return
        scope.launch {
            val category = repository.createCategory(trimmed)
            _categories.value = repository.getCategories()
            onCreated(category)
        }
    }

    fun delete(category: Category) {
        scope.launch {
            repository.archiveCategory(category.id)
            _categories.value = repository.getCategories()
            onDeleted(category.id)
        }
    }

    companion object {
        const val MAX_NAME_LENGTH = 30
    }
}
