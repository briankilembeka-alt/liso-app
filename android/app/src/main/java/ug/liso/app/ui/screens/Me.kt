package ug.liso.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ug.liso.app.R
import ug.liso.app.data.Api
import ug.liso.app.data.Category

/** Me tab: sign in, or open a shop, or go to the shop's incoming calls. */
@Composable
fun MeScreen(api: Api, signedIn: Boolean, lang: String, onSignIn: () -> Unit, onIncomingCalls: (sellerId: String) -> Unit) {
    if (!signedIn) {
        Centered {
            Text(stringResource(R.string.me_signed_out), style = MaterialTheme.typography.titleMedium)
            Button(onClick = onSignIn) { Text(stringResource(R.string.sign_in)) }
        }
        return
    }
    var reload by remember { mutableStateOf(0) }
    Loader(reload, { api.mySellers() to api.categories(lang).filter { it.depth == 1 } }) { (shops, categories) ->
        val shop = shops.firstOrNull()
        if (shop == null) {
            OpenShopForm(api, categories) { reload++ }
        } else {
            Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(R.string.my_shop), style = MaterialTheme.typography.labelLarge)
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(shop.businessName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(shop.district, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Button(onClick = { onIncomingCalls(shop.id) }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                    Text(stringResource(R.string.take_calls), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OpenShopForm(api: Api, categories: List<Category>, onOpened: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var district by remember { mutableStateOf("Kampala") }
    var business by remember { mutableStateOf(false) }
    var category by remember { mutableStateOf(categories.firstOrNull()) }
    var menuOpen by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.open_shop_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        OutlinedTextField(name, { name = it.take(80) }, label = { Text(stringResource(R.string.business_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(district, { district = it.take(40) }, label = { Text(stringResource(R.string.district)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !business, onClick = { business = false }, label = { Text(stringResource(R.string.kind_individual)) })
            FilterChip(selected = business, onClick = { business = true }, label = { Text(stringResource(R.string.kind_business)) })
        }
        if (business) Text(stringResource(R.string.business_docs_grace), style = MaterialTheme.typography.bodySmall)
        ExposedDropdownMenuBox(expanded = menuOpen, onExpandedChange = { menuOpen = it }) {
            OutlinedTextField(
                value = category?.name.orEmpty(), onValueChange = {}, readOnly = true,
                label = { Text(stringResource(R.string.what_you_sell)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(menuOpen) },
                modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
            )
            ExposedDropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                categories.forEach { c -> DropdownMenuItem(text = { Text(c.name) }, onClick = { category = c; menuOpen = false }) }
            }
        }
        Button(
            onClick = {
                val c = category ?: return@Button
                scope.launch {
                    busy = true; failed = false
                    try { api.openShop(name.trim(), if (business) "business" else "individual", district.trim(), c.id); onOpened() }
                    catch (e: Exception) { failed = true }
                    busy = false
                }
            },
            enabled = !busy && name.trim().length >= 2 && district.trim().length >= 2 && category != null,
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) { Text(stringResource(R.string.open_shop)) }
        if (failed) Text(stringResource(R.string.error_offline), color = MaterialTheme.colorScheme.error)
    }
}
