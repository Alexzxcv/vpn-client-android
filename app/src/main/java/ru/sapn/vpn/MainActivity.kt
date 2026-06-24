package ru.sapn.vpn

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import ru.sapn.vpn.ui.theme.SapnTheme

class MainActivity : ComponentActivity() {

    private val container by lazy { (application as SapnApp).container }

    private val authViewModel: AuthViewModel by viewModels {
        AuthViewModel.Factory(container.authRepository)
    }

    private val connectionViewModel: ConnectionViewModel by viewModels {
        ConnectionViewModel.Factory(application, container.vpnRepository)
    }

    private val accountViewModel: AccountViewModel by viewModels {
        AccountViewModel.Factory(container.accountRepository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SapnTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    val loggedIn by authViewModel.isLoggedIn.collectAsStateWithLifecycle()
                    var showAccount by remember { mutableStateOf(false) }

                    when {
                        !loggedIn -> LoginScreen(viewModel = authViewModel)
                        showAccount -> AccountScreen(
                            viewModel = accountViewModel,
                            onBack = { showAccount = false },
                        )
                        else -> ConnectionScreen(
                            viewModel = connectionViewModel,
                            onLogout = authViewModel::logout,
                            onOpenAccount = { showAccount = true },
                        )
                    }
                }
            }
        }
    }
}
