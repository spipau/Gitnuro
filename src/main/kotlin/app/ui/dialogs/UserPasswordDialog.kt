package app.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusOrder
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.theme.outlinedTextFieldColors
import app.theme.primaryTextColor
import app.theme.textButtonColors
import app.ui.components.PrimaryButton

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun UserPasswordDialog(
    onReject: () -> Unit,
    onAccept: (user: String, password: String) -> Unit
) {
    var userField by remember { mutableStateOf("") }
    var passwordField by remember { mutableStateOf("") }
    val userFieldFocusRequester = remember { FocusRequester() }
    val passwordFieldFocusRequester = remember { FocusRequester() }
    val buttonFieldFocusRequester = remember { FocusRequester() }
    val acceptDialog = {
        onAccept(userField, passwordField)
    }
    MaterialDialog(
        onCloseRequested = onReject
    ) {
        Column(
            modifier = Modifier
                .background(MaterialTheme.colors.background),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {

            Text(
                text = "Introduce your remote server credentials",
                modifier = Modifier
                    .padding(vertical = 8.dp),
                color = MaterialTheme.colors.primaryTextColor,
            )

            OutlinedTextField(
                modifier = Modifier
                    .focusOrder(userFieldFocusRequester) {
                        this.next = passwordFieldFocusRequester
                    }
                    .width(300.dp)
                    .onPreviewKeyEvent {
                        if (it.key == Key.Enter) {
                            acceptDialog()
                            true
                        } else {
                            false
                        }
                    },
                value = userField,
                singleLine = true,
                colors = outlinedTextFieldColors(),
                label = { Text("User", fontSize = 14.sp) },
                textStyle = TextStyle(fontSize = 14.sp, color = MaterialTheme.colors.primaryTextColor),
                onValueChange = {
                    userField = it
                },
            )
            OutlinedTextField(
                modifier = Modifier.padding(bottom = 8.dp)
                    .focusOrder(passwordFieldFocusRequester) {
                        this.previous = userFieldFocusRequester
                        this.next = buttonFieldFocusRequester
                    }
                    .width(300.dp)
                    .onPreviewKeyEvent {
                        if (it.key == Key.Enter) {
                            acceptDialog()
                            true
                        } else {
                            false
                        }
                    },
                value = passwordField,
                singleLine = true,
                label = { Text("Password", fontSize = 14.sp) },
                textStyle = TextStyle(fontSize = 14.sp, color = MaterialTheme.colors.primaryTextColor),
                colors = outlinedTextFieldColors(),
                onValueChange = {
                    passwordField = it
                },
                visualTransformation = PasswordVisualTransformation()
            )

            Row(
                modifier = Modifier
                    .padding(top = 16.dp)
                    .align(Alignment.End)
            ) {
                TextButton(
                    modifier = Modifier.padding(end = 8.dp),
                    colors = textButtonColors(),
                    onClick = {
                        onReject()
                    }
                ) {
                    Text("Cancel")
                }
                PrimaryButton(
                    modifier = Modifier.focusOrder(buttonFieldFocusRequester) {
                        this.previous = passwordFieldFocusRequester
                        this.next = userFieldFocusRequester
                    },
                    onClick = {
                        onAccept(userField, passwordField)
                    },
                    text = "Continue"
                )
            }
        }

        LaunchedEffect(Unit) {
            userFieldFocusRequester.requestFocus()
        }
    }
}