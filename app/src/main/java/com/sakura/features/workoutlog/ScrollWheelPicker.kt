package com.sakura.features.workoutlog

import android.os.Build
import android.view.ContextThemeWrapper
import android.widget.NumberPicker
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.sakura.R

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
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()

    AndroidView(
        modifier = modifier
            .fillMaxWidth()
            .height((48 * visibleCount).dp),
        factory = { context ->
            val themed = ContextThemeWrapper(context, R.style.ThemedNumberPicker)
            NumberPicker(themed).apply {
                minValue = range.first
                maxValue = range.last
                value = selectedValue.coerceIn(range)
                wrapSelectorWheel = false
                setOnValueChangedListener { _, _, newVal ->
                    onValueChange(newVal)
                }
                if (label != null) {
                    setFormatter { "$it $label" }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    setTextColor(textColor)
                }
            }
        },
        update = { picker ->
            if (picker.value != selectedValue.coerceIn(range)) {
                picker.value = selectedValue.coerceIn(range)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                picker.setTextColor(textColor)
            }
        }
    )
}
