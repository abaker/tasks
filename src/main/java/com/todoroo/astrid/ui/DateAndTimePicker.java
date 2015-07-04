/**
 * Copyright (c) 2012 Todoroo Inc
 *
 * See the file "LICENSE" for the full license governing this code.
 */
package com.todoroo.astrid.ui;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ToggleButton;

import com.todoroo.andlib.utility.DateUtilities;
import com.todoroo.astrid.data.Task;
import com.todoroo.astrid.ui.AstridTimePicker.TimePickerEnabledChangedListener;
import com.todoroo.astrid.ui.CalendarView.OnSelectedDateListener;

import org.tasks.R;

import java.util.ArrayList;
import java.util.Date;

import static org.tasks.date.DateTimeUtils.newDate;

public class DateAndTimePicker extends LinearLayout {

    private static final int SHORTCUT_PADDING = 8;

    private class UrgencyValue {
        public String label;
        public int setting;
        public long dueDate;

        public UrgencyValue(String label, int setting) {
            this.label = label;
            this.setting = setting;
            dueDate = Task.createDueDate(setting, 0);
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private final CalendarView calendarView;
    private final AstridTimePicker timePicker;
    private final LinearLayout dateShortcuts;
    private UrgencyValue todayUrgency;

    public DateAndTimePicker(Context context, AttributeSet attrs) {
        super(context, attrs);
        LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        inflater.inflate(R.layout.date_time_picker_no_shortcuts, this, true);

        calendarView = (CalendarView) findViewById(R.id.calendar);
        timePicker = (AstridTimePicker) findViewById(R.id.time_picker);

        findViewById(R.id.date_shortcuts).setVisibility(View.GONE);
        dateShortcuts = (LinearLayout) timePicker.findViewById(R.id.date_shortcuts);

        setUpListeners();
        constructShortcutList(context, attrs);
    }

    public void initializeWithDate(long dateValue) {
        Date date = newDate(dateValue);
        Date forCalendar;
        if (dateValue> 0) {
            forCalendar = getDateForCalendar(date);
        } else {
            forCalendar = date;
        }
        calendarView.setCalendarDate(forCalendar);
        if (Task.hasDueTime(dateValue)) {
            timePicker.setHours(date.getHours());
            timePicker.setMinutes(date.getMinutes());
            timePicker.setHasTime(true);
        } else {
            timePicker.setHours(18);
            timePicker.setMinutes(0);
            timePicker.setHasTime(false);
        }
        updateShortcutView(forCalendar);
    }

    private Date getDateForCalendar(Date date) {
        Date forCalendar = newDate(date.getTime() / 1000L * 1000L);
        forCalendar.setHours(12);
        forCalendar.setMinutes(0);
        forCalendar.setSeconds(0);
        return forCalendar;
    }

    private void setUpListeners() {
        calendarView.setOnSelectedDateListener(new OnSelectedDateListener() {
            @Override
            public void onSelectedDate(Date date) {
                updateShortcutView(date);
            }
        });

        timePicker.setTimePickerEnabledChangedListener(new TimePickerEnabledChangedListener() {
            @Override
            public void timePickerEnabledChanged(boolean hasTime) {
                if (hasTime) {
                    forceDateSelected();
                }
            }
        });
    }

    private void forceDateSelected() {
        ToggleButton none = (ToggleButton) dateShortcuts.getChildAt(dateShortcuts.getChildCount() - 1);
        if (none.isChecked()) {
            Date date = newDate(todayUrgency.dueDate);
            calendarView.setCalendarDate(date);
            calendarView.invalidate();
            if (todayUrgency.setting == Task.URGENCY_NONE) {
                timePicker.forceNoTime();
            }
            updateShortcutView(date);
        }
    }

    private void constructShortcutList(Context context, AttributeSet attrs) {
        int arrayResource = R.array.TEA_urgency;
        TypedArray t = context.obtainStyledAttributes(attrs, R.styleable.DateAndTimePicker);
        for (int i = 0; i < t.getIndexCount(); i++) {
            int attr = t.getIndex(i);
            switch(attr) {
            case R.styleable.DateAndTimePicker_shortcutLabels:
                arrayResource = t.getResourceId(attr, R.array.TEA_urgency);
            }
        }

        String[] labels = context.getResources().getStringArray(arrayResource);
        ArrayList<UrgencyValue> urgencyValues = new ArrayList<>();
        todayUrgency = new UrgencyValue(labels[2],
                Task.URGENCY_TODAY);
        urgencyValues.add(new UrgencyValue(labels[0],
                Task.URGENCY_NONE));

        Resources r = context.getResources();
        DisplayMetrics metrics = r.getDisplayMetrics();
        TypedValue onColor = new TypedValue();
        context.getTheme().resolveAttribute(R.attr.asThemeTextColor, onColor, false);

        int onColorValue = r.getColor(onColor.data);
        int offColorValue = r.getColor(android.R.color.transparent);
        int borderColorValue = r.getColor(android.R.color.transparent);
        int cornerRadius = (int) (5 * r.getDisplayMetrics().density);
        int strokeWidth = (int) (1 * r.getDisplayMetrics().density);

        for (int i = 0; i < urgencyValues.size(); i++) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LayoutParams.FILL_PARENT, (int) ((38) * metrics.density), 0);
            UrgencyValue uv = urgencyValues.get(i);

            ToggleButton tb = new ToggleButton(context);
            String label = uv.label;
            tb.setTextOff(label);
            tb.setTextOn(label);
            tb.setTag(uv);
            if (i == 0) {
                tb.setBackgroundDrawable(CustomBorderDrawable.customButton(cornerRadius, cornerRadius, cornerRadius, cornerRadius, onColorValue, offColorValue, borderColorValue, strokeWidth));
            } else if (i == urgencyValues.size() - 2) {
                lp.topMargin = (int) (-1 * metrics.density);
                tb.setBackgroundDrawable(CustomBorderDrawable.customButton(cornerRadius, cornerRadius, cornerRadius, cornerRadius, onColorValue, offColorValue, borderColorValue, strokeWidth));
            } else if (i == urgencyValues.size() - 1) {
                lp.topMargin = (int) (5 * metrics.density);
                tb.setBackgroundDrawable(CustomBorderDrawable.customButton(cornerRadius, cornerRadius, cornerRadius, cornerRadius, onColorValue, offColorValue, borderColorValue, strokeWidth));
            } else {
                lp.topMargin = (int) (-1 * metrics.density);
                tb.setBackgroundDrawable(CustomBorderDrawable.customButton(cornerRadius, cornerRadius, cornerRadius, cornerRadius, onColorValue, offColorValue, borderColorValue, strokeWidth));
            }
            int verticalPadding = (int) (SHORTCUT_PADDING * metrics.density);
            tb.setPadding(0, verticalPadding, 0, verticalPadding);
            tb.setLayoutParams(lp);
            tb.setGravity(Gravity.CENTER);
            tb.setTextSize(18);
            tb.setTextColor(context.getResources().getColorStateList(R.color.task_edit_toggle_button_text));

            tb.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    UrgencyValue value = (UrgencyValue) v.getTag();
                    Date date = newDate(value.dueDate);
                    calendarView.setCalendarDate(date);
                    calendarView.invalidate();
                    if (value.setting == Task.URGENCY_NONE) {
                        timePicker.forceNoTime();
                    }
                    updateShortcutView(date);
                }
            });
            dateShortcuts.addView(tb);
        }
    }

    public boolean hasTime() {
        return timePicker.hasTime();
    }

    private void updateShortcutView(Date date) {
        for (int i = 0; i < dateShortcuts.getChildCount(); i++) {
            View child = dateShortcuts.getChildAt(i);
            if (child instanceof ToggleButton) {
                ToggleButton tb = (ToggleButton) child;
                UrgencyValue uv = (UrgencyValue) tb.getTag();
                if (uv != null) {
                    if (uv.dueDate == date.getTime()) {
                        tb.setChecked(true);
                    } else {
                        tb.setChecked(false);
                    }
                }
            }
        }
    }

    public long constructDueDate() {
        Date calendarDate = newDate(calendarView.getCalendarDate().getTime());
        if (timePicker.hasTime() && calendarDate.getTime() > 0) {
            calendarDate.setHours(timePicker.getHours());
            calendarDate.setMinutes(timePicker.getMinutes());
            calendarDate.setSeconds(1);
        } else {
            calendarDate.setSeconds(0);
        }
        return calendarDate.getTime();
    }

    public boolean isAfterNow() {
        long dueDate = constructDueDate();
        return dueDate > DateUtilities.now();
    }

    public String getDisplayString(Context context) {
        long dueDate = constructDueDate();
        return getDisplayString(context, dueDate, false, false);
    }

    public static String getDisplayString(Context context, long forDate, boolean hideYear, boolean hideTime) {
        StringBuilder displayString = new StringBuilder();
        Date d = newDate(forDate);
        if (d.getTime() > 0) {
            if (hideYear) {
                displayString.append(DateUtilities.getDateStringHideYear(d));
            } else {
                displayString.append(DateUtilities.getDateString(d));
            }
            if (Task.hasDueTime(forDate) && !hideTime) {
                displayString.append(", "); //$NON-NLS-1$ //$NON-NLS-2$
                displayString.append(DateUtilities.getTimeString(context, d));
            }
        }
        return displayString.toString();
    }

    public static String getDisplayString(Context context, long forDate) {
        return getDisplayString(context, forDate, false, false);
    }

}
