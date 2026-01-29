package de.mm20.launcher2.ui.launcher.widgets.snappoint

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.widgets.SnapPointWidget

@Composable
fun SnapPointWidget(widget: SnapPointWidget) {
    // Snap point needs minimal height for layout positioning.
    // It's essentially invisible but needs to exist in the layout tree
    // for onPlaced to be called and position tracking to work.
    Spacer(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
    )
}
