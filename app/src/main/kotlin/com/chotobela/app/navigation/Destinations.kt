package com.chotobela.app.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.ui.graphics.vector.ImageVector
import com.chotobela.app.R

enum class Destination(
    val route: String,
    @StringRes val labelRes: Int,
    val icon: ImageVector
) {
    HOME("home", R.string.nav_home, Icons.Filled.Home),
    LIBRARY("library", R.string.nav_library, Icons.AutoMirrored.Filled.LibraryBooks),
    STORE("store", R.string.nav_store, Icons.Filled.Storefront),
    DOWNLOADS("downloads", R.string.nav_downloads, Icons.Filled.CloudDownload),
    PROFILE("profile", R.string.nav_profile, Icons.Filled.Person);

    companion object {
        fun fromRoute(route: String?): Destination? =
            entries.firstOrNull { it.route == route }
    }
}

object Routes {
    const val ONBOARDING = "onboarding"
    const val GAME_DETAILS = "game/{gameId}"
    const val PLAYER = "player/{gameId}"
    const val SETTINGS = "settings"

    fun gameDetails(gameId: String) = "game/$gameId"
    fun player(gameId: String) = "player/$gameId"
}
