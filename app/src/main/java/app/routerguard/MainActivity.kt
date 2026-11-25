package app.routerguard

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import app.routerguard.ui.LoginScreen
import app.routerguard.ui.MainScreen
import app.routerguard.ui.RouterGuardTheme

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RouterGuardTheme {
                val state by vm.state.collectAsState()
                if (!state.configured) {
                    LoginScreen(state = state, onConnect = vm::connect)
                } else {
                    MainScreen(
                        state = state,
                        onRefresh = vm::refresh,
                        onToggleBlock = vm::setBlocked,
                        onRename = vm::setLabel,
                        onSignOut = vm::signOut,
                        onMessageShown = vm::messageShown,
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.refreshIfStale()
    }
}
