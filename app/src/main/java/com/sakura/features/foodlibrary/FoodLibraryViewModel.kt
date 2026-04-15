package com.sakura.features.foodlibrary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.sakura.data.food.FoodLibraryItem
import com.sakura.data.food.FoodRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class FoodLibraryViewModel(
    private val foodRepo: FoodRepository
) : ViewModel() {

    private val _allItems = MutableStateFlow<List<FoodLibraryItem>>(emptyList())
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val filteredItems: StateFlow<List<FoodLibraryItem>> = combine(
        _allItems, _searchQuery
    ) { items, query ->
        if (query.isBlank()) items
        else items.filter { it.name.contains(query, ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val isLoading = MutableStateFlow(true)

    fun updateSearch(query: String) {
        _searchQuery.value = query
    }

    fun deleteItem(itemId: String) {
        viewModelScope.launch {
            foodRepo.deleteFromLibrary(itemId)
            reload()
        }
    }

    fun saveItem(item: FoodLibraryItem) {
        viewModelScope.launch {
            foodRepo.saveToLibrary(item)
            reload()
        }
    }

    private fun reload() {
        viewModelScope.launch {
            _allItems.value = foodRepo.loadLibrary()
        }
    }

    init {
        viewModelScope.launch {
            _allItems.value = foodRepo.loadLibrary()
            isLoading.value = false
        }
    }

    companion object {
        fun factory(foodRepo: FoodRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                    @Suppress("UNCHECKED_CAST")
                    return FoodLibraryViewModel(foodRepo) as T
                }
            }
    }
}
