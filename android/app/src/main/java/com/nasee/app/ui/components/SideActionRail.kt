package com.nasee.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nasee.app.ui.theme.GlassWhite

/**
 * 右侧操作栏。
 *
 * 垂直排列操作按钮：
 * - 点赞按钮
 * - 文件夹按钮
 * - 排序按钮
 * - 点赞列表按钮
 * - 断开连接按钮
 *
 * 液态玻璃圆形背景。
 *
 * @param isLiked 当前视频是否已点赞
 * @param likedCount 点赞总数
 * @param onLikeClick 点赞回调
 * @param onFolderClick 文件夹回调
 * @param onSortClick 排序回调
 * @param onLikedVideosClick 点赞列表回调
 * @param onDisconnect 断开连接回调
 * @param modifier 修饰符
 */
@Composable
fun SideActionRail(
    isLiked: Boolean,
    likedCount: Int,
    onLikeClick: () -> Unit,
    onFolderClick: () -> Unit,
    onSortClick: () -> Unit,
    onLikedVideosClick: () -> Unit,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .padding(end = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // 点赞
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            GlassIconButton(onClick = onLikeClick) {
                LikeButton(isLiked = isLiked, onClick = onLikeClick)
            }
            Text(
                text = if (isLiked) "已赞" else "点赞",
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 10.sp
            )
        }

        // 文件夹
        RailItem(
            icon = Icons.Default.Folder,
            label = "文件夹",
            onClick = onFolderClick
        )

        // 排序
        RailItem(
            icon = Icons.Default.Sort,
            label = "排序",
            onClick = onSortClick
        )

        // 点赞列表
        RailItem(
            icon = Icons.Outlined.PlayCircleOutline,
            label = "收藏",
            onClick = onLikedVideosClick
        )

        Spacer(modifier = Modifier.height(8.dp))

        // 断开连接
        RailItem(
            icon = Icons.Default.Logout,
            label = "断开",
            onClick = onDisconnect,
            tint = Color(0xFFFF5252)
        )
    }
}

/**
 * 单个操作栏按钮项。
 */
@Composable
private fun RailItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: Color = Color.White
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        GlassIconButton(onClick = onClick) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(26.dp)
            )
        }
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 10.sp
        )
    }
}

/**
 * 液态玻璃圆形按钮。
 */
@Composable
private fun GlassIconButton(
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape),
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = GlassWhite
        )
    ) {
        content()
    }
}
