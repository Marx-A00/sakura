package com.sakura.features.workoutlog

import android.widget.NumberPicker
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Wraps the native Android NumberPicker in a Compose AndroidView.
 *
 * Smooth, battle-tested scroll wheel — no custom fling/snap needed.
 */
@Composable
fun ScrollWheelPicker(
    range: IntRange,
    selectedValue: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    visibleCount: Int = 3,
    label: String? = null
) {
    AndroidView(
        modifier = modifier
            .fillMaxWidth()
            .height((48 * visibleCount).dp),
        factory = { context ->
            NumberPicker(context).apply {
                minValue = range.first
                maxValue = range.last
                value = selectedValue.coerceIn(range)
                wrapSelectorWheel = false
                setOnValueChangedListener { _, _, newVal ->
                    onValueChange(newVal)
                }
                // Show the label suffix on each displayed value
                if (label != null) {
                    setFormatter { "$it $label" }
                }
            }
        },
        update = { picker ->
            if (picker.value != selectedValue.coerceIn(range)) {
                picker.value = selectedValue.coerceIn(range)
            }
        }
    )
}
