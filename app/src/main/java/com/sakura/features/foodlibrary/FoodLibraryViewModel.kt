package com.sakura.features.foodlibrary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.sakura.data.food.DayTemplate
import com.sakura.data.food.FoodLibraryItem
import com.sakura.data.food.FoodRepository
import com.sakura.data.food.MealTemplate
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
    val allItems: StateFlow<List<FoodLibraryItem>> = _allItems.asStateFlow()
    private val _allTemplates = MutableStateFlow<List<MealTemplate>>(emptyList())
    private val _allDayTemplates = MutableStateFlow<List<DayTemplate>>(emptyList())
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val filteredItems: StateFlow<List<FoodLibraryItem>> = combine(
        _allItems, _searchQuery
    ) { items, query ->
        if (query.isBlank()) items
        else items.filter { it.name.contains(query, ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredTemplates: StateFlow<List<MealTemplate>> = combine(
        _allTemplates, _searchQuery
    ) { templates, query ->
        if (query.isBlank()) templates
        else templates.filter { t ->
            t.name.contains(query, ignoreCase = true) ||
                t.entries.any { it.name.contains(query, ignoreCase = true) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredDayTemplates: StateFlow<List<DayTemplate>> = combine(
        _allDayTemplates, _searchQuery
    ) { templates, query ->
        if (query.isBlank()) templates
        else templates.filter { t ->
            t.name.contains(query, ignoreCase = true) ||
                t.meals.any { m -> m.items.any { it.name.contains(query, ignoreCase = true) } }
        }
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

    fun deleteTemplate(templateId: String) {
        viewModelScope.launch {
            foodRepo.deleteTemplate(templateId)
            reload()
        }
    }

    fun saveTemplate(template: MealTemplate) {
        viewModelScope.launch {
            foodRepo.saveTemplate(template)
            reload()
        }
    }

    fun deleteDayTemplate(templateId: String) {
        viewModelScope.launch {
            foodRepo.deleteDayTemplate(templateId)
            reload()
        }
    }

    fun renameDayTemplate(template: DayTemplate, newName: String) {
        viewModelScope.launch {
            foodRepo.saveDayTemplate(template.copy(name = newName))
            reload()
        }
    }

    private fun reload() {
        viewModelScope.launch {
            _allItems.value = foodRepo.loadLibrary()
            _allTemplates.value = foodRepo.loadTemplates()
            _allDayTemplates.value = foodRepo.loadDayTemplates()
        }
    }

    init {
        viewModelScope.launch {
            _allItems.value = foodRepo.loadLibrary()
            _allTemplates.value = foodRepo.loadTemplates()
            _allDayTemplates.value = foodRepo.loadDayTemplates()
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
