package ug.liso.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ug.liso.app.R
import ug.liso.app.data.Api
import ug.liso.app.data.Seller

/**
 * Home for a category: live shows on top (milestone 3), then the Top sellers strip, then sellers ranked on merit.
 * TODO: auto-scrolling Top sellers strip (3 s per slide) with profile photos.
 */
@Composable
fun HomeScreen(api: Api, categoryId: String, categoryName: String, onSeller: (String) -> Unit) {
    Loader(categoryId, { api.topSellers(categoryId) to api.sellers(categoryId) }) { (top, ranked) ->
        LazyColumn(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { Text(categoryName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp)) }
            if (top.isNotEmpty()) {
                item { Text(stringResource(R.string.top_sellers), style = MaterialTheme.typography.titleMedium) }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        top.forEach { s -> Card(Modifier.weight(1f).clickable { onSeller(s.id) }) { Text(s.businessName, Modifier.padding(12.dp)) } }
                    }
                }
            }
            item { Text(stringResource(R.string.sellers_near_you), style = MaterialTheme.typography.titleMedium) }
            items(ranked, key = { it.id }) { s -> SellerRow(s) { onSeller(s.id) } }
        }
    }
}

@Composable
private fun SellerRow(s: Seller, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(s.businessName, fontWeight = FontWeight.SemiBold)
                val where = listOfNotNull(s.district, s.distanceKm?.let { "$it km" }).joinToString(" · ")
                Text(where, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (s.sponsored) Text("Sponsored", style = MaterialTheme.typography.labelSmall)
        }
    }
}
