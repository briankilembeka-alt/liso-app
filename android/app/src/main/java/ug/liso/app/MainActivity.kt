package ug.liso.app

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import ug.liso.app.data.Api
import ug.liso.app.ui.screens.CategoriesScreen
import ug.liso.app.ui.screens.HomeScreen
import ug.liso.app.ui.screens.SellerProfileScreen
import ug.liso.app.ui.screens.WelcomeScreen
import ug.liso.app.ui.theme.LisoTheme

class MainActivity : ComponentActivity() {
    private val api = Api()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LisoTheme {
                val nav = rememberNavController()
                val lang = if (LocalConfiguration.current.locales[0].language == "lg") "lg" else "en"
                Scaffold { pad ->
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
                        composable("call/{sellerId}") {
                            // Next step of milestone 1: sign-in, ID check, then the IVS Real-Time call screen.
                            Box(Modifier.fillMaxSize(), Alignment.Center) { Text("Video call coming next") }
                        }
                    }
                }
            }
        }
    }
}
