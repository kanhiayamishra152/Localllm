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

sealed class Screen(val route: String) {
    data object Chat : Screen("chat?conversationId={conversationId}") {
        fun createRoute(conversationId: String? = null) =
            if (conversationId != null) "chat?conversationId=$conversationId"
            else "chat"
    }
    data object Models : Screen("models")
    data object Settings : Screen("settings")
}

@Composable
fun NavGraph() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "chat"
    ) {
        composable(
            route = "chat?conversationId={conversationId}",
            arguments = listOf(
                navArgument("conversationId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) {
            ChatScreen(
                navController = navController,
                conversationId = it.arguments?.getString("conversationId")
            )
        }

        composable(Screen.Models.route) {
            ModelBrowserScreen(navController = navController)
        }

        composable(Screen.Settings.route) {
            SettingsScreen(navController = navController)
        }
    }
}
