package com.nasee.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nasee.app.data.model.FolderNode
import com.nasee.app.ui.theme.GlassWhite
import com.nasee.app.ui.theme.NASeePrimary

/**
 * 文件夹树 BottomSheet。
 *
 * 递归渲染文件夹树，支持展开/折叠。
 * 顶部固定"全部视频"选项，选中高亮。
 *
 * @param folders 文件夹列表
 * @param selectedFolder 当前选中的文件夹路径
 * @param onSelect 选择回调
 * @param onDismiss 关闭回调
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderBottomSheet(
    folders: List<FolderNode>,
    selectedFolder: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val expandedMap = remember { mutableStateMapOf<String, Boolean>() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "选择文件夹",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // 全部视频
                item {
                    FolderItem(
                        name = "全部视频",
                        path = "",
                        count = folders.sumOf { it.count },
                        isSelected = selectedFolder.isBlank(),
                        onClick = { onSelect("") }
                    )
                }

                // 递归渲染文件夹树
                folders.forEach { node ->
                    item {
                        FolderTreeItem(
                            node = node,
                            depth = 0,
                            selectedFolder = selectedFolder,
                            expandedMap = expandedMap,
                            onSelect = onSelect
                        )
                    }
                }
            }
        }
    }
}

/**
 * 递归渲染文件夹树节点。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FolderTreeItem(
    node: FolderNode,
    depth: Int,
    selectedFolder: String,
    expandedMap: MutableMap<String, Boolean>,
    onSelect: (String) -> Unit
) {
    val isExpanded = expandedMap[node.path] ?: false
    val hasChildren = node.children.isNotEmpty()
    val isSelected = selectedFolder == node.path

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onSelect(node.path) }
                .padding(start = (depth * 20).dp, top = 8.dp, bottom = 8.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 展开/折叠图标
            if (hasChildren) {
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                    contentDescription = if (isExpanded) "折叠" else "展开",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(24.dp)
                        .clickable { expandedMap[node.path] = !isExpanded }
                )
            } else {
                Spacer(modifier = Modifier.width(24.dp))
            }

            Spacer(modifier = Modifier.width(8.dp))

            // 文件夹图标
            Icon(
                imageVector = Icons.Default.Folder,
                contentDescription = null,
                tint = if (isSelected) NASeePrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            // 文件夹名称
            Text(
                text = node.name,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isSelected) NASeePrimary else MaterialTheme.colorScheme.onSurface,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier.weight(1f)
            )

            // 视频数量
            Text(
                text = "${node.count}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // 递归渲染子文件夹
        if (isExpanded && hasChildren) {
            node.children.forEach { child ->
                FolderTreeItem(
                    node = child,
                    depth = depth + 1,
                    selectedFolder = selectedFolder,
                    expandedMap = expandedMap,
                    onSelect = onSelect
                )
            }
        }
    }
}

/**
 * 简单文件夹项（非树形）。
 */
@Composable
private fun FolderItem(
    name: String,
    path: String,
    count: Int,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Outlined.VideoLibrary,
            contentDescription = null,
            tint = if (isSelected) NASeePrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.bodyLarge,
            color = if (isSelected) NASeePrimary else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "$count",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
