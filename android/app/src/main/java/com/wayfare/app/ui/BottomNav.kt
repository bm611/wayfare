package com.wayfare.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** The three places the app goes. "Tape" is a trip's ledger; "You" is the account. */
enum class NavTab(val label: String) { Trips("Trips"), Tape("Tape"), You("You") }

/**
 * The bottom bar. Its glyphs are drawn rather than iconised: two stacked
 * boarding-pass stubs for Trips, three ragged receipt lines for Tape, a plain
 * ring for You. The active tab takes an amber pill behind its glyph — the same
 * tinted-not-filled treatment the segmented control uses, so the one solid amber
 * on screen is always the action button.
 */
@Composable
fun WayfareBottomBar(
    current: NavTab,
    modifier: Modifier = Modifier,
    onSelect: (NavTab) -> Unit,
) {
    Row(
        modifier.fillMaxWidth().background(NavBar).navigationBarsPadding()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        NavTab.entries.forEach { tab ->
            NavItem(tab, tab == current, Modifier.weight(1f)) { onSelect(tab) }
        }
    }
}

@Composable
private fun NavItem(tab: NavTab, active: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val tint by animateColorAsState(if (active) Amber else Slate, label = "Tab tint")
    Column(
        modifier
            .clickable(enabled = !active, onClick = onClick)
            .semantics { role = Role.Tab; selected = active }
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            Modifier.width(64.dp).height(32.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(if (active) Amber.copy(alpha = .22f) else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            when (tab) {
                NavTab.Trips -> Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    repeat(2) { Box(Modifier.width(18.dp).height(6.dp).clip(RoundedCornerShape(2.dp)).background(tint)) }
                }
                NavTab.Tape -> Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    // Ragged, like a receipt torn off mid-line.
                    listOf(20.dp, 20.dp, 13.dp).forEach {
                        Box(Modifier.width(it).height(2.5.dp).background(tint))
                    }
                }
                // A ring, so the pill behind it shows through when the tab is on.
                NavTab.You -> Box(Modifier.size(16.dp).border(2.5.dp, tint, CircleShape))
            }
        }
        Text(
            tab.label,
            color = tint,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
            ),
        )
    }
}
