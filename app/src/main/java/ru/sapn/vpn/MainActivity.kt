package ru.sapn.vpn

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.sapn.vpn.ui.account.AccountScreen
import ru.sapn.vpn.ui.account.AccountViewModel
import ru.sapn.vpn.ui.auth.AuthViewModel
import ru.sapn.vpn.ui.auth.LoginScreen
import ru.sapn.vpn.ui.connection.ConnectionScreen
import ru.sapn.vpn.ui.connection.ConnectionViewModel
import ru.sapn.vpn.ui.settings.SettingsScreen
import ru.sapn.vpn.ui.settings.SettingsViewModel
import ru.sapn.vpn.ui.theme.Sapn
import ru.sapn.vpn.ui.theme.SapnTheme

class MainActivity : ComponentActivity() {

    private val container by lazy { (application as SapnApp).container }

    private val authViewModel: AuthViewModel by viewModels {
        AuthViewModel.Factory(container.authRepository)
    }

    private val connectionViewModel: ConnectionViewModel by viewModels {
        ConnectionViewModel.Factory(application, container.vpnRepository, container.updateRepository)
    }

    private val accountViewModel: AccountViewModel by viewModels {
        AccountViewModel.Factory(container.accountRepository)
    }

    private val settingsViewModel: SettingsViewModel by viewModels {
        SettingsViewModel.Factory(container.settingsStore)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Тёмные системные бары всегда (приложение всегда тёмное) — светлые иконки
        // статус-бара/времени на тёмном фоне, прозрачный скрим под контентом.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent {
            SapnTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    val loggedIn by authViewModel.isLoggedIn.collectAsStateWithLifecycle()

                    if (!loggedIn) {
                        // Логин — на весь экран, без нижней навигации.
                        LoginScreen(viewModel = authViewModel)
                    } else {
                        var tab by remember { mutableStateOf(Tab.Connect) }
                        Scaffold(
                            containerColor = MaterialTheme.colorScheme.background,
                            bottomBar = { BottomNav(current = tab, onSelect = { tab = it }) },
                        ) { inner ->
                            Box(Modifier.padding(inner)) {
                                when (tab) {
                                    Tab.Connect -> ConnectionScreen(viewModel = connectionViewModel)
                                    Tab.Account -> AccountScreen(
                                        viewModel = accountViewModel,
                                        onLogout = authViewModel::logout,
                                    )
                                    Tab.Settings -> SettingsScreen(viewModel = settingsViewModel)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private enum class Tab { Connect, Account, Settings }

@Composable
private fun BottomNav(current: Tab, onSelect: (Tab) -> Unit) {
    NavigationBar(
        containerColor = Sapn.Elevated,
        contentColor = Sapn.Mute,
    ) {
        val colors = NavigationBarItemDefaults.colors(
            selectedIconColor = Sapn.Ion,
            selectedTextColor = Sapn.Ion,
            indicatorColor = Sapn.Ion.copy(alpha = 0.14f),
            unselectedIconColor = Sapn.Faint,
            unselectedTextColor = Sapn.Faint,
        )
        NavigationBarItem(
            selected = current == Tab.Connect,
            onClick = { onSelect(Tab.Connect) },
            icon = { Icon(Icons.Outlined.Bolt, contentDescription = null) },
            label = { Text("Подключение") },
            colors = colors,
        )
        NavigationBarItem(
            selected = current == Tab.Account,
            onClick = { onSelect(Tab.Account) },
            icon = { Icon(Icons.Outlined.Person, contentDescription = null) },
            label = { Text("Аккаунт") },
            colors = colors,
        )
        NavigationBarItem(
            selected = current == Tab.Settings,
            onClick = { onSelect(Tab.Settings) },
            icon = { Icon(Icons.Outlined.Tune, contentDescription = null) },
            label = { Text("Настройки") },
            colors = colors,
        )
    }
}
