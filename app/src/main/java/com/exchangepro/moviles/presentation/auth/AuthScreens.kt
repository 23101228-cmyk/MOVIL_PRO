package com.exchangepro.moviles.presentation.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.ui.res.painterResource
import com.exchangepro.moviles.R
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.exchangepro.moviles.data.repository.FirebaseAuthRepository
import com.exchangepro.moviles.data.repository.RegistrationData
import com.exchangepro.moviles.domain.model.UserRole
import com.exchangepro.moviles.presentation.navigation.Route
import com.exchangepro.moviles.ui.components.ExchangeCard
import com.exchangepro.moviles.ui.components.PrimaryAction
import com.exchangepro.moviles.ui.theme.ExchangeMuted
import com.exchangepro.moviles.ui.theme.ExchangeNegative
import com.exchangepro.moviles.ui.theme.ExchangePositive
import com.exchangepro.moviles.ui.theme.ExchangePrimary

@Composable
fun LoginScreen(navController: NavController) {
    val authRepository = remember { FirebaseAuthRepository() }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var submitted by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var messageIsSuccess by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }

    AuthContainer {
        BrandHeader(
            title = "Bienvenido de vuelta",
            subtitle = "Ingresa a tu cuenta para continuar"
        )

        ExchangeCard {
            AuthField(
                label = "Correo electronico",
                value = email,
                onValueChange = { email = it },
                placeholder = "tu@correo.com",
                icon = Icons.Default.Email,
                keyboardType = KeyboardType.Email,
                isError = submitted && !isValidEmail(email),
                error = "Ingresa un correo valido"
            )
            Spacer(Modifier.height(14.dp))
            AuthField(
                label = "Contrasena",
                value = password,
                onValueChange = { password = it },
                placeholder = "********",
                icon = Icons.Default.Lock,
                isPassword = !showPassword,
                trailingIcon = {
                    IconButton(onClick = { showPassword = !showPassword }) {
                        Icon(if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility, contentDescription = null)
                    }
                },
                isError = submitted && password.isBlank(),
                error = "Ingresa tu contrasena"
            )

            message?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, color = if (messageIsSuccess) ExchangePositive else ExchangeNegative, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.height(18.dp))
            PrimaryAction(
                if (isLoading) "Iniciando sesion..." else "Iniciar Sesion",
                onClick = {
                    if (!isLoading) {
                        submitted = true
                        val cleanEmail = email.trim()
                        if (isValidEmail(cleanEmail) && password.isNotBlank()) {
                            isLoading = true
                            message = null
                            authRepository.signIn(cleanEmail, password) { result ->
                                isLoading = false
                                result.fold(
                                    onSuccess = { role ->
                                        messageIsSuccess = true
                                        message = "Sesion iniciada."
                                        val destination = if (role == UserRole.ADMIN) {
                                            Route.AdminDashboard.value
                                        } else {
                                            Route.Home.value
                                        }
                                        navController.navigate(destination) {
                                            popUpTo(Route.Login.value) { inclusive = true }
                                        }
                                    },
                                    onFailure = { error ->
                                        messageIsSuccess = false
                                        message = error.message ?: "No se pudo iniciar sesion."
                                    }
                                )
                            }
                        } else {
                            messageIsSuccess = false
                            message = "Revisa tus credenciales."
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            TextButton(
                enabled = !isLoading,
                onClick = {
                    submitted = true
                    val cleanEmail = email.trim()
                    if (isValidEmail(cleanEmail)) {
                        isLoading = true
                        message = null
                        authRepository.sendPasswordReset(cleanEmail) { result ->
                            isLoading = false
                            result.fold(
                                onSuccess = {
                                    messageIsSuccess = true
                                    message = "Te enviamos un enlace para restablecer tu contrasena."
                                },
                                onFailure = { error ->
                                    messageIsSuccess = false
                                    message = error.message ?: "No se pudo enviar el correo."
                                }
                            )
                        }
                    } else {
                        messageIsSuccess = false
                        message = "Ingresa primero un correo valido."
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Olvidaste tu contrasena?")
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Text("No tienes cuenta?", color = ExchangeMuted)
                TextButton(enabled = !isLoading, onClick = { navController.navigate(Route.Register.value) }) {
                    Text("Registrate")
                }
            }
        }
    }
}

@Composable
fun RegisterScreen(navController: NavController) {
    val authRepository = remember { FirebaseAuthRepository() }
    var names by remember { mutableStateOf("") }
    var lastNames by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var document by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var showConfirmPassword by remember { mutableStateOf(false) }
    var submitted by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var messageIsSuccess by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }

    AuthContainer(scroll = true) {
        BrandHeader(
            title = "Crear tu cuenta",
            subtitle = "Unete a la plataforma de intercambio P2P"
        )

        ExchangeCard {
            AuthField("Nombres", names, { names = it.take(100) }, "Ej: Juan", Icons.Default.Person, isError = submitted && names.isBlank(), error = "Requerido")
            Spacer(Modifier.height(12.dp))
            AuthField("Apellidos", lastNames, { lastNames = it.take(100) }, "Ej: Perez", Icons.Default.Person, isError = submitted && lastNames.isBlank(), error = "Requerido")
            Spacer(Modifier.height(12.dp))
            AuthField("Correo electronico", email, { email = it }, "tu@correo.com", Icons.Default.Email, KeyboardType.Email, isError = submitted && !isValidEmail(email), error = "Correo invalido")
            Spacer(Modifier.height(12.dp))
            AuthField("Telefono", phone, { phone = it.filter(Char::isDigit).take(9) }, "999888777", Icons.Default.Phone, KeyboardType.Phone, isError = submitted && phone.length != 9, error = "Debe tener 9 digitos")
            Spacer(Modifier.height(12.dp))
            AuthField("Documento de identidad", document, { document = it.filter(Char::isDigit).take(8) }, "DNI o CE", Icons.Default.Badge, KeyboardType.Number, isError = submitted && document.length != 8, error = "Debe tener 8 digitos")
            Spacer(Modifier.height(12.dp))
            AuthField(
                label = "Contrasena",
                value = password,
                onValueChange = { password = it.take(50) },
                placeholder = "********",
                icon = Icons.Default.Lock,
                isPassword = !showPassword,
                trailingIcon = {
                    IconButton(onClick = { showPassword = !showPassword }) {
                        Icon(if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility, contentDescription = null)
                    }
                },
                isError = submitted && password.length < 6,
                error = "Minimo 6 caracteres"
            )
            Spacer(Modifier.height(12.dp))
            AuthField(
                label = "Confirmar contrasena",
                value = confirmPassword,
                onValueChange = { confirmPassword = it.take(50) },
                placeholder = "********",
                icon = Icons.Default.Lock,
                isPassword = !showConfirmPassword,
                trailingIcon = {
                    IconButton(onClick = { showConfirmPassword = !showConfirmPassword }) {
                        Icon(if (showConfirmPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility, contentDescription = null)
                    }
                },
                isError = submitted && confirmPassword != password,
                error = "No coinciden"
            )

            message?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, color = if (messageIsSuccess) ExchangePositive else ExchangeNegative, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.height(18.dp))
            PrimaryAction(
                if (isLoading) "Creando cuenta..." else "Crear Cuenta",
                onClick = {
                    if (!isLoading) {
                        submitted = true
                        val valid = names.isNotBlank() &&
                            lastNames.isNotBlank() &&
                            isValidEmail(email) &&
                            phone.length == 9 &&
                            document.length == 8 &&
                            password.length >= 6 &&
                            password == confirmPassword

                        if (valid) {
                            isLoading = true
                            message = null
                            authRepository.register(
                                RegistrationData(
                                    names = names,
                                    lastNames = lastNames,
                                    email = email,
                                    phone = phone,
                                    documentNumber = document,
                                    password = password
                                )
                            ) { result ->
                                isLoading = false
                                result.fold(
                                    onSuccess = {
                                        messageIsSuccess = true
                                        message = "Cuenta creada exitosamente."
                                        navController.navigate(Route.Login.value) {
                                            popUpTo(Route.Register.value) { inclusive = true }
                                        }
                                    },
                                    onFailure = { error ->
                                        messageIsSuccess = false
                                        message = error.message ?: "No se pudo crear la cuenta."
                                    }
                                )
                            }
                        } else {
                            messageIsSuccess = false
                            message = "Completa los datos requeridos."
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Text("Ya tienes cuenta?", color = ExchangeMuted)
                TextButton(enabled = !isLoading, onClick = { navController.navigate(Route.Login.value) }) {
                    Text("Inicia sesion")
                }
            }
        }
    }
}

@Composable
private fun AuthContainer(scroll: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxSize()
    ) {
        if (scroll) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                item {
                    Column(modifier = Modifier.fillMaxWidth(), content = content)
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
                content = content
            )
        }
    }
}

@Composable
private fun BrandHeader(title: String, subtitle: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(id = R.drawable.app_logo),
            contentDescription = "ExchangePro Logo",
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(10.dp))
        )
        Spacer(Modifier.width(10.dp))
        Text("ExchangePro", color = ExchangePrimary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    }
    Spacer(Modifier.height(22.dp))
    Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    Text(subtitle, color = ExchangeMuted)
    Spacer(Modifier.height(24.dp))
}

@Composable
private fun AuthField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    keyboardType: KeyboardType = KeyboardType.Text,
    isPassword: Boolean = false,
    trailingIcon: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    error: String = ""
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(icon, contentDescription = null, tint = ExchangeMuted) },
        trailingIcon = trailingIcon,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
        isError = isError
    )
    if (isError) {
        Text(error, color = ExchangeNegative, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 12.dp, top = 4.dp))
    }
}

private fun isValidEmail(value: String): Boolean =
    value.trim().isNotBlank() && Regex(".+@.+\\..+").matches(value.trim())
