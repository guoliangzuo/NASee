package com.nasee.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nasee.app.ui.theme.NASeePrimary

/**
 * 排序下拉菜单。
 *
 * 排序选项：名称 / 修改时间 / 文件大小 / 时长
 * 每项可切换升降序。
 *
 * @param currentSort 当前排序字段
 * @param currentOrder 当前排序方向
 * @param onSortSelected 选择回调
 * @param onDismiss 关闭回调
 */
@Composable
fun SortMenu(
    currentSort: String,
    currentOrder: String,
    onSortSelected: (field: String, order: String) -> Unit,
    onDismiss: () -> Unit
) {
    val sortOptions = listOf(
        SortOption("name", "名称", Icons.Default.TextFields),
        SortOption("mod_time", "修改时间", Icons.Default.Schedule),
        SortOption("file_size", "文件大小", Icons.Outlined.Storage),
        SortOption("duration", "时长", Icons.Default.Straighten)
    )

    DropdownMenu(
        expanded = true,
        onDismissRequest = onDismiss,
        modifier = Modifier.width(200.dp)
    ) {
        Text(
            text = "排序方式",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        sortOptions.forEach { option ->
            val isSelected = currentSort == option.field
            val newOrder = if (isSelected && currentOrder == "desc") "asc" else "desc"

            DropdownMenuItem(
                text = {
                    Column {
                        Text(
                            text = option.label,
                            color = if (isSelected) NASeePrimary else MaterialTheme.colorScheme.onSurface,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                        )
                        if (isSelected) {
                            Text(
                                text = if (currentOrder == "desc") "降序 ↓" else "升序 ↑",
                                style = MaterialTheme.typography.bodySmall,
                                color = NASeePrimary
                            )
                        }
                    }
                },
                leadingIcon = {
                    Icon(
                        imageVector = option.icon,
                        contentDescription = null,
                        tint = if (isSelected) NASeePrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingIcon = {
                    if (isSelected) {
                        Icon(
                            imageVector = if (currentOrder == "desc") Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                            contentDescription = null,
                            tint = NASeePrimary
                        )
                    }
                },
                onClick = { onSortSelected(option.field, newOrder) }
            )
        }
    }
}

/** 排序选项数据 */
private data class SortOption(
    val field: String,
    val label: String,
    val icon: ImageVector
)
