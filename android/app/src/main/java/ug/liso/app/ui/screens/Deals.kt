package ug.liso.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import ug.liso.app.R
import ug.liso.app.data.Api
import ug.liso.app.data.ApiException
import ug.liso.app.data.Deal

/** "UGX 450,000" */
fun ugx(amount: String): String = "UGX " + (amount.toLongOrNull()?.let { "%,d".format(it) } ?: amount)

private val DELIVERY = listOf(
    "seller" to R.string.delivery_seller,
    "safe_boda" to R.string.delivery_safe_boda,
    "partner" to R.string.delivery_partner,
    "pickup" to R.string.delivery_pickup,
)

private fun statusText(status: String) = when (status) {
    "agreed" -> R.string.status_agreed
    "accepted" -> R.string.status_accepted
    "delivered" -> R.string.status_delivered
    "completed" -> R.string.status_completed
    "cancelled" -> R.string.status_cancelled
    else -> R.string.status_agreed
}

/** Seller writes up what was agreed on the call. The buyer then accepts it in their Deals tab. */
@Composable
fun WriteUpDealScreen(api: Api, callId: String, onDone: (dealId: String?) -> Unit) {
    var item by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var delivery by remember { mutableStateOf("seller") }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val priceUgx = price.toLongOrNull()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.write_up_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.write_up_hint))
        OutlinedTextField(item, { item = it.take(200) }, label = { Text(stringResource(R.string.deal_item)) }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            price, { v -> price = v.filter { it.isDigit() }.take(10) },
            label = { Text(stringResource(R.string.deal_price)) }, prefix = { Text("UGX ") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        // Liso's 3% fee is paid by the seller; show what they will receive.
        priceUgx?.let {
            val fee = (it * 3 + 50) / 100
            Text(stringResource(R.string.you_receive, ugx((it - fee).toString()), ugx(fee.toString())), style = MaterialTheme.typography.bodySmall)
        }
        Text(stringResource(R.string.delivery), style = MaterialTheme.typography.labelLarge)
        DELIVERY.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (key, label) -> FilterChip(selected = delivery == key, onClick = { delivery = key }, label = { Text(stringResource(label)) }) }
            }
        }
        Button(
            onClick = {
                scope.launch {
                    busy = true; failed = false
                    try { onDone(api.writeUpDeal(callId, item.trim(), priceUgx!!, delivery).id) } catch (e: Exception) { failed = true }
                    busy = false
                }
            },
            enabled = !busy && item.trim().length >= 2 && (priceUgx ?: 0) > 0,
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) { Text(stringResource(R.string.send_to_buyer)) }
        OutlinedButton(onClick = { onDone(null) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.no_deal)) }
        if (failed) Text(stringResource(R.string.error_offline), color = MaterialTheme.colorScheme.error)
    }
}

@Composable
fun DealsScreen(api: Api, signedIn: Boolean, onSignIn: () -> Unit, onDeal: (String) -> Unit) {
    if (!signedIn) {
        Centered {
            Text(stringResource(R.string.deals_signed_out), style = MaterialTheme.typography.titleMedium)
            Button(onClick = onSignIn) { Text(stringResource(R.string.sign_in)) }
        }
        return
    }
    Loader(Unit, { api.deals() }) { deals ->
        if (deals.isEmpty()) {
            Centered { Text(stringResource(R.string.no_deals), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            return@Loader
        }
        LazyColumn(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { Text(stringResource(R.string.tab_deals), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
            items(deals, key = { it.id }) { d ->
                Card(Modifier.fillMaxWidth().clickable { onDeal(d.id) }) {
                    Column(Modifier.padding(16.dp)) {
                        Text(d.item, fontWeight = FontWeight.SemiBold)
                        Text("${ugx(d.priceUgx)} · ${d.businessName.orEmpty()}", style = MaterialTheme.typography.bodySmall)
                        Text(stringResource(statusText(d.status)), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

/** One deal. Buyer: accept, show the handover code, confirm receipt. Seller: enter the buyer's code at handover. */
@Composable
fun DealScreen(api: Api, dealId: String, viewerIsSeller: Boolean? = null) {
    var reload by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf<Int?>(null) }
    var code by remember { mutableStateOf("") }
    Loader(reload, { api.deal(dealId) to api.deals().firstOrNull { it.id == dealId }?.role }) { (deal, role) ->
        val seller = viewerIsSeller ?: (role == "seller")
        fun act(block: suspend () -> Deal) = scope.launch {
            error = null
            try { block(); reload++ } catch (e: ApiException) {
                error = if (e.code == "BAD_HANDOVER_CODE") R.string.error_handover_code else R.string.error_offline
            } catch (e: Exception) { error = R.string.error_offline }
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(deal.item, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(ugx(deal.priceUgx), style = MaterialTheme.typography.titleLarge)
            if (seller) Text(stringResource(R.string.you_receive, ugx(deal.sellerReceivesUgx), ugx(deal.feeUgx)), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(DELIVERY.firstOrNull { it.first == deal.delivery }?.second ?: R.string.delivery_seller))
            Text(stringResource(statusText(deal.status)), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)

            when {
                !seller && deal.status == "agreed" -> {
                    Button(onClick = { act { api.acceptDeal(dealId) } }, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text(stringResource(R.string.accept_deal)) }
                    OutlinedButton(onClick = { act { api.cancelDeal(dealId) } }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.cancel_deal)) }
                }
                !seller && deal.status == "accepted" -> deal.handoverCode?.let { c ->
                    Text(stringResource(R.string.handover_code_hint))
                    Text(c, fontSize = 40.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 8.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
                }
                !seller && deal.status == "delivered" -> {
                    Text(stringResource(R.string.confirm_hint))
                    Button(onClick = { act { api.confirmReceived(dealId) } }, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text(stringResource(R.string.confirm_received)) }
                }
                seller && deal.status == "agreed" -> Text(stringResource(R.string.waiting_for_buyer))
                seller && deal.status == "accepted" -> {
                    Text(stringResource(R.string.enter_handover_code))
                    OutlinedTextField(
                        code, { v -> code = v.filter { it.isDigit() }.take(4) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), singleLine = true, modifier = Modifier.fillMaxWidth(),
                    )
                    Button(onClick = { act { api.markDelivered(dealId, code) } }, enabled = code.length == 4, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                        Text(stringResource(R.string.mark_delivered))
                    }
                }
            }
            error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
        }
    }
}
