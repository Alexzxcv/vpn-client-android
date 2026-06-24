package ru.sapn.vpn.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.sapn.vpn.ui.components.Eyebrow
import ru.sapn.vpn.ui.theme.Sapn

@Composable
fun LoginScreen(viewModel: AuthViewModel) {
    val state by viewModel.ui.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .imePadding()
            .padding(horizontal = 28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("SAPN", style = MaterialTheme.typography.headlineMedium, color = Sapn.Frost)
        Spacer(Modifier.height(6.dp))
        Eyebrow("Приватный доступ")
        Spacer(Modifier.height(40.dp))

        Field(state.login, viewModel::onLoginChange, "Email или логин")
        Spacer(Modifier.height(12.dp))
        Field(state.password, viewModel::onPasswordChange, "Пароль", password = true)

        state.error?.let {
            Spacer(Modifier.height(12.dp))
            Text(
                it,
                color = Sapn.Alert,
                style = MaterialTheme.typography.bodySmall.copy(color = Sapn.Alert),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(28.dp))
        Button(
            onClick = viewModel::login,
            enabled = !state.loading,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Sapn.Ion, contentColor = Sapn.Void),
        ) {
            if (state.loading) {
                CircularProgressIndicator(color = Sapn.Void, strokeWidth = 2.dp, modifier = Modifier.height(20.dp))
            } else {
                Text("Войти", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun Field(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    password: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        modifier = Modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Sapn.Ion,
            unfocusedBorderColor = Sapn.Hairline,
            focusedLabelColor = Sapn.Ion,
            unfocusedLabelColor = Sapn.Mute,
            focusedTextColor = Sapn.Frost,
            unfocusedTextColor = Sapn.Frost,
            cursorColor = Sapn.Ion,
        ),
    )
}
