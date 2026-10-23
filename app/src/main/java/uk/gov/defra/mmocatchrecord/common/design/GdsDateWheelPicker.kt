@file:Suppress("detekt.FunctionNaming", "detekt.LongParameterList", "detekt.MagicNumber")

package uk.gov.defra.mmocatchrecord.common.design

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import uk.gov.defra.mmocatchrecord.R
import java.time.LocalDate
import java.time.Month
import java.time.YearMonth
import java.time.format.TextStyle
import kotlin.math.roundToInt

data class GdsDateWheelPickerTestTags(
    val monthWheel: String,
    val dayWheel: String,
    val yearWheel: String,
)

private const val VISIBLE_ITEMS = 3
private val WheelTextSize = 20.sp
private val MonthWheelWidth = 84.dp
private val DayWheelWidth = 56.dp
private val YearWheelWidth = 84.dp

/** Month / Day / Year wheel date picker; the wheel value is the selection (no Ok/Cancel).
 * TalkBack adjusts each wheel like a slider; tapping the row above/below avoids dragging (WCAG 2.5.7). */
@Suppress("FunctionNaming", "LongParameterList")
@Composable
fun GdsDateWheelPicker(
    value: LocalDate,
    onValueChange: (LocalDate) -> Unit,
    yearRange: IntRange,
    testTags: GdsDateWheelPickerTestTags,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
) {
    val locale = appLocale()
    val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    val daysInMonth = YearMonth.of(value.year, value.monthValue).lengthOfMonth()

    fun update(
        year: Int = value.year,
        month: Int = value.monthValue,
        day: Int = value.dayOfMonth,
    ) {
        val maxDay = YearMonth.of(year, month).lengthOfMonth()
        onValueChange(LocalDate.of(year, month, day.coerceAtMost(maxDay)))
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        // Widths grow with font scale and FlowRow wraps instead of clipping at large text sizes (WCAG 1.4.4).
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            DateWheel(
                label = stringResource(R.string.date_month_label),
                values = (1..12).toList(),
                selected = value.monthValue,
                displayText = { Month.of(it).getDisplayName(TextStyle.SHORT, locale) },
                spokenText = { Month.of(it).getDisplayName(TextStyle.FULL_STANDALONE, locale) },
                onSelectedChange = { update(month = it) },
                testTag = testTags.monthWheel,
                modifier =
                    Modifier
                        .width(MonthWheelWidth * fontScale)
                        .then(focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier),
            )
            DateWheel(
                label = stringResource(R.string.date_day_label),
                values = (1..daysInMonth).toList(),
                selected = value.dayOfMonth,
                displayText = { it.toString().padStart(2, '0') },
                spokenText = { it.toString() },
                onSelectedChange = { update(day = it) },
                testTag = testTags.dayWheel,
                modifier = Modifier.width(DayWheelWidth * fontScale),
            )
            DateWheel(
                label = stringResource(R.string.date_year_label),
                values = yearRange.toList(),
                selected = value.year,
                displayText = { it.toString() },
                spokenText = { it.toString() },
                onSelectedChange = { update(year = it) },
                testTag = testTags.yearWheel,
                modifier = Modifier.width(YearWheelWidth * fontScale),
            )
        }
    }
}

private fun wheelKeyStep(event: KeyEvent): Int =
    when (event.key) {
        Key.DirectionUp -> -1
        Key.DirectionDown -> 1
        else -> 0
    }

private fun Modifier.wheelSemantics(
    label: String,
    values: List<Int>,
    selected: Int,
    spokenText: (Int) -> String,
    onSelectedChange: (Int) -> Unit,
): Modifier =
    clearAndSetSemantics {
        contentDescription = label
        stateDescription = spokenText(selected)
        progressBarRangeInfo =
            ProgressBarRangeInfo(
                current = selected.toFloat(),
                range = values.first().toFloat()..values.last().toFloat(),
                steps = (values.size - 2).coerceAtLeast(0),
            )
        setProgress { target ->
            val newValue = target.roundToInt().coerceIn(values.first(), values.last())
            if (newValue != selected) onSelectedChange(newValue)
            true
        }
    }

@Suppress("FunctionNaming", "LongParameterList", "LongMethod")
@Composable
private fun DateWheel(
    label: String,
    values: List<Int>,
    selected: Int,
    displayText: (Int) -> String,
    spokenText: (Int) -> String,
    onSelectedChange: (Int) -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val itemHeight: Dp = maxOf(Spacing.minTouchTarget, with(density) { (WheelTextSize * 1.8f).toDp() })
    val itemHeightPx = with(density) { itemHeight.toPx() }
    val selectedIndex = values.indexOf(selected).coerceAtLeast(0)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex)
    val currentValues by rememberUpdatedState(values)
    val currentOnSelectedChange by rememberUpdatedState(onSelectedChange)
    val currentSelected by rememberUpdatedState(selected)

    fun centredIndex(): Int {
        val extra = if (listState.firstVisibleItemScrollOffset > itemHeightPx / 2) 1 else 0
        return (listState.firstVisibleItemIndex + extra).coerceIn(0, currentValues.lastIndex)
    }

    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .distinctUntilChanged()
            .filter { !it }
            .collect {
                val settled = currentValues[centredIndex()]
                if (settled != currentSelected) currentOnSelectedChange(settled)
            }
    }
    LaunchedEffect(selectedIndex, values.size) {
        if (!listState.isScrollInProgress && centredIndex() != selectedIndex) {
            listState.animateScrollToItem(selectedIndex)
        }
    }

    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Box(
        modifier =
            modifier
                .height(itemHeight * VISIBLE_ITEMS)
                .onKeyEvent { event ->
                    val step = wheelKeyStep(event)
                    if (step == 0 || event.type != KeyEventType.KeyDown) return@onKeyEvent step != 0
                    values.getOrNull(selectedIndex + step)?.let(onSelectedChange)
                    true
                }.focusable(interactionSource = interactionSource)
                .testTag(testTag)
                .wheelSemantics(label, values, selected, spokenText, onSelectedChange),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .align(Alignment.Center)
                    .height(itemHeight)
                    .background(if (isFocused) MmoColors.FocusYellow else Color.Transparent),
        ) {
            HorizontalDivider(thickness = 2.dp, color = MmoColors.Text)
            Box(modifier = Modifier.weight(1f))
            HorizontalDivider(thickness = 2.dp, color = MmoColors.Text)
        }
        LazyColumn(
            state = listState,
            flingBehavior = rememberSnapFlingBehavior(listState),
            contentPadding = PaddingValues(vertical = itemHeight),
            modifier = Modifier.fillMaxWidth().height(itemHeight * VISIBLE_ITEMS),
        ) {
            itemsIndexed(values) { index, item ->
                Box(
                    contentAlignment = Alignment.Center,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(itemHeight)
                            .focusProperties { canFocus = false }
                            .clickable { if (item != selected) onSelectedChange(item) },
                ) {
                    Text(
                        text = displayText(item),
                        color = if (index == selectedIndex) MmoColors.Text else MmoColors.Grey1,
                        fontSize = WheelTextSize,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }
    }
}
