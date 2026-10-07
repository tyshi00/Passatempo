package com.thelightphone.games.klondike

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import com.thelightphone.games.ActivePuzzleStore
import com.thelightphone.games.DailyPlaytimeStore
import com.thelightphone.games.GameBudgets
import com.thelightphone.games.GameKeys
import com.thelightphone.games.cards.Card
import com.thelightphone.games.cards.CardBack
import com.thelightphone.games.cards.CardView
import com.thelightphone.games.cards.EmptySlot
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SimpleLightScreen
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable
import com.thelightphone.sdk.ui.rememberLightHapticTrigger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val UNDO_LIMIT = 120
private const val FLIGHT_MILLIS = 170
private const val HINT_MILLIS = 3_000L
private const val BUDGET_TICK_MS = 1000L

enum class Notice { DEAD_END, UNWINNABLE }

data class MoveAnimation(
    val id: Long,
    val cards: List<Card>,
    val source: Pile,
    val destination: Pile,
)

data class Table(
    val game: Game,
    val animation: MoveAnimation? = null,
    val hint: Action? = null,
    val notice: Notice? = null,
    val checking: Boolean = false,
)

// ============================================================ ViewModel

class KlondikeScreenViewModel(
    private val activePuzzleStore: ActivePuzzleStore,
    private val dailyPlaytimeStore: DailyPlaytimeStore,
) : LightViewModel<Unit>() {

    private val history = ArrayDeque<Game>()
    private var hintCursor = 0
    private var animationId = 0L
    private var analysis: Job? = null
    private var budgetJob: Job? = null

    val table = MutableStateFlow(Table(Game.deal(System.currentTimeMillis())))
    val canUndo = MutableStateFlow(false)
    val remainingSeconds = MutableStateFlow(GameBudgets.KLONDIKE_SECONDS)
    val timeUp = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            remainingSeconds.value = dailyPlaytimeStore.remainingSeconds(GameKeys.KLONDIKE, GameBudgets.KLONDIKE_SECONDS)
            if (remainingSeconds.value <= 0) timeUp.value = true
        }
        viewModelScope.launch {
            val saved = activePuzzleStore.load(GameKeys.KLONDIKE)?.let { SaveState.decode(it) }
            val current = table.value
            if (saved != null && current.game.moves == 0 && history.isEmpty()) {
                table.value = Table(saved, notice = noticeFor(saved))
            }
        }
    }

    override fun onScreenShow(screen: SimpleLightScreen<Unit>) {
        super.onScreenShow(screen)
        if (!timeUp.value) startBudgetTicker()
    }

    override fun onScreenHide(screen: SimpleLightScreen<Unit>) {
        super.onScreenHide(screen)
        stopBudgetTicker()
    }

    override fun onAppPause() {
        super.onAppPause()
        stopBudgetTicker()
        persist()
    }

    private fun startBudgetTicker() {
        budgetJob?.cancel()
        budgetJob = viewModelScope.launch {
            while (isActive && remainingSeconds.value > 0) {
                delay(BUDGET_TICK_MS)
                val remaining = dailyPlaytimeStore.addUsage(
                    GameKeys.KLONDIKE,
                    elapsedSeconds = 1,
                    dailyBudgetSeconds = GameBudgets.KLONDIKE_SECONDS,
                )
                remainingSeconds.value = remaining
                if (remaining <= 0) { timeUp.value = true; break }
            }
        }
    }

    private fun stopBudgetTicker() {
        budgetJob?.cancel()
    }

    fun newGame() {
        history.clear()
        canUndo.value = false
        hintCursor = 0
        table.value = Table(Game.deal(System.currentTimeMillis()))
        persist()
    }

    fun undo() {
        val previous = history.removeLastOrNull() ?: return
        canUndo.value = history.isNotEmpty()
        hintCursor = 0
        table.value = Table(previous, notice = noticeFor(previous))
        persist()
    }

    fun tap(pile: Pile, cardIndex: Int) {
        val action = table.value.game.autoAction(pile, cardIndex) ?: return
        commit(action)
    }

    fun drop(source: Pile, cardIndex: Int, destination: Pile) {
        commit(Action.Shift(source, cardIndex, destination), animate = false)
    }

    fun requestHint() {
        val current = table.value
        val hints = current.game.hints()
        if (hints.isEmpty()) {
            table.value = current.copy(hint = null, notice = Notice.DEAD_END)
            return
        }
        val index = ((hintCursor % hints.size) + hints.size) % hints.size
        hintCursor++
        table.value = current.copy(hint = hints[index], checking = analysis?.isActive != true)
        checkWinnable()
    }

    fun clearHint() {
        val current = table.value
        if (current.hint != null) table.value = current.copy(hint = null)
    }

    private fun commit(action: Action, animate: Boolean = true) {
        val current = table.value.game
        val next = current.perform(action) ?: return
        if (next == current) return

        val cards = if (animate) current.cardsMovedBy(action) else emptyList()
        history.addLast(current)
        while (history.size > UNDO_LIMIT) history.removeFirst()
        canUndo.value = true
        hintCursor = 0

        table.value = Table(
            game = next,
            animation = if (cards.isEmpty()) null
            else MoveAnimation(++animationId, cards, sourceOf(action), destinationOf(action)),
            notice = noticeFor(next),
        )
        persist()
    }

    private fun noticeFor(game: Game): Notice? =
        if (!game.isWon && game.isDeadEnd()) Notice.DEAD_END else null

    private fun checkWinnable() {
        if (analysis?.isActive == true) return
        val snapshot = table.value.game
        analysis = viewModelScope.launch(Dispatchers.Default) {
            val result = Solver.analyze(snapshot)
            val current = table.value
            if (current.game != snapshot) return@launch
            table.value = current.copy(
                checking = false,
                notice = if (result.verdict == Verdict.UNWINNABLE) Notice.UNWINNABLE else current.notice,
            )
        }
    }

    private fun sourceOf(action: Action): Pile = when (action) {
        Action.Draw -> Pile.Stock
        is Action.Shift -> action.source
        is Action.TurnOver -> Pile.Tableau(action.column)
    }

    private fun destinationOf(action: Action): Pile = when (action) {
        Action.Draw -> Pile.Waste
        is Action.Shift -> action.destination
        is Action.TurnOver -> Pile.Tableau(action.column)
    }

    private fun persist() {
        val snapshot = table.value.game
        viewModelScope.launch { activePuzzleStore.save(GameKeys.KLONDIKE, SaveState.encode(snapshot)) }
    }
}

