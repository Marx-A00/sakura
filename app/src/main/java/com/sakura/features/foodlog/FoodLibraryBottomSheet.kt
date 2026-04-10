package com.sakura.features.foodlog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.SheetState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sakura.data.food.FoodLibraryItem
import kotlinx.coroutines.launch

private val LIBRARY_TABS = listOf("Recent", "Library")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodLibraryBottomSheet(
    sheetState: SheetState,
    recentItems: List<FoodLibraryItem>,
    libraryItems: List<FoodLibraryItem>,
    onSelect: (FoodLibraryItem) -> Unit,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(initialPage = 0) { LIBRARY_TABS.size }

    fun dismiss() {
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            if (!sheetState.isVisible) onDismiss()
        }
    }

    ModalBottomSheet(
        onDismissRequest = { dismiss() },
        sheetState = sheetState
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            PrimaryTabRow(selectedTabIndex = pagerState.currentPage) {
                LIBRARY_TABS.forEachIndexed { index, title ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                        text = { Text(title) }
                    )
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(360.dp)
            ) { page ->
                val displayItems = if (page == 0) recentItems else libraryItems
                if (displayItems.isEmpty()) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Text(
                            text = if (page == 0) "No recent items" else "No saved foods",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        items(displayItems) { item ->
                            LibraryItemRow(
                                item = item,
                                onSelect = { onSelect(item); dismiss() }
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun LibraryItemRow(
    item: FoodLibraryItem,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = item.name.ifBlank { "Unnamed" },
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp
            )
            Text(
                text = "${item.calories} kcal  P: ${item.protein}g  C: ${item.carbs}g  F: ${item.fat}g",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
