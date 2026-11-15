package com.aiphotographer.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aiphotographer.R
import com.aiphotographer.intelligence.ShootingInterest
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/*
 * First launch: three short slides that sell the idea by showing it, then one
 * question - what do you like to shoot? Every image is drawn by ShotMuse's own
 * showcase generator and every "after" is the app's real enhancer output, so
 * nothing here is borrowed and nothing is promised that the app does not do.
 */

private val Ink = Color(0xFF0E0B12)
private val Card = Color(0xFF1A1520)
private val Muted = Color(0xFFB8AFC2)
private val Amber = Color(0xFFFFC46B)
private val Coral = Color(0xFFFF7A59)
private val Brand = Brush.linearGradient(listOf(Amber, Coral))

private data class Showcase(
    val interest: ShootingInterest,
    val label: String,
    @DrawableRes val before: Int,
    @DrawableRes val after: Int,
)

private val SHOWCASE = listOf(
    Showcase(ShootingInterest.PEOPLE, "People", R.drawable.showcase_people_before, R.drawable.showcase_people_after),
    Showcase(ShootingInterest.FOOD, "Food", R.drawable.showcase_food_before, R.drawable.showcase_food_after),
    Showcase(ShootingInterest.TRAVEL, "Travel", R.drawable.showcase_travel_before, R.drawable.showcase_travel_after),
    Showcase(ShootingInterest.NIGHT, "Night & city", R.drawable.showcase_night_before, R.drawable.showcase_night_after),
    Showcase(ShootingInterest.PRODUCTS, "Products", R.drawable.showcase_products_before, R.drawable.showcase_products_after),
    Showcase(ShootingInterest.PETS, "Pets", R.drawable.showcase_pets_before, R.drawable.showcase_pets_after),
)

private const val PAGES = 4

@Composable
fun OnboardingScreen(
    onFinish: (List<ShootingInterest>) -> Unit,
    initialPage: Int = 0,
) {
    val pager = rememberPagerState(initialPage = initialPage) { PAGES }
    val scope = rememberCoroutineScope()
    val picks = remember { mutableStateListOf<ShootingInterest>() }
    val last = pager.currentPage == PAGES - 1

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        // ---- Wordmark and skip ----
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier.size(40.dp).offset(x = (-8).dp),
            )
            Text("ShotMuse", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp, letterSpacing = (-0.3).sp)
            Spacer(Modifier.weight(1f))
            if (!last) {
                Text(
                    text = "Skip",
                    color = Muted,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable { scope.launch { pager.animateScrollToPage(PAGES - 1) } }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }

        HorizontalPager(
            state = pager,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 20.dp),
            pageSpacing = 20.dp,
        ) { page ->
            when (page) {
                0 -> HeroPage()
                1 -> CoachingPage()
                2 -> EditsPage()
                else -> InterestsPage(picks)
            }
        }

        // ---- Dots and the one button ----
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(PAGES) { i ->
                    val width by animateDpAsState(if (i == pager.currentPage) 22.dp else 7.dp, label = "dot")
                    Box(
                        Modifier
                            .height(7.dp)
                            .width(width)
                            .clip(CircleShape)
                            .background(if (i == pager.currentPage) Amber else Color.White.copy(alpha = 0.25f)),
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            val label = when {
                !last -> "Next"
                picks.isEmpty() -> "Skip for now"
                else -> "Start shooting"
            }
            Text(
                text = label,
                color = Ink,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Brand)
                    .clickable {
                        if (last) onFinish(picks.toList())
                        else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                    }
                    .semantics { contentDescription = label }
                    .padding(horizontal = 26.dp, vertical = 14.dp),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Slides
// ---------------------------------------------------------------------------

@Composable
private fun SlideText(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, color = Color.White, fontSize = 30.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp)
        Text(body, color = Muted, fontSize = 16.sp, lineHeight = 22.sp)
    }
}

@Composable
private fun PhotoCard(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    // Takes the space it is given, then the largest 3:4 card that fits in it,
    // so tall phones and short ones both see the whole photo.
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .aspectRatio(3f / 4f)
                .clip(RoundedCornerShape(28.dp))
                .background(Card),
            content = content,
        )
    }
}

