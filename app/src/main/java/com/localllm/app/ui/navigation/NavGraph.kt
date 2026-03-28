package com.localllm.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.localllm.app.ui.screens.chat.ChatScreen
import com.localllm.app.ui.screens.models.ModelBrowserScreen
import com.localllm.app.ui.screens.settings.SettingsScreen

@Composable
fun NavGraph() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "chat") {
        composable(
            route = "chat?conversationId={conversationId}",
            arguments = listOf(navArgument("conversationId") {
                type = NavType.StringType; nullable = true; defaultValue = null
            })
        ) {
            ChatScreen(navController = navController)
        }
        composable("models") {
            ModelBrowserScreen(navController = navController)
        }
        composable("settings") {
            SettingsScreen(navController = navController)
        }
    }
}
