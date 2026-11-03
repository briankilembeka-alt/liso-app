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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ug.liso.app.R
import ug.liso.app.data.Api

private val NIN = Regex("^[A-Z0-9]{14}$")

/**
 * Buyer ID check, shown the first time they tap Call. Browsing never needs it.
 * Milestone 1 takes the NIN only; a KYC partner's check (and ID photo) replaces this before the pilot.
 */
@Composable
fun IdCheckScreen(api: Api, onVerified: () -> Unit) {
    var nin by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.id_check_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.id_check_why))
        OutlinedTextField(
            value = nin, onValueChange = { v -> nin = v.uppercase().filter { it.isLetterOrDigit() }.take(14) },
            label = { Text(stringResource(R.string.nin)) },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
            singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = {
                scope.launch {
                    busy = true; failed = false
                    try { api.verifyNationalId(nin); onVerified() } catch (e: Exception) { failed = true }
                    busy = false
                }
            },
            enabled = !busy && NIN.matches(nin),
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) { Text(stringResource(R.string.continue_)) }
        if (failed) Text(stringResource(R.string.error_id_check), color = MaterialTheme.colorScheme.error)
    }
}