@Composable
private fun Chip(text: String, highlight: Boolean, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = if (highlight) Ink else Color.White,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(if (highlight) Brush.linearGradient(listOf(Amber, Coral)) else Brush.linearGradient(listOf(Color(0x99000000), Color(0x99000000))))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

/** Slide 1: the promise, shown as a photo flipping between phone and ShotMuse. */
@Composable
private fun HeroPage() {
    var showAfter by remember { mutableStateOf(false) }
    var index by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1600); showAfter = true
            delay(2200); showAfter = false; index = (index + 1) % SHOWCASE.size
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(22.dp)) {
        PhotoCard(Modifier.weight(1f)) {
            val shot = SHOWCASE[index]
            Crossfade(targetState = showAfter to index, animationSpec = tween(600), label = "hero") { (after, i) ->
                Image(
                    painter = painterResource(if (after) SHOWCASE[i].after else SHOWCASE[i].before),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Chip(
                text = if (showAfter) "With ShotMuse" else "Phone camera",
                highlight = showAfter,
                modifier = Modifier.align(Alignment.TopStart).padding(14.dp),
            )
            Chip(text = shot.label, highlight = false, modifier = Modifier.align(Alignment.TopEnd).padding(14.dp))
        }
        SlideText(
            "Your pocket\nphotographer",
            "Point your camera. ShotMuse reads the light and the frame, and tells you the one thing that makes the shot.",
        )
    }
}

/** Slide 2: coaching in plain words, one tip, then the green light. */
@Composable
private fun CoachingPage() {
    var fixed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) { delay(2400); fixed = !fixed }
    }
    Column(verticalArrangement = Arrangement.spacedBy(22.dp)) {
        PhotoCard(Modifier.weight(1f)) {
            Crossfade(targetState = fixed, animationSpec = tween(500), label = "coach") { ok ->
                Image(
                    painter = painterResource(if (ok) R.drawable.showcase_people_after else R.drawable.showcase_people_before),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            AnimatedContent(
                targetState = fixed,
                transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(300)) },
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                label = "tip",
            ) { ok ->
                if (ok) TipPill(Icons.Filled.CheckCircle, "Looks good — take it", Color(0xFF5BD98A))
                else TipPill(Icons.Filled.WbSunny, "Bright light behind them — turn them to face the light", Amber)
            }
        }
        SlideText(
            "One tip.\nNot twenty settings.",
            "No ISO, no shutter speeds. Just what to do next, in words, until the screen says it looks good.",
        )
    }
}

@Composable
private fun TipPill(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, tint: Color) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color(0xCC000000))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Text(text, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

/** Slide 3: drag to compare - the edit is real, and the original is kept. */
@Composable
private fun EditsPage() {
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        PhotoCard(Modifier.weight(1f)) {
            BeforeAfterSlider(R.drawable.showcase_travel_before, R.drawable.showcase_travel_after)
        }
        SlideText("Edits that stay true", "Light, colour and detail fixed in a second. Never a filter.")
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Promise(Icons.Outlined.PhotoLibrary, "Your original is always saved too")
            Promise(Icons.Outlined.CloudOff, "Works offline. Your photos never leave your phone")
        }
    }
}

@Composable
private fun Promise(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(icon, contentDescription = null, tint = Amber, modifier = Modifier.size(20.dp))
        Text(text, color = Color.White, fontSize = 15.sp)
    }
}

@Composable
private fun BeforeAfterSlider(@DrawableRes before: Int, @DrawableRes after: Int) {
    val split = remember { Animatable(0.85f) }
    var width by remember { mutableStateOf(1f) }
    val scope = rememberCoroutineScope()
    // A one-time sweep so people see it can be dragged.
    LaunchedEffect(Unit) {
        delay(500)
        split.animateTo(0.2f, tween(1100, easing = FastOutSlowInEasing))
        split.animateTo(0.5f, tween(700, easing = FastOutSlowInEasing))
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                width = size.width.toFloat()
                detectHorizontalDragGestures { change, _ ->
                    scope.launch { split.snapTo((change.position.x / width).coerceIn(0.03f, 0.97f)) }
                }
            },
    ) {
        Image(painterResource(after), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        Image(
            painterResource(before), null, contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .drawWithContent { clipRect(right = size.width * split.value) { this@drawWithContent.drawContent() } },
        )
        // The handle
        Box(Modifier.fillMaxHeight().fillMaxWidth(split.value)) {
            Box(Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(2.dp).background(Color.White))
            Box(
                Modifier
                    .align(Alignment.CenterEnd)
                    .offset(x = 15.dp)
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(Color.White),
                contentAlignment = Alignment.Center,
            ) { Text("⟷", color = Ink, fontWeight = FontWeight.Bold) }
        }
        Chip("Before", false, Modifier.align(Alignment.TopStart).padding(14.dp))
        Chip("After", true, Modifier.align(Alignment.TopEnd).padding(14.dp))
    }
}

/** The question: what do you like to shoot? Tunes the coaching. */
@Composable
private fun InterestsPage(picks: MutableList<ShootingInterest>) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SlideText("What do you love\nto shoot?", "Pick a few. ShotMuse tunes its coaching to you.")
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f),
        ) {
            items(SHOWCASE) { item ->
                val selected = item.interest in picks
                val border by animateColorAsState(if (selected) Amber else Color.Transparent, label = "border")
                Box(
                    modifier = Modifier
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(20.dp))
                        .border(3.dp, border, RoundedCornerShape(20.dp))
                        .clickable { if (selected) picks.remove(item.interest) else picks.add(item.interest) }
                        .semantics { contentDescription = item.label },
                ) {
                    Image(painterResource(item.after), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    Box(
                        Modifier.fillMaxSize().background(
                            Brush.verticalGradient(0.5f to Color.Transparent, 1f to Color(0xD0000000)),
                        ),
                    )
                    Text(
                        item.label,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        modifier = Modifier.align(Alignment.BottomStart).padding(12.dp),
                    )
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(10.dp)
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(if (selected) Amber else Color(0x66000000))
                            .border(1.5.dp, Color.White.copy(alpha = if (selected) 0f else 0.8f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (selected) Icon(Icons.Filled.Check, null, tint = Ink, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}
