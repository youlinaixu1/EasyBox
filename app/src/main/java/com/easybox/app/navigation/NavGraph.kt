package com.easybox.app.navigation

import androidx.compose.runtime.*
import com.easybox.app.data.model.AppItem
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.easybox.app.data.local.AppDatabase
import com.easybox.app.data.local.PreferencesManager
import com.easybox.app.data.repository.AppRepository
import com.easybox.app.ui.chess.ChineseChessScreen
import com.easybox.app.ui.chess.ChineseChessViewModel
import com.easybox.app.ui.chess.InternationalChessScreen
import com.easybox.app.ui.chess.InternationalChessViewModel
import com.easybox.app.ui.home.HomeScreen
import com.easybox.app.ui.home.HomeViewModel


import com.easybox.app.ui.doudizhu.DouDiZhuScreen
import com.easybox.app.ui.doudizhu.DouDiZhuViewModel
import com.easybox.app.ui.plugin.PluginScreen
import com.easybox.app.ui.spinner.SpinnerScreen

object Routes {
    const val HOME = "home"
    const val CHINESE_CHESS = "chinese_chess"
    const val INTERNATIONAL_CHESS = "international_chess"
    const val DOU_DI_ZHU = "dou_di_zhu"
    const val SPINNER = "spinner"
    const val PLUGIN = "plugin/{pluginId}"
}

@Composable
fun EasyBoxNavGraph(navController: NavHostController) {
    val context = navController.context.applicationContext
    val database = AppDatabase.getInstance(context)
    val prefs = PreferencesManager(context)
    val repository = AppRepository(database.appItemDao(), prefs)

    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            val homeViewModel: HomeViewModel = viewModel(
                factory = HomeViewModel.Factory(repository)
            )
            HomeScreen(
                viewModel = homeViewModel,
                onAppClick = { appId ->
                    when (appId) {
                        "chinese_chess" -> navController.navigate(Routes.CHINESE_CHESS)
                        "international_chess" -> navController.navigate(Routes.INTERNATIONAL_CHESS)
                        "dou_di_zhu" -> navController.navigate(Routes.DOU_DI_ZHU)
                        "spinner" -> navController.navigate(Routes.SPINNER)
                    }
                },
                onPluginClick = { pluginId ->
                    navController.navigate("plugin/$pluginId")
                }
            )
        }

        composable(Routes.CHINESE_CHESS) {
            val viewModel = viewModel<ChineseChessViewModel>()
            ChineseChessScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.INTERNATIONAL_CHESS) {
            val viewModel = viewModel<InternationalChessViewModel>()
            InternationalChessScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.DOU_DI_ZHU) {
            val viewModel = viewModel<DouDiZhuViewModel>()
            DouDiZhuScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.PLUGIN) { backStackEntry ->
            val pluginId = backStackEntry.arguments?.getString("pluginId") ?: ""
            var item by remember { mutableStateOf<AppItem?>(null) }
            LaunchedEffect(pluginId) {
                item = database.appItemDao().getById(pluginId)
            }
            val appItem = item
            if (appItem != null) {
                val meta = parsePluginMeta(appItem.pluginPath)
                PluginScreen(
                    pluginName = appItem.name,
                    pluginUrl = meta.first,
                    pluginDesc = meta.second,
                    onBack = { navController.popBackStack() }
                )
            }
        }

        composable(Routes.SPINNER) {
            SpinnerScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}

private fun parsePluginMeta(json: String?): Pair<String, String> {
    if (json.isNullOrBlank()) return "" to ""
    return try {
        val url = json.substringAfter("\"url\":\"", "").substringBefore("\"")
        val desc = json.substringAfter("\"desc\":\"", "").substringBefore("\"")
        url to desc
    } catch (_: Exception) { "" to "" }
}
