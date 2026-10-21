package com.sheen.puzzler.ui

import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.sheen.puzzler.PuzzlerApplication
import com.sheen.puzzler.ui.rules.RulesScreen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    @Volatile private var settingsLoaded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        val app = application as PuzzlerApplication

        // Hold the splash until settings are known, so the intro honors mute and vibration from its first sound.
        lifecycleScope.launch {
            app.progress.progress.first()
            settingsLoaded = true
        }
        splash.setKeepOnScreenCondition { !settingsLoaded }

        // Cold start only, and not when the user has turned system animations off.
        val playIntro = !app.introShown && animationsEnabled()
        var showIntro by mutableStateOf(playIntro)
        var anchor by mutableStateOf<OrbAnchor?>(null)
        if (playIntro) {
            app.introShown = true
            splash.setOnExitAnimationListener { view ->
                // The system shows an icon-less splash when the app isn't opened from the launcher (iconView throws
                // then); the orb starts where the icon would have been, at the splash's center.
                val icon = runCatching { view.iconView }.getOrNull() ?: view.view
                val at = IntArray(2).also(icon::getLocationInWindow)
                // A splash icon without a background is drawn 288dp across, larger than the icon view that hosts it,
                // and splash_orb.xml puts the orb at radius 26 of its 108-unit canvas.
                anchor = OrbAnchor(
                    center = Offset(at[0] + icon.width / 2f, at[1] + icon.height / 2f),
                    radius = SPLASH_ICON_DP * resources.displayMetrics.density * 26f / 108f,
                )
                view.remove()
            }
        }

        setContent {
            PuzzlerTheme {
                Surface(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize()) {
                        // Android 15 draws apps edge to edge, so keep the screens clear of the system bars. The intro
                        // stays full-screen so it lines up with the splash.
                        Box(Modifier.fillMaxSize().safeDrawingPadding()) { PuzzlerNav(app) }
                        if (showIntro) IntroOverlay(anchor, app.progress, onDone = { showIntro = false })
                    }
                }
            }
        }
    }

    private companion object {
        const val SPLASH_ICON_DP = 288f
    }

    private fun animationsEnabled() =
        Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
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
                onRules = { nav.navigate("rules") },
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
                onRules = { nav.navigate("rules") },
            )
        }
        composable("settings") {
            SettingsScreen(progressRepo = app.progress, onBack = { nav.popBackStack() }, onRules = { nav.navigate("rules") })
        }
        composable("rules") {
            RulesScreen(onBack = { nav.popBackStack() })
        }
    }
}
