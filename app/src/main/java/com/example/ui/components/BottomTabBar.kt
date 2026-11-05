package com.example.ui.components

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddBox
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.outlined.AddBox
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import com.example.model.EditorTab

@Composable
fun BottomTabBar(
    activeTab: EditorTab,
    onSelectTab: (EditorTab) -> Unit
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        EditorTab.entries.forEach { tab ->
            val selected = activeTab == tab
            val icon = when (tab) {
                EditorTab.HOME -> if (selected) Icons.Filled.Home else Icons.Outlined.Home
                EditorTab.INSERT -> if (selected) Icons.Filled.AddBox else Icons.Outlined.AddBox
                EditorTab.LAYOUT -> if (selected) Icons.Filled.Article else Icons.Outlined.Article
                EditorTab.REVIEW -> if (selected) Icons.Filled.Visibility else Icons.Outlined.Visibility
            }
            NavigationBarItem(
                selected = selected,
                onClick = { onSelectTab(tab) },
                icon = {
                    Icon(
                        imageVector = icon,
                        contentDescription = tab.titleAr
                    )
                },
                label = {
                    Text(
                        text = tab.titleAr,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                modifier = Modifier.testTag("tab_${tab.name.lowercase()}")
            )
        }
    }
}
