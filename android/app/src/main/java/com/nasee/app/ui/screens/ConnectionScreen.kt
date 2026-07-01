package com.nasee.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nasee.app.NASeeApplication
import com.nasee.app.ui.components.LiquidGlassCard
import com.nasee.app.ui.theme.NASeePrimary
import com.nasee.app.ui.theme.NASeeSecondary
import com.nasee.app.ui.viewmodel.ConnectionViewModel

/**
 * 连接设置页。
 *
 * 用户输入服务端地址和密码，测试连接后保存配置。
 * 液态玻璃卡片背景 + 渐变遮罩。
 *
 * @param onConnected 连接成功回调
 */
@Composable
fun ConnectionScreen(
    onConnected: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val viewModel: ConnectionViewModel = viewModel {
        ConnectionViewModel(context.applicationContext as NASeeApplication)
    }
    val uiState by viewModel.uiState.collectAsState()
    val keyboard = LocalSoftwareKeyboardController.current

    // 连接成功后导航
    LaunchedEffect(uiState.isConnected) {
        if (uiState.isConnected) {
            onConnected()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1A1A2E),
                        Color(0xFF0A0A0F)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Logo 区域
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(NASeePrimary, NASeeSecondary)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Router,
                    contentDescription = "NASee",
                    tint = Color.White,
                    modifier = Modifier.size(40.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "NASee",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
            )
            Text(
                text = "连接到你的视频库",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 14.sp
            )

            Spacer(modifier = Modifier.height(32.dp))

            // 液态玻璃表单卡片
            LiquidGlassCard {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 服务端地址
                    OutlinedTextField(
                        value = uiState.address,
                        onValueChange = viewModel::updateAddress,
                        label = { Text("服务端地址") },
                        placeholder = { Text("http://192.168.1.100:8080") },
                        leadingIcon = { Icon(Icons.Default.Router, contentDescription = null) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Next
                        ),
                        colors = glassTextFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // 外网地址（可选）
                    OutlinedTextField(
                        value = uiState.externalAddress,
                        onValueChange = viewModel::updateExternalAddress,
                        label = { Text("外网地址（可选）") },
                        placeholder = { Text("FN Connect 外网地址") },
                        leadingIcon = { Icon(Icons.Default.Public, contentDescription = null) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Next
                        ),
                        colors = glassTextFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // 密码
                    var showPassword by remember { mutableStateOf(false) }
                    OutlinedTextField(
                        value = uiState.password,
                        onValueChange = viewModel::updatePassword,
                        label = { Text("访问密码") },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                        trailingIcon = {
                            IconButton(onClick = { showPassword = !showPassword }) {
                                Icon(
                                    imageVector = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (showPassword) "隐藏密码" else "显示密码"
                                )
                            }
                        },
                        singleLine = true,
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                keyboard?.hide()
                                viewModel.testConnection()
                            }
                        ),
                        colors = glassTextFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // 错误信息
                    if (uiState.error != null) {
                        Text(
                            text = uiState.error!!,
                            color = Color(0xFFFF5252),
                            fontSize = 13.sp,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // 连接成功提示
                    if (uiState.isConnectionSuccess) {
                        Text(
                            text = "✓ 连接测试成功",
                            color = Color(0xFF4CAF50),
                            fontSize = 13.sp,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // 测试连接按钮
                    Button(
                        onClick = {
                            keyboard?.hide()
                            viewModel.testConnection()
                        },
                        enabled = !uiState.isTesting,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NASeePrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (uiState.isTesting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("测试连接")
                        }
                    }

                    // 保存并连接按钮
                    OutlinedButton(
                        onClick = {
                            keyboard?.hide()
                            viewModel.saveAndConnect()
                        },
                        enabled = !uiState.isTesting,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color.White
                        )
                    ) {
                        Text(if (uiState.hasSavedConfig) "保存并进入" else "连接")
                    }
                }
            }
        }
    }
}

/**
 * 液态玻璃风格的 TextField 配色。
 */
@Composable
private fun glassTextFieldColors() = TextFieldDefaults.colors(
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    focusedContainerColor = Color.White.copy(alpha = 0.08f),
    unfocusedContainerColor = Color.White.copy(alpha = 0.05f),
    cursorColor = NASeePrimary,
    focusedIndicatorColor = NASeePrimary,
    unfocusedIndicatorColor = Color.White.copy(alpha = 0.3f),
    focusedLabelColor = NASeePrimary,
    unfocusedLabelColor = Color.White.copy(alpha = 0.6f),
    focusedLeadingIconColor = NASeePrimary,
    unfocusedLeadingIconColor = Color.White.copy(alpha = 0.5f),
    focusedTrailingIconColor = NASeePrimary,
    unfocusedTrailingIconColor = Color.White.copy(alpha = 0.5f)
)
