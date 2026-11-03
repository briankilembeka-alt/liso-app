package ug.liso.app.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ug.liso.app.R

/**
 * Welcome: one big blue "Shop now" that visibly presses in, then straight to categories.
 * TODO: background video of a buyer on a call with a seller showing a shoe (short, muted, looped, low bitrate).
 */
@Composable
fun WelcomeScreen(onShopNow: () -> Unit) {
    val press = remember { MutableInteractionSource() }
    val pressed by press.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.92f else 1f, label = "press")
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("liso", fontSize = 44.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
        Text(stringResource(R.string.welcome_tagline), style = MaterialTheme.typography.titleMedium)
        Button(
            onClick = onShopNow,
            interactionSource = press,
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier.width(240.dp).height(64.dp).scale(scale),
        ) {
            Text(stringResource(R.string.shop_now), fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
    }
}
