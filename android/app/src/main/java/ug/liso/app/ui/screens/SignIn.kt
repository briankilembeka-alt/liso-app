package ug.liso.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ug.liso.app.R
import ug.liso.app.data.Api
import ug.liso.app.data.ApiException

/** Phone number, then the 6-digit SMS code. Asked only when the person first needs an account (e.g. to call). */
@Composable
fun SignInScreen(api: Api, onSignedIn: () -> Unit) {
    var phone by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var codeSent by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Int?>(null) }
    val scope = rememberCoroutineScope()

    fun run(block: suspend () -> Unit) = scope.launch {
        busy = true; error = null
        try { block() } catch (e: ApiException) {
            error = when (e.code) {
                "BAD_CODE" -> R.string.error_bad_code
                "TOO_MANY_CODES" -> R.string.error_too_many_codes
                "BAD_REQUEST" -> R.string.error_bad_phone
                else -> R.string.error_offline
            }
        } catch (e: Exception) { error = R.string.error_offline }
        busy = false
    }

    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.sign_in_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        if (!codeSent) {
            Text(stringResource(R.string.sign_in_phone_hint))
            OutlinedTextField(
                value = phone, onValueChange = { phone = it.take(16) },
                label = { Text(stringResource(R.string.phone_number)) },
                placeholder = { Text("07XX XXX XXX") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = { run { api.requestCode(phone); codeSent = true } },
                enabled = !busy && phone.count { it.isDigit() } >= 9,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) { Text(stringResource(R.string.send_code)) }
        } else {
            Text(stringResource(R.string.sign_in_code_hint, phone))
            OutlinedTextField(
                value = code, onValueChange = { v -> code = v.filter { it.isDigit() }.take(6) },
                label = { Text(stringResource(R.string.code)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = { run { api.verifyCode(phone, code); onSignedIn() } },
                enabled = !busy && code.length == 6,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) { Text(stringResource(R.string.continue_)) }
            TextButton(onClick = { codeSent = false; code = "" }) { Text(stringResource(R.string.change_number)) }
        }
        error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
    }
}
