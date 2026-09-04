package com.nexoratech.markets.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.nexoratech.markets.auth.AccountViewModel
import com.nexoratech.markets.ui.components.NexoraPrimaryButton
import com.nexoratech.markets.ui.theme.Sell400
import com.nexoratech.markets.ui.theme.Teal400
import com.nexoratech.markets.ui.theme.TextSecondary

/**
 * Create account — the front door of the app. Client-side validation
 * mirrors the server policy (8+ chars, letter + number) so users never
 * get a rejection after submit that we could have caught at the field.
 */
@Composable
fun CreateAccountScreen(
    viewModel: AccountViewModel,
    onBack: () -> Unit,
) {
    var displayName by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var busy by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(16.dp))
        TextButton(onClick = onBack) { Text("← Back", color = TextSecondary) }
        Spacer(Modifier.height(12.dp))

        Text(
            "Create account",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Your watchlists and analysis sync through Nexora Cloud.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
        )
        Spacer(Modifier.height(28.dp))

        AuthField(
            label = "Display name",
            value = displayName,
            onValueChange = { displayName = it },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        )
        Spacer(Modifier.height(14.dp))
        AuthField(
            label = "Email",
            value = email,
            onValueChange = { email = it },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next,
            ),
        )
        Spacer(Modifier.height(14.dp))
        AuthField(
            label = "Password",
            value = password,
            onValueChange = { password = it },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Next,
            ),
            supporting = "8+ characters, at least one letter and one number",
        )
        Spacer(Modifier.height(14.dp))
        AuthField(
            label = "Confirm password",
            value = confirm,
            onValueChange = { confirm = it },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
            ),
        )

        error?.let {
            Spacer(Modifier.height(14.dp))
            Text(it, color = Sell400, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(Modifier.height(24.dp))
        if (busy) {
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .height(28.dp),
                color = Teal400,
                strokeWidth = 2.5.dp,
            )
        } else {
            NexoraPrimaryButton(
                text = "Create account",
                onClick = {
                    val localError = when {
                        displayName.isBlank() -> "Enter a display name"
                        !email.contains("@") -> "Enter a valid email address"
                        password.length < 8 || !password.any { it.isLetter() } || !password.any { it.isDigit() } ->
                            "Password needs 8+ characters with a letter and a number"
                        password != confirm -> "Passwords don't match"
                        else -> null
                    }
                    if (localError != null) {
                        error = localError
                    } else {
                        busy = true
                        error = null
                        viewModel.register(displayName.trim(), email.trim(), password) { message ->
                            busy = false
                            if (message != null) error = message
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun AuthField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    visualTransformation: androidx.compose.ui.text.input.VisualTransformation =
        androidx.compose.ui.text.input.VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    supporting: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        supportingText = if (supporting != null) {
            { Text(supporting, style = MaterialTheme.typography.labelSmall) }
        } else null,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Teal400,
            focusedLabelColor = Teal400,
            cursorColor = Teal400,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}
