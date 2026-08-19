import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController
import com.rafambn.graphitesurface.sample.App

fun MainViewController(): UIViewController = ComposeUIViewController { App() }
