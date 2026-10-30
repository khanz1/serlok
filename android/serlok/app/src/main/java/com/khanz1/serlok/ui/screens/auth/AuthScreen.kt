package com.khanz1.serlok.ui.screens.auth

import android.util.Patterns
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.khanz1.serlok.R
import com.khanz1.serlok.ui.components.SerlokMark
import com.khanz1.serlok.ui.theme.SerlokSpacing

@Composable
fun AuthScreen(
    onNavigateToHome: () -> Unit,
    viewModel: AuthViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    var register by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var validated by rememberSaveable { mutableStateOf(false) }

    val nameInvalid = register && name.trim().length < 2
    val emailInvalid = !Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()
    val passwordInvalid = password.length < 8
    val loading = state is AuthUiState.Loading

    LaunchedEffect(Unit) { viewModel.navigateToHome.collect { onNavigateToHome() } }
    LaunchedEffect(register) {
        viewModel.clearError()
        validated = false
    }

    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(56.dp))
        Box(
            modifier = Modifier.size(84.dp).clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            SerlokMark(Modifier.size(48.dp), contentDescription = stringResource(R.string.app_name))
        }
        Spacer(Modifier.height(SerlokSpacing.lg))
        Text(
            stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            stringResource(R.string.brand_meaning),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(SerlokSpacing.xxl))
        Text(
            stringResource(if (register) R.string.auth_register else R.string.auth_welcome),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            stringResource(R.string.auth_subtitle),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 24.dp),
        )

        AnimatedVisibility(register) {
            Column {
                AuthField(
                    value = name,
                    onValueChange = { name = it; viewModel.clearError() },
                    label = stringResource(R.string.auth_name),
                    icon = Icons.Rounded.Person,
                    isError = validated && nameInvalid,
                    errorText = stringResource(R.string.auth_name_error),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                )
                Spacer(Modifier.height(12.dp))
            }
        }
        AuthField(
            value = email,
            onValueChange = { email = it; viewModel.clearError() },
            label = stringResource(R.string.auth_email),
            icon = Icons.Rounded.Email,
            isError = validated && emailInvalid,
            errorText = stringResource(R.string.auth_email_error),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        )
        Spacer(Modifier.height(12.dp))
        AuthField(
            value = password,
            onValueChange = { password = it; viewModel.clearError() },
            label = stringResource(R.string.auth_password),
            icon = Icons.Rounded.Lock,
            isError = validated && passwordInvalid,
            errorText = stringResource(R.string.auth_password_error),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailing = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        if (passwordVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                        contentDescription = stringResource(R.string.auth_password),
                    )
                }
            },
        )

        if (state is AuthUiState.Error) {
            Text(
                text = (state as AuthUiState.Error).message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )
        }
        Spacer(Modifier.height(SerlokSpacing.xl))
        Button(
            onClick = {
                validated = true
                if (!nameInvalid && !emailInvalid && !passwordInvalid) {
                    if (register) viewModel.register(name, email, password)
                    else viewModel.login(email, password)
                }
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            enabled = !loading,
            shape = MaterialTheme.shapes.small,
        ) {
            if (loading) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.size(10.dp))
            }
            Text(stringResource(if (register) R.string.auth_register else R.string.auth_login))
        }
        TextButton(onClick = { register = !register }, enabled = !loading) {
            Text(stringResource(if (register) R.string.auth_switch_login else R.string.auth_switch_register))
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun AuthField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: ImageVector,
    isError: Boolean,
    errorText: String,
    keyboardOptions: KeyboardOptions,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailing: (@Composable () -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        trailingIcon = trailing,
        singleLine = true,
        isError = isError,
        supportingText = if (isError) ({ Text(errorText) }) else null,
        keyboardOptions = keyboardOptions,
        visualTransformation = visualTransformation,
        shape = MaterialTheme.shapes.small,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = MaterialTheme.colorScheme.onSurface,
            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
            focusedLabelColor = MaterialTheme.colorScheme.primary,
            unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            focusedLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unfocusedLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            focusedTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unfocusedTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            cursorColor = MaterialTheme.colorScheme.primary,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}
