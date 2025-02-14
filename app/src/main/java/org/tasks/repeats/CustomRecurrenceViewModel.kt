package org.tasks.repeats

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import net.fortuna.ical4j.model.Date
import net.fortuna.ical4j.model.Recur
import net.fortuna.ical4j.model.Recur.Frequency.DAILY
import net.fortuna.ical4j.model.Recur.Frequency.HOURLY
import net.fortuna.ical4j.model.Recur.Frequency.MINUTELY
import net.fortuna.ical4j.model.Recur.Frequency.MONTHLY
import net.fortuna.ical4j.model.Recur.Frequency.WEEKLY
import net.fortuna.ical4j.model.Recur.Frequency.YEARLY
import net.fortuna.ical4j.model.WeekDay
import net.fortuna.ical4j.model.WeekDayList
import net.fortuna.ical4j.model.property.RRule
import org.tasks.data.entity.CaldavAccount.Companion.TYPE_MICROSOFT
import org.tasks.date.DateTimeUtils.toDateTime
import org.tasks.repeats.CustomRecurrenceActivity.Companion.EXTRA_ACCOUNT_TYPE
import org.tasks.repeats.CustomRecurrenceActivity.Companion.EXTRA_DATE
import org.tasks.repeats.CustomRecurrenceActivity.Companion.EXTRA_RRULE
import org.tasks.time.DateTime
import org.tasks.time.DateTimeUtils2.currentTimeMillis
import org.tasks.time.startOfDay
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.WeekFields
import java.util.Calendar
import java.util.Calendar.DAY_OF_WEEK_IN_MONTH
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class CustomRecurrenceViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    locale: Locale,
) : ViewModel() {
    data class ViewState(
        val interval: Int = 1,
        val frequency: Recur.Frequency = WEEKLY,
        val dueDate: Long = currentTimeMillis().startOfDay(),
        val endSelection: Int = 0,
        val endDate: Long = dueDate.toDateTime().plusMonths(1).startOfDay().millis,
        val endCount: Int = 1,
        val frequencyOptions: List<Recur.Frequency> = FREQ_ALL,
        val daysOfWeek: List<DayOfWeek> = Locale.getDefault().daysOfWeek(),
        val selectedDays: List<DayOfWeek> = emptyList(),
        val locale: Locale = Locale.getDefault(),
        val monthDay: WeekDay? = null,
        val isMicrosoftTask: Boolean = false,
    ) {
        val dueDayOfWeek: DayOfWeek
            get() = Instant.ofEpochMilli(dueDate).atZone(ZoneId.systemDefault()).dayOfWeek

        val dueDayOfMonth: Int
            get() = DateTime(dueDate).dayOfMonth

        val nthWeek: Int
            get() =
                Calendar.getInstance(locale)
                    .apply { timeInMillis = dueDate }
                    .get(DAY_OF_WEEK_IN_MONTH)

        val lastWeekDayOfMonth: Boolean
            get() =
                Calendar.getInstance(locale)
                    .apply { timeInMillis = dueDate }
                    .let { it[DAY_OF_WEEK_IN_MONTH] == it.getActualMaximum(DAY_OF_WEEK_IN_MONTH) }
    }

    private val _state = MutableStateFlow(ViewState())
    val state = _state.asStateFlow()

    init {
        val daysOfWeek = locale.daysOfWeek()
        val recur = savedStateHandle.get<String>(EXTRA_RRULE)
            ?.takeIf { it.isNotBlank() }
            ?.let { RRule(it) }
            ?.recur
        val dueDate = savedStateHandle
            .get<Long>(EXTRA_DATE)
            ?.takeIf { it > 0 }
            ?: currentTimeMillis().startOfDay()
        val isMicrosoftTask = savedStateHandle.get<Int>(EXTRA_ACCOUNT_TYPE) == TYPE_MICROSOFT
        val frequencies = if (isMicrosoftTask) FREQ_MICROSOFT else FREQ_ALL
        _state.update { state ->
            state.copy(
                interval = recur?.interval?.takeIf { it > 0 } ?: 1,
                frequency = recur?.frequency?.takeIf { frequencies.contains(it) } ?: WEEKLY,
                dueDate = dueDate,
                endSelection = when {
                    isMicrosoftTask -> 0
                    recur == null -> 0
                    recur.until != null -> 1
                    recur.count >= 0 -> 2
                    else -> 0
                },
                endDate = DateTime(dueDate).plusMonths(1).startOfDay().millis,
                endCount = recur?.count?.takeIf { it >= 0 } ?: 1,
                daysOfWeek = daysOfWeek,
                selectedDays = recur
                    ?.dayList
                    ?.takeIf { recur.frequency == WEEKLY }
                    ?.toDaysOfWeek()
                    ?: emptyList(),
                locale = locale,
                monthDay = recur
                    ?.dayList
                    ?.takeIf { recur.frequency == MONTHLY && !isMicrosoftTask }
                    ?.firstOrNull(),
                isMicrosoftTask = isMicrosoftTask,
                frequencyOptions = frequencies,
            )
        }
    }

    fun setEndType(endType: Int) {
        _state.update {
            it.copy(endSelection = endType)
        }
    }

    fun setFrequency(frequency: Recur.Frequency) {
        _state.update {
            it.copy(frequency = frequency)
        }
    }

    fun setEndDate(endDate: Long) {
        _state.update {
            it.copy(endDate = endDate)
        }
    }

    fun setInterval(interval: Int) {
        _state.update {
            it.copy(interval = interval)
        }
    }

    fun setOccurrences(occurrences: Int) {
        _state.update {
            it.copy(endCount = occurrences)
        }
    }

    fun toggleDay(dayOfWeek: DayOfWeek) {
        _state.update { state ->
            state.copy(
                selectedDays = state.selectedDays.toMutableList().also {
                    if (!it.remove(dayOfWeek)) {
                        it.add(dayOfWeek)
                    }
                }
            )
        }
    }

    fun getRecur(): String {
        val state = _state.value
        val builder = Recur.Builder().frequency(state.frequency)
        if (state.frequency == WEEKLY) {
            builder.dayList(state.selectedDays.toWeekDayList())
        } else if (state.frequency == MONTHLY) {
            state.monthDay?.let { builder.dayList(WeekDayList(it)) }
        }
        if (state.interval > 1) {
            builder.interval(state.interval)
        }
        when (state.endSelection) {
            // 1 -> builder.until(Date(state.endDate))
            // builder.until expects that Date() is in local timezone and strips it, which effectively
            // equivalent to decrementing the "endDate" value by TimeZone.offset. This changes the date
            // to the previous day in timezones to the East of GMT, so this value shall be pre-shifted
            1 -> builder.until(Date(DateTime(state.endDate).let { it.millis + it.offset }))
            2 -> builder.count(state.endCount.coerceAtLeast(1))
        }
        return builder.build().toString()
    }

    fun setMonthSelection(selection: Int) {
        _state.update {
            it.copy(
                monthDay = when (selection) {
                    0 -> null
                    1 -> WeekDay(it.dueDayOfWeek.weekDay, it.nthWeek)
                    2 -> WeekDay(it.dueDayOfWeek.weekDay, -1)
                    else -> throw IllegalArgumentException()
                },
            )
        }
    }

    companion object {
        val FREQ_ALL = listOf(MINUTELY, HOURLY, DAILY, WEEKLY, MONTHLY, YEARLY)
        val FREQ_MICROSOFT = listOf(DAILY, WEEKLY, MONTHLY, YEARLY)

        private fun Locale.daysOfWeek(): List<DayOfWeek> {
            val values = DayOfWeek.values()
            val weekFields = WeekFields.of(this)
            var index = values.indexOf(weekFields.firstDayOfWeek)
            return (0..6).map {
                values[index].also { index = (index + 1) % 7 }
            }
        }

        private fun WeekDayList.toDaysOfWeek(): List<DayOfWeek> = map {
            when (it) {
                WeekDay.SU -> DayOfWeek.SUNDAY
                WeekDay.MO -> DayOfWeek.MONDAY
                WeekDay.TU -> DayOfWeek.TUESDAY
                WeekDay.WE -> DayOfWeek.WEDNESDAY
                WeekDay.TH -> DayOfWeek.THURSDAY
                WeekDay.FR -> DayOfWeek.FRIDAY
                WeekDay.SA -> DayOfWeek.SATURDAY
                else -> throw IllegalArgumentException()
            }
        }

        private fun List<DayOfWeek>.toWeekDayList(): WeekDayList =
            WeekDayList(*sortedBy { it.value }.map { it.weekDay }.toTypedArray())

        private val DayOfWeek.weekDay: WeekDay
            get() = when (this) {
                DayOfWeek.SUNDAY -> WeekDay.SU
                DayOfWeek.MONDAY -> WeekDay.MO
                DayOfWeek.TUESDAY -> WeekDay.TU
                DayOfWeek.WEDNESDAY -> WeekDay.WE
                DayOfWeek.THURSDAY -> WeekDay.TH
                DayOfWeek.FRIDAY -> WeekDay.FR
                DayOfWeek.SATURDAY -> WeekDay.SA
                else -> throw IllegalArgumentException()
            }
    }
}
