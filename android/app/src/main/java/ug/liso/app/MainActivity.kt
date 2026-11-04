package ug.liso.app

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import ug.liso.app.data.Api
import ug.liso.app.data.Session
import ug.liso.app.ui.screens.CallScreen
import ug.liso.app.ui.screens.CategoriesScreen
import ug.liso.app.ui.screens.DealScreen
import ug.liso.app.ui.screens.DealsScreen
import ug.liso.app.ui.screens.HomeScreen
import ug.liso.app.ui.screens.IdCheckScreen
import ug.liso.app.ui.screens.MeScreen
import ug.liso.app.ui.screens.SellerCallsScreen
import ug.liso.app.ui.screens.SellerProfileScreen
import ug.liso.app.ui.screens.SignInScreen
import ug.liso.app.ui.screens.WelcomeScreen
import ug.liso.app.ui.screens.WriteUpDealScreen
import ug.liso.app.ui.theme.LisoTheme

/** Bottom tabs. The agreed design has Home / Search / Live / Deals / Me; Search and Live come later. */
private data class Tab(val route: String, val label: Int, val icon: Int)

private val TABS = listOf(
    Tab("categories", R.string.tab_home, android.R.drawable.ic_menu_view),
    Tab("deals", R.string.tab_deals, android.R.drawable.ic_menu_agenda),
    Tab("me", R.string.tab_me, android.R.drawable.ic_menu_myplaces),
)

class MainActivity : ComponentActivity() {
    private val session by lazy { Session(this) }
    private val api by lazy { Api(session) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LisoTheme {
                val nav = rememberNavController()
                val lang = if (LocalConfiguration.current.locales[0].language == "lg") "lg" else "en"
                val entry by nav.currentBackStackEntryAsState()
                val route = entry?.destination?.route
                Scaffold(
                    bottomBar = {
                        if (TABS.any { it.route == route }) {
                            NavigationBar {
                                TABS.forEach { tab ->
                                    NavigationBarItem(
                                        selected = route == tab.route,
                                        onClick = {
                                            nav.navigate(tab.route) {
                                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        },
                                        icon = { Icon(painterResource(tab.icon), contentDescription = null) },
                                        label = { Text(stringResource(tab.label)) },
                                    )
                                }
                            }
                        }
                    },
                ) { pad ->
                    NavHost(nav, startDestination = "welcome", modifier = Modifier.padding(pad)) {
                        composable("welcome") { WelcomeScreen { nav.navigate("categories") } }
                        composable("categories") {
                            CategoriesScreen(api, lang) { id, name -> nav.navigate("home/$id/${Uri.encode(name)}") }
                        }
                        composable("home/{id}/{name}") { e ->
                            HomeScreen(api, e.arguments?.getString("id")!!, e.arguments?.getString("name").orEmpty()) {
                                nav.navigate("seller/$it")
                            }
                        }
                        composable("seller/{id}") { e ->
                            SellerProfileScreen(api, e.arguments?.getString("id")!!) { nav.navigate("call/$it") }
                        }
                        composable("call/{sellerId}") { e ->
                            CallScreen(
                                api = api,
                                sellerId = e.arguments?.getString("sellerId")!!,
                                signedIn = session.signedIn,
                                onNeedsSignIn = { nav.navigate("signin") },
                                onNeedsId = { nav.navigate("idcheck") },
                                // After the call the seller writes up the deal; the buyer finds it in Deals.
                                onDone = { nav.navigate("deals") { popUpTo("categories") } },
                            )
                        }
                        composable("signin") { SignInScreen(api) { nav.popBackStack() } }
                        composable("idcheck") { IdCheckScreen(api) { nav.popBackStack() } }
                        composable("deals") {
                            DealsScreen(api, session.signedIn, onSignIn = { nav.navigate("signin") }) { nav.navigate("deal/$it") }
                        }
                        composable("deal/{id}") { e -> DealScreen(api, e.arguments?.getString("id")!!) }
                        composable("me") {
                            MeScreen(api, session.signedIn, lang, onSignIn = { nav.navigate("signin") }) { nav.navigate("shop-calls/$it") }
                        }
                        composable("shop-calls/{sellerId}") { e ->
                            SellerCallsScreen(api, e.arguments?.getString("sellerId")!!) { callId -> nav.navigate("write-up/$callId") }
                        }
                        composable("write-up/{callId}") { e ->
                            WriteUpDealScreen(api, e.arguments?.getString("callId")!!) { dealId ->
                                nav.popBackStack()
                                if (dealId != null) nav.navigate("deal/$dealId")
                            }
                        }
                    }
                }
            }
        }
    }
}
