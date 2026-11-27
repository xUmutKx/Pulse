package com.umutk.pulse.watch

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.umutk.pulse.watch.apps.Apps
import com.umutk.pulse.watch.apps.Prefs

/** Pulse Watch: the home (time in the middle, quick controls to the left, the apps to the right) and every app; a swipe to the right goes back. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.load(this)
        setContent { WatchApp() }
    }
}

@Composable
private fun WatchApp() {
    val nav = rememberSwipeDismissableNavController()
    Apps.open = { route -> nav.navigate(route) }
    SwipeDismissableNavHost(navController = nav, startDestination = "home") {
        composable("home") { HomeScreen(onOpen = { route -> nav.navigate(route) }) }
        Apps.all.forEach { app -> composable(app.route) { app.screen() } }
    }
}
