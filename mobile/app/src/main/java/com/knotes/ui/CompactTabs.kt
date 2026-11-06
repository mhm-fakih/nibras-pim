package com.knotes.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * A short tab row: 32dp high instead of Material's 48dp, and each tab only as wide as its label
 * (Material's scrollable row makes every tab at least 90dp). [fill] spreads the tabs across the
 * width; otherwise they sit side by side and scroll when they don't fit.
 */
@Composable
fun CompactTabs(
    labels: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    fill: Boolean = false,
) {
    val primary = MaterialTheme.colorScheme.primary
    Column(modifier.fillMaxWidth()) {
        Row(
            modifier = if (fill) Modifier.fillMaxWidth()
            else Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp),
        ) {
            labels.forEachIndexed { i, label ->
                val on = i == selected
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    // Intrinsic width, so the underline below can fill the label's width.
                    modifier = (if (fill) Modifier.weight(1f) else Modifier.width(IntrinsicSize.Max))
                        .height(32.dp)
                        .clickable { onSelect(i) },
                ) {
                    Box(Modifier.weight(1f).padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                        Text(
                            label,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (on) primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                    Box(
                        Modifier
                            .height(2.dp)
                            .fillMaxWidth()
                            .background(if (on) primary else Color.Transparent),
                    )
                }
            }
        }
        HorizontalDivider()
    }
}
