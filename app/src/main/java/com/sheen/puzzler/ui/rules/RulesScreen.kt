package com.sheen.puzzler.ui.rules

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sheen.puzzler.ui.PuzzlerColors
import com.sheen.puzzler.ui.PuzzlerFonts
import kotlinx.coroutines.launch

/** How to play: one swipeable card per topic, each with a looping demo (docs/SPEC.md §7). */
@Composable
fun RulesScreen(onBack: () -> Unit) {
    val cards = Rules.cards
    val pager = rememberPagerState { cards.size }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().padding(16.dp).testTag("rules_page")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹ Back") }
            Text("How to play", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
        HorizontalPager(state = pager, modifier = Modifier.weight(1f).fillMaxWidth()) { page ->
            RulesCardView(cards[page], active = pager.currentPage == page)
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.Center) {
            cards.indices.forEach { i ->
                Box(
                    Modifier
                        .padding(horizontal = 4.dp)
                        .size(8.dp)
                        .background(if (i == pager.currentPage) PuzzlerColors.gold else Color.White.copy(alpha = 0.3f), CircleShape),
                )
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(
                onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } },
                enabled = pager.currentPage > 0,
            ) { Text("‹ Previous") }
            Spacer(Modifier.weight(1f))
            val last = pager.currentPage == cards.lastIndex
            Button(
                onClick = { if (last) onBack() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
                modifier = Modifier.testTag("rules_next"),
            ) { Text(if (last) "Done" else "Next ›") }
        }
    }
}

@Composable
private fun RulesCardView(
    card: RulesCard,
    active: Boolean,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            card.title,
            fontFamily = PuzzlerFonts.display,
            fontSize = 30.sp,
            color = PuzzlerColors.gold,
            modifier = Modifier.padding(vertical = 8.dp).testTag(if (active) "rules_title" else "rules_title_offscreen"),
        )
        // Only the card on screen runs its demo; neighbors the pager keeps composed stay still.
        if (active) DemoBoard(card.demos, Modifier.fillMaxWidth(0.75f)) else Spacer(Modifier.height(280.dp))
        Spacer(Modifier.height(12.dp))
        card.table.forEach { (label, value) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                Text(label, fontSize = 15.sp, modifier = Modifier.weight(1f))
                Text(value, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = PuzzlerColors.gold)
            }
        }
        if (card.table.isNotEmpty()) Spacer(Modifier.height(8.dp))
        card.lines.forEach { line ->
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                Text("•  ", fontSize = 15.sp, color = PuzzlerColors.gold)
                Text(line, fontSize = 15.sp, lineHeight = 21.sp)
            }
        }
    }
}
