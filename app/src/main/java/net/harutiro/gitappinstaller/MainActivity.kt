package net.harutiro.gitappinstaller

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import net.harutiro.gitappinstaller.ui.RepoAddScreen
import net.harutiro.gitappinstaller.ui.RepoListScreen
import net.harutiro.gitappinstaller.ui.SettingsScreen
import net.harutiro.gitappinstaller.ui.theme.GitAppInstallerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GitAppInstallerTheme { App() }
        }
    }
}

@Composable
private fun App() {
    val nav = rememberNavController()
    Surface(modifier = Modifier.fillMaxSize()) {
        NavHost(navController = nav, startDestination = "list") {
            composable("list") {
                RepoListScreen(
                    onAdd = { nav.navigate("add") },
                    onOpenSettings = { nav.navigate("settings") },
                )
            }
            composable("add") {
                RepoAddScreen(
                    onBack = { nav.popBackStack() },
                    onSaved = { nav.popBackStack() },
                )
            }
            composable("settings") {
                SettingsScreen(onBack = { nav.popBackStack() })
            }
        }
    }
}
