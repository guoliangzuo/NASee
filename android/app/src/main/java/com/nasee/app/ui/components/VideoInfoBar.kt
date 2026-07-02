package com.nasee.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nasee.app.ui.theme.GlassWhite

/**
 * 底部视频信息栏。
 *
 * 显示视频标题和文件路径，液态玻璃背景效果。
 *
 * @param title 视频标题
 * @param folder 文件路径
 * @param modifier 修饰符
 */
@Composable
fun VideoInfoBar(
    title: String,
    folder: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.Black.copy(alpha = 0.3f),
                        Color.Black.copy(alpha = 0.7f)
                    )
                )
            )
            .padding(horizontal = 16.dp)
            .padding(bottom = 16.dp)
            .padding(top = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    GlassWhite,
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // 标题
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            if (folder.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = folder,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
