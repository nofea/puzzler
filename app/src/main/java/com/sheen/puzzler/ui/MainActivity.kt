package com.sheen.puzzler.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.sheen.puzzler.data.Progress
import com.sheen.puzzler.ui.rules.RulesScreen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    @Volatile private var settingsLoaded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        val app = application as PuzzlerApplication

        // Hold the splash until settings are known, so the intro honors mute and vibration from its first sound. Progress
        // from an older version is wiped first.
        lifecycleScope.launch {
            app.progress.migrate()
            app.progress.progress.first()
            settingsLoaded = true
        }
        splash.setKeepOnScreenCondition { !settingsLoaded }

        // Cold start only, and not when the user has turned system animations off.
        val playIntro = !app.introShown && animationsEnabled(this)
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
                anchor =
                    OrbAnchor(
                        center = Offset(at[0] + (icon.width / 2f), at[1] + (icon.height / 2f)),
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
}

@Composable
fun PuzzlerNav(app: PuzzlerApplication) {
    val nav = rememberNavController()
    val levels = app.levels.levels
    val maxStars = levels.size * 3
    val scope = rememberCoroutineScope()
    var finale by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        NavHost(navController = nav, startDestination = "home") {
            composable("home") {
                HomeScreen(
                    progressRepo = app.progress,
                    maxStars = maxStars,
                    onCampaign = { nav.navigate("map") },
                    onEndless = { nav.navigate("endless") },
                    onRules = { nav.navigate("rules") },
                    onSettings = { nav.navigate("settings") },
                    onAbout = { nav.navigate("about") },
                )
            }
            composable("map") {
                LevelMapScreen(
                    levels = levels,
                    progressRepo = app.progress,
                    onPlay = { id -> nav.navigate("game/$id") },
                    onHome = { nav.popBackStack("home", inclusive = false) },
                    onRules = { nav.navigate("rules") },
                )
            }
            composable("game/{id}", arguments = listOf(navArgument("id") { type = NavType.IntType })) { entry ->
                val id = entry.arguments!!.getInt("id")
                val level = app.levels.level(id)
                val vm: GameViewModel =
                    viewModel(
                        key = "game_$id",
                        factory = viewModelFactory { initializer { GameViewModel(level, app.progress) } },
                    )
                GameScreen(
                    vm = vm,
                    progressRepo = app.progress,
                    hasNext = id < levels.size,
                    isLastLevel = id == levels.size,
                    onExit = { nav.popBackStack("map", inclusive = false) },
                    onNext = { nav.navigate("game/${id + 1}") { popUpTo("map") } },
                    onRules = { nav.navigate("rules") },
                    onFinale = { finale = true },
                )
            }
            composable("endless") {
                val vm: EndlessViewModel =
                    viewModel(key = "endless", factory = viewModelFactory { initializer { EndlessViewModel(app.progress) } })
                EndlessScreen(
                    vm = vm,
                    progressRepo = app.progress,
                    onExit = { nav.popBackStack("home", inclusive = false) },
                    onRules = { nav.navigate("rules") },
                )
            }
            composable("settings") {
                SettingsScreen(
                    progressRepo = app.progress,
                    onBack = { nav.popBackStack() },
                    onRules = { nav.navigate("rules") },
                    onReplayFinale = { finale = true },
                )
            }
            composable("about") {
                AboutScreen(onBack = { nav.popBackStack() })
            }
            composable("rules") {
                RulesScreen(onBack = { nav.popBackStack() })
            }
        }

        // All levels complete: over everything, then on to the level map.
        if (finale) {
            val progress by app.progress.progress.collectAsState(initial = Progress())
            val fx = rememberFx(app.progress)
            val done = {
                finale = false
                scope.launch { app.progress.markFinaleSeen() }
                nav.navigate("map") { popUpTo("home") }
            }
            BackHandler(onBack = done)
            FinaleOverlay(progress.totalStars, maxStars, fx, onDone = done)
        }
    }
}
