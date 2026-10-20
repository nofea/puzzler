package com.sheen.puzzler.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.sheen.puzzler.PuzzlerApplication

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as PuzzlerApplication
        setContent {
            PuzzlerTheme {
                Surface(Modifier.fillMaxSize()) {
                    PuzzlerNav(app)
                }
            }
        }
    }
}

@Composable
fun PuzzlerNav(app: PuzzlerApplication) {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = "map") {
        composable("map") {
            LevelMapScreen(
                levels = app.levels.levels,
                progressRepo = app.progress,
                onPlay = { id -> nav.navigate("game/$id") },
                onSettings = { nav.navigate("settings") },
            )
        }
        composable("game/{id}", arguments = listOf(navArgument("id") { type = NavType.IntType })) { entry ->
            val id = entry.arguments!!.getInt("id")
            val level = app.levels.level(id)
            val vm: GameViewModel = viewModel(
                key = "game_$id",
                factory = viewModelFactory { initializer { GameViewModel(level, app.progress) } },
            )
            GameScreen(
                vm = vm,
                progressRepo = app.progress,
                hasNext = id < app.levels.levels.size,
                onExit = { nav.popBackStack("map", inclusive = false) },
                onNext = {
                    nav.navigate("game/${id + 1}") { popUpTo("map") }
                },
            )
        }
        composable("settings") {
            SettingsScreen(progressRepo = app.progress, onBack = { nav.popBackStack() })
        }
    }
}
