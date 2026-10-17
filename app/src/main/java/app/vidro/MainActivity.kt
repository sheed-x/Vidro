package app.vidro

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import app.vidro.ui.VidroRoot
import app.vidro.ui.theme.VidroTheme

// AppCompatActivity (not plain ComponentActivity) so the in-app language choice applies on Android 7-12 too.
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VidroTheme {
                VidroRoot()
            }
        }
    }
}
