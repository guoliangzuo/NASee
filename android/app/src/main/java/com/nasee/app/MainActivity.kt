package com.nasee.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.nasee.app.ui.screens.ConnectionScreen
import com.nasee.app.ui.screens.LikedVideosScreen
import com.nasee.app.ui.screens.PlayerScreen
import com.nasee.app.ui.theme.NASeeTheme
import com.nasee.app.ui.viewmodel.PlayerViewModel

/**
 * 单 Activity 入口。
 *
 * 使用 Jetpack Compose Navigation 管理页面路由：
 * - [Routes.CONNECTION] → 连接设置页
 * - [Routes.PLAYER] → 播放页（核心界面）
 * - [Routes.LIKED] → 收藏列表页
 *
 * 启动时检查是否有已保存的配置，决定起始路由。
 *
 * 生命周期监听：
 * - 当 App 进入后台时立即暂停播放
 * - 当 App 回到前台时不自动恢复播放
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as NASeeApplication
        val hasConfig = app.appContainer.encryptedConfigStore.hasConfig()

        setContent {
            NASeeTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    NASeeApp(startDestination = if (hasConfig) Routes.PLAYER else Routes.CONNECTION)
                }
            }
        }
    }
}

/** 路由常量 */
object Routes {
    const val CONNECTION = "connection"
    const val PLAYER = "player"
    const val LIKED = "liked"
}

/**
 * 应用根 Composable，管理导航图。
 */
@Composable
fun NASeeApp(startDestination: String) {
    val navController = rememberNavController()
    
    // 生命周期观察者：用于监听 App 前后台切换
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    var isInBackground by remember { mutableStateOf(false) }

    // 监听生命周期事件
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    // App 进入后台，暂停播放
                    isInBackground = true
                    // 通过 ViewModel 暂停播放
                    try {
                        val viewModel = viewModel<PlayerViewModel>(
                            viewModelStoreOwner = navController.getBackStackEntry(Routes.PLAYER)
                        )
                        viewModel.pauseCurrentPlayer()
                    } catch (e: Exception) {
                        // 如果 PlayerViewModel 不存在（不在播放页），忽略
                    }
                }
                Lifecycle.Event.ON_RESUME -> {
                    // App 回到前台，不自动恢复播放
                    isInBackground = false
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = Modifier.fillMaxSize(),
        enterTransition = {
            slideInHorizontally(animationSpec = tween(300)) { it } + fadeIn(tween(300))
        },
        exitTransition = {
            slideOutHorizontally(animationSpec = tween(300)) { -it } + fadeOut(tween(300))
        },
        popEnterTransition = {
            slideInHorizontally(animationSpec = tween(300)) { -it } + fadeIn(tween(300))
        },
        popExitTransition = {
            slideOutHorizontally(animationSpec = tween(300)) { it } + fadeOut(tween(300))
        }
    ) {
        composable(Routes.CONNECTION) {
            ConnectionScreen(
                onConnected = {
                    navController.navigate(Routes.PLAYER) {
                        popUpTo(Routes.CONNECTION) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.PLAYER) {
            PlayerScreen(
                onNavigateToLiked = { navController.navigate(Routes.LIKED) },
                onDisconnect = {
                    navController.navigate(Routes.CONNECTION) {
                        popUpTo(Routes.PLAYER) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.LIKED) {
            LikedVideosScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}
