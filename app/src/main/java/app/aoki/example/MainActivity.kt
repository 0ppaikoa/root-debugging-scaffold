package app.aoki.example

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import androidx.compose.runtime.LaunchedEffect
import app.aoki.example.debug.Journal
import app.aoki.example.module.ModuleService
import app.aoki.example.ui.detail.DetailScreen
import app.aoki.example.ui.export.ExportDemoScreen
import app.aoki.example.ui.home.HomeScreen
import app.aoki.example.ui.root.RootDemoScreen
import app.aoki.example.ui.theme.RootDebugTheme
import kotlinx.serialization.Serializable

@Serializable
data object HomeKey : NavKey

@Serializable
data class DetailKey(val itemId: String) : NavKey

@Serializable
data object ExportKey : NavKey

@Serializable
data object RootDemoKey : NavKey

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ModuleService.register()
        setContent {
            RootDebugTheme {
                val notificationPermission = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission(),
                ) { granted ->
                    Journal.append(this@MainActivity, "notification permission: " + granted)
                }
                LaunchedEffect(Unit) {
                    if (Build.VERSION.SDK_INT >= 33 &&
                        checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
                        PackageManager.PERMISSION_GRANTED
                    ) {
                        notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
                val backStack = rememberNavBackStack(HomeKey)
                NavDisplay(
                    backStack = backStack,
                    onBack = { backStack.removeLastOrNull() },
                    transitionSpec = {
                        slideInHorizontally(initialOffsetX = { it }) togetherWith
                            slideOutHorizontally(targetOffsetX = { -it })
                    },
                    popTransitionSpec = {
                        slideInHorizontally(initialOffsetX = { -it }) togetherWith
                            slideOutHorizontally(targetOffsetX = { it })
                    },
                    predictivePopTransitionSpec = { _ ->
                        slideInHorizontally(initialOffsetX = { -it }) togetherWith
                            slideOutHorizontally(targetOffsetX = { it })
                    },
                    entryProvider = entryProvider {
                        entry<HomeKey> {
                            HomeScreen(
                                onItemClick = { id ->
                                    when (id) {
                                        "debug-export" -> backStack.add(ExportKey)
                                        "root-demo" -> backStack.add(RootDemoKey)
                                        else -> backStack.add(DetailKey(id))
                                    }
                                },
                            )
                        }
                        entry<DetailKey> { key ->
                            DetailScreen(
                                itemId = key.itemId,
                                onBack = { backStack.removeLastOrNull() },
                            )
                        }
                        entry<ExportKey> {
                            ExportDemoScreen(
                                onBack = { backStack.removeLastOrNull() },
                            )
                        }
                        entry<RootDemoKey> {
                            RootDemoScreen(
                                onBack = { backStack.removeLastOrNull() },
                            )
                        }
                    },
                )
            }
        }
    }
}
