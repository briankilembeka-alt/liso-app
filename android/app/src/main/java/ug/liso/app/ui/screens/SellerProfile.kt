package ug.liso.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ug.liso.app.R
import ug.liso.app.data.Api

/** Seller profile with the Video call button. The first tap asks the buyer to verify their National ID. */
@Composable
fun SellerProfileScreen(api: Api, sellerId: String, onCall: (String) -> Unit) {
    Loader(sellerId, { api.seller(sellerId) }) { s ->
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(s.businessName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(s.district, color = MaterialTheme.colorScheme.onSurfaceVariant)
            s.completedDeals?.let { Text(stringResource(R.string.completed_deals, it)) }
            Button(onClick = { onCall(s.id) }, enabled = s.canTakeCalls, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Text(stringResource(R.string.call_seller), fontWeight = FontWeight.Bold)
            }
            Text(stringResource(R.string.call_recorded_notice), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.liso_protect), style = MaterialTheme.typography.bodySmall)
        }
    }
}