// ============================================================ Screen

class KlondikeScreen(sealedActivity: SealedLightActivity) :
    LightScreen<Unit, KlondikeScreenViewModel>(sealedActivity) {

    override val viewModelClass: Class<KlondikeScreenViewModel>
        get() = KlondikeScreenViewModel::class.java

    override fun createViewModel(): KlondikeScreenViewModel = KlondikeScreenViewModel(
        activePuzzleStore = ActivePuzzleStore(lightContext.dataStore),
        dailyPlaytimeStore = DailyPlaytimeStore(lightContext.dataStore),
    )

    @Composable
    override fun Content() {
        val table by viewModel.table.collectAsState()
        val canUndo by viewModel.canUndo.collectAsState()
        val remaining by viewModel.remainingSeconds.collectAsState()
        val isTimeUp by viewModel.timeUp.collectAsState()
        val themeColors by LightThemeController.colors.collectAsState()
        val game = table.game

        LightTheme(colors = themeColors) {
            val foreground = LightThemeTokens.colors.content
            val background = LightThemeTokens.colors.background
            val subdued = LightThemeTokens.colors.contentSecondary

            Column(
                modifier = Modifier.fillMaxSize().background(background),
            ) {
                LightTopBar(
                    leftButton = LightBarButton.LightIcon(icon = LightIcons.BACK, onClick = { goBack() }),
                    center = LightTopBarCenter.Text("Klondike"),
                    rightButton = LightBarButton.Text(text = formatClock(remaining), onClick = null),
                )

                if (isTimeUp && !game.isWon) {
                    Box(
                        Modifier.fillMaxSize().padding(2f.gridUnitsAsDp()),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            LightText(
                                text = "That's ${GameBudgets.KLONDIKE_SECONDS / 60} minutes of Klondike for today!",
                                variant = LightTextVariant.Heading,
                                align = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            LightText(
                                text = "Come back tomorrow for more.",
                                variant = LightTextVariant.Detail,
                                lighten = true,
                                align = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(top = 0.5f.gridUnitsAsDp()),
                            )
                        }
                    }
                    return@Column
                }

                LaunchedEffect(table.hint) {
                    if (table.hint != null) {
                        delay(HINT_MILLIS)
                        viewModel.clearHint()
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LightText(
                        text = "New",
                        variant = LightTextVariant.Detail,
                        modifier = Modifier.lightClickable { viewModel.newGame() },
                    )
                    LightText(
                        text = "Hint",
                        variant = LightTextVariant.Detail,
                        modifier = Modifier.lightClickable { viewModel.requestHint() },
                    )
                    LightText(
                        text = "${game.moves} moves",
                        variant = LightTextVariant.Detail,
                        lighten = true,
                    )
                    LightText(
                        text = "Undo",
                        variant = LightTextVariant.Detail,
                        lighten = !canUndo,
                        modifier = Modifier.lightClickable { viewModel.undo() },
                    )
                }

                BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                    val geometry = remember(maxWidth, maxHeight, game) {
                        geometryFor(maxWidth, maxHeight, game)
                    }
                    val slots = remember(geometry, game) { buildSlots(game, geometry) }
                    var drag by remember { mutableStateOf<DragState?>(null) }
                    val triggerHaptic = rememberLightHapticTrigger()
                    var emptied by remember(game) { mutableStateOf(List(Game.FOUNDATIONS) { 0 }) }

                    val animation = table.animation
                    val flightPlan = remember(animation?.id) {
                        animation?.let { flightFor(it, game, geometry, slots) }
                    }
                    val progress = remember(animation?.id) { Animatable(0f) }
                    var landed by remember(animation?.id) { mutableStateOf(false) }
                    val flight = if (landed) null else flightPlan

                    LaunchedEffect(animation?.id) {
                        if (flightPlan == null) return@LaunchedEffect
                        progress.animateTo(
                            targetValue = 1f,
                            animationSpec = tween(FLIGHT_MILLIS, easing = FastOutSlowInEasing),
                        )
                        landed = true
                    }

                    val hint = table.hint

                    for (slot in slots) {
                        val dragged = drag?.let { active ->
                            slot.pile == active.source && slot.cardIndex >= active.cardIndex
                        } == true
                        if (dragged) continue

                        val cardIndex = flight.visibleIndexFor(slot) ?: continue

                        val isHintSource = hint.marksSource(slot)
                        val isHintTarget = hint.marksTarget(slot, game)
                        val cardForeground = if (isHintSource) background else foreground
                        val cardBackground = if (isHintSource) foreground else background
                        val outline = if (isHintTarget) foreground else subdued
                        val borderW = if (isHintSource || isHintTarget) 2.dp else 1.dp
                        val placement = Modifier.offset(slot.x, slot.y)

                        when (val pile = slot.pile) {
                            Pile.Stock ->
                                if (cardIndex >= 0) {
                                    CardBack(
                                        width = geometry.cardW, height = geometry.cardH,
                                        foreground = foreground, background = background,
                                        modifier = placement, emphasized = isHintSource,
                                    )
                                } else {
                                    EmptySlot(geometry.cardW, geometry.cardH, outline, placement, borderW) {
                                        Box(Modifier.size(geometry.cardW * 0.30f).border(borderW, outline, CircleShape))
                                    }
                                }

                            Pile.Waste ->
                                if (cardIndex >= 0) {
                                    CardView(
                                        card = game.waste[cardIndex],
                                        width = geometry.cardW, height = geometry.cardH,
                                        foreground = cardForeground, background = cardBackground,
                                        modifier = placement, borderWidth = borderW,
                                    )
                                } else {
                                    EmptySlot(geometry.cardW, geometry.cardH, outline, placement, borderW)
                                }

                            is Pile.Foundation -> {
                                val index = cardIndex - emptied.getOrElse(pile.index) { 0 }
                                if (index >= 0) {
                                    CardView(
                                        card = game.foundations[pile.index][index],
                                        width = geometry.cardW, height = geometry.cardH,
                                        foreground = cardForeground, background = cardBackground,
                                        modifier = placement, borderWidth = borderW,
                                    )
                                } else {
                                    EmptySlot(geometry.cardW, geometry.cardH, outline, placement, borderW)
                                }
                            }

                            is Pile.Tableau -> {
                                val column = game.tableau[pile.index]
                                val entry = column.getOrNull(cardIndex)
                                when {
                                    entry == null ->
                                        EmptySlot(geometry.cardW, geometry.cardH, outline, placement, borderW)
                                    !entry.faceUp ->
                                        CardBack(
                                            width = geometry.cardW, height = geometry.cardH,
                                            foreground = foreground, background = background,
                                            modifier = placement,
                                        )
                                    else ->
                                        CardView(
                                            card = entry.card,
                                            width = geometry.cardW, height = geometry.cardH,
                                            foreground = cardForeground, background = cardBackground,
                                            modifier = placement,
                                            showCenterGlyph = cardIndex == column.lastIndex,
                                            borderWidth = borderW,
                                        )
                                }
                            }
                        }
                    }

                    flight?.let { active ->
                        val t = progress.value
                        val x = active.startX + (active.endX - active.startX) * t
                        val y = active.startY + (active.endY - active.startY) * t
                        active.cards.forEachIndexed { i, card ->
                            CardView(
                                card = card,
                                width = geometry.cardW, height = geometry.cardH,
                                foreground = foreground, background = background,
                                modifier = Modifier.offset(x, y + geometry.fanUp * i),
                                showCenterGlyph = i == active.cards.lastIndex,
                            )
                        }
                    }

                    drag?.let { active ->
                        active.cards.forEachIndexed { i, card ->
                            CardView(
                                card = card,
                                width = geometry.cardW, height = geometry.cardH,
                                foreground = foreground, background = background,
                                modifier = Modifier.offset(
                                    x = active.originX + active.dx,
                                    y = active.originY + active.dy + geometry.fanUp * i,
                                ),
                                showCenterGlyph = i == active.cards.lastIndex,
                            )
                        }
                    }

                    Box(
                        Modifier
                            .fillMaxSize()
                            .pointerInput(slots, triggerHaptic) {
                                detectTapGestures { offset ->
                                    val slot = slots.hit(offset.x.toDp(), offset.y.toDp())
                                    if (slot != null) {
                                        triggerHaptic()
                                        viewModel.tap(slot.pile, slot.cardIndex)
                                    }
                                }
                            }
                            .pointerInput(slots, triggerHaptic) {
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        val slot = slots.hit(offset.x.toDp(), offset.y.toDp())
                                        val state = viewModel.table.value.game
                                        if (slot != null && slot.cardIndex >= 0 &&
                                            state.isDraggable(slot.pile, slot.cardIndex)
                                        ) {
                                            triggerHaptic()
                                            drag = DragState(
                                                source = slot.pile, cardIndex = slot.cardIndex,
                                                cards = state.cardsAt(slot.pile, slot.cardIndex),
                                                originX = slot.x, originY = slot.y,
                                            )
                                        }
                                    },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        drag = drag?.let {
                                            it.copy(dx = it.dx + amount.x.toDp(), dy = it.dy + amount.y.toDp())
                                        }
                                    },
                                    onDragEnd = {
                                        val active = drag
                                        if (active != null) {
                                            val centerX = active.originX + active.dx + geometry.cardW / 2
                                            val centerY = active.originY + active.dy + geometry.cardH / 2
                                            val target = dropTarget(geometry, centerX, centerY)
                                            if (target != null) viewModel.drop(active.source, active.cardIndex, target)
                                        }
                                        drag = null
                                    },
                                    onDragCancel = { drag = null },
                                )
                            },
                    )

                    val notice = when {
                        table.notice == Notice.UNWINNABLE -> "This deal can't be won"
                        table.notice == Notice.DEAD_END -> "No moves left"
                        table.checking -> "Checking"
                        else -> null
                    }
                    if (notice != null && !game.isWon) {
                        LightText(
                            text = notice, variant = LightTextVariant.Detail, lighten = true,
                            modifier = Modifier.align(Alignment.BottomCenter).background(background)
                                .padding(horizontal = 10.dp, vertical = 2.dp),
                        )
                    }

                    if (game.isWon) {
                        var waterfallDone by remember(game) { mutableStateOf(false) }
                        if (!waterfallDone) {
                            VictoryWaterfall(
                                game = game, cardWidth = geometry.cardW, cardHeight = geometry.cardH,
                                foundationX = remember(geometry) {
                                    (0 until Game.FOUNDATIONS).map { geometry.columnX(3 + it) }
                                },
                                foundationY = 0.dp, boardWidth = maxWidth, boardHeight = maxHeight,
                                foreground = foreground, background = background,
                                onLaunchedChange = { emptied = it },
                                onFinished = { waterfallDone = true },
                            )
                            Box(Modifier.fillMaxSize().lightClickable { waterfallDone = true })
                        } else {
                            Box(
                                modifier = Modifier.fillMaxSize().background(background),
                                contentAlignment = Alignment.Center,
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    LightText(text = "You win", variant = LightTextVariant.Heading)
                                    LightText(
                                        text = "${game.moves} moves", variant = LightTextVariant.Detail,
                                        lighten = true, modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
                                    )
                                    LightText(
                                        text = "Deal again", variant = LightTextVariant.Copy,
                                        modifier = Modifier.lightClickable { viewModel.newGame() },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- hint marking

private fun Action?.marksSource(slot: Slot): Boolean = when (this) {
    null -> false
    Action.Draw -> slot.pile == Pile.Stock
    is Action.Shift -> slot.pile == source && slot.cardIndex >= cardIndex
    is Action.TurnOver -> slot.pile == Pile.Tableau(column)
}

private fun Action?.marksTarget(slot: Slot, game: Game): Boolean {
    val shift = this as? Action.Shift ?: return false
    if (slot.pile != shift.destination) return false
    return slot.cardIndex == game.topIndexOf(shift.destination)
}

private fun Game.topIndexOf(pile: Pile): Int = when (pile) {
    Pile.Stock -> stock.lastIndex
    Pile.Waste -> waste.lastIndex
    is Pile.Foundation -> foundations[pile.index].lastIndex
    is Pile.Tableau -> tableau[pile.index].lastIndex
}

// ---------------------------------------------------------------- layout

private data class TableGeometry(
    val cardW: Dp,
    val cardH: Dp,
    val gap: Dp,
    val padding: Dp,
    val tableauY: Dp,
    val fanUp: Dp,
    val fanDown: Dp,
) {
    fun columnX(index: Int): Dp = padding + (cardW + gap) * index
}

private data class Slot(
    val pile: Pile,
    val cardIndex: Int,
    val x: Dp,
    val y: Dp,
    val w: Dp,
    val h: Dp,
)

private data class DragState(
    val source: Pile,
    val cardIndex: Int,
    val cards: List<Card>,
    val originX: Dp,
    val originY: Dp,
    val dx: Dp = 0.dp,
    val dy: Dp = 0.dp,
)

private data class Flight(
    val cards: List<Card>,
    val destination: Pile,
    val firstLandedIndex: Int,
    val startX: Dp,
    val startY: Dp,
    val endX: Dp,
    val endY: Dp,
)

private fun flightFor(
    animation: MoveAnimation, game: Game, geometry: TableGeometry, slots: List<Slot>,
): Flight? {
    if (animation.cards.isEmpty()) return null
    val landedFrom = game.pileSize(animation.destination) - animation.cards.size
    if (landedFrom < 0) return null
    val target = slots.firstOrNull {
        it.pile == animation.destination && it.cardIndex == landedFrom
    } ?: return null

    val startX: Dp
    val startY: Dp
    when (val source = animation.source) {
        is Pile.Tableau -> {
            startX = geometry.columnX(source.index)
            var y = geometry.tableauY
            for (entry in game.tableau[source.index]) {
                y += if (entry.faceUp) geometry.fanUp else geometry.fanDown
            }
            startY = y
        }
        else -> {
            val origin = slots.firstOrNull { it.pile == source } ?: return null
            startX = origin.x
            startY = origin.y
        }
    }
    return Flight(animation.cards, animation.destination, landedFrom, startX, startY, target.x, target.y)
}

private fun Flight?.visibleIndexFor(slot: Slot): Int? = visibleCardIndex(
    pile = slot.pile, cardIndex = slot.cardIndex,
    landing = this?.destination, firstLandedIndex = this?.firstLandedIndex ?: 0,
)

private fun Game.pileSize(pile: Pile): Int = when (pile) {
    Pile.Stock -> stock.size
    Pile.Waste -> waste.size
    is Pile.Foundation -> foundations[pile.index].size
    is Pile.Tableau -> tableau[pile.index].size
}

private fun geometryFor(width: Dp, height: Dp, game: Game): TableGeometry {
    val padding = 6.dp
    val gap = 4.dp
    val cardW = (width - padding * 2 - gap * 6) / 7
    val cardH = cardW * 1.42f
    val tableauY = cardH + 14.dp

    var fanUp = cardH * 0.30f
    var fanDown = cardH * 0.15f

    val tallest = game.tableau.maxOfOrNull { column ->
        val down = column.count { !it.faceUp }
        val up = column.size - down
        if (up > 0) down * fanDown.value + (up - 1) * fanUp.value
        else maxOf(0f, (down - 1) * fanDown.value)
    } ?: 0f

    val room = (height - tableauY - cardH - 4.dp).value
    if (tallest > room && tallest > 0f) {
        val scale = (room / tallest).coerceIn(0.30f, 1f)
        fanUp *= scale
        fanDown *= scale
    }
    return TableGeometry(cardW, cardH, gap, padding, tableauY, fanUp, fanDown)
}

private fun buildSlots(game: Game, geometry: TableGeometry): List<Slot> {
    val slots = ArrayList<Slot>(64)
    fun add(pile: Pile, cardIndex: Int, x: Dp, y: Dp) {
        slots.add(Slot(pile, cardIndex, x, y, geometry.cardW, geometry.cardH))
    }
    add(Pile.Stock, game.stock.lastIndex, geometry.columnX(0), 0.dp)
    add(Pile.Waste, game.waste.lastIndex, geometry.columnX(1), 0.dp)
    for (i in 0 until Game.FOUNDATIONS) {
        add(Pile.Foundation(i), game.foundations[i].lastIndex, geometry.columnX(3 + i), 0.dp)
    }
    for (col in 0 until Game.COLUMNS) {
        val column = game.tableau[col]
        val x = geometry.columnX(col)
        if (column.isEmpty()) { add(Pile.Tableau(col), -1, x, geometry.tableauY); continue }
        var y = geometry.tableauY
        column.forEachIndexed { index, entry ->
            add(Pile.Tableau(col), index, x, y)
            y += if (entry.faceUp) geometry.fanUp else geometry.fanDown
        }
    }
    return slots
}

private fun List<Slot>.hit(x: Dp, y: Dp): Slot? = lastOrNull {
    x >= it.x && x < it.x + it.w && y >= it.y && y < it.y + it.h
}

private fun dropTarget(geometry: TableGeometry, x: Dp, y: Dp): Pile? {
    val slack = geometry.gap / 2
    if (y < geometry.tableauY - 7.dp) {
        for (i in 0 until Game.FOUNDATIONS) {
            val left = geometry.columnX(3 + i)
            if (x >= left - slack && x < left + geometry.cardW + slack) return Pile.Foundation(i)
        }
        return null
    }
    for (i in 0 until Game.COLUMNS) {
        val left = geometry.columnX(i)
        if (x >= left - slack && x < left + geometry.cardW + slack) return Pile.Tableau(i)
    }
    return null
}

private fun formatClock(totalSeconds: Int): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
