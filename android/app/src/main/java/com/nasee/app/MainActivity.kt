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
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.nasee.app.ui.screens.ConnectionScreen
import com.nasee.app.ui.screens.LikedVideosScreen
import com.nasee.app.ui.screens.PlayerScreen
import com.nasee.app.ui.theme.NASeeTheme

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
 * 后台暂停播放功能已在 PlayerScreen.kt 中实现。
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
