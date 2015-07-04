/**
 * Copyright (c) 2012 Todoroo Inc
 *
 * See the file "LICENSE" for the full license governing this code.
 */
package com.todoroo.astrid.ui;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.view.View;
import android.view.Window;
import android.view.WindowManager.LayoutParams;
import android.widget.Button;

import org.tasks.R;
import org.tasks.preferences.ActivityPreferences;

public class DateAndTimeDialog extends Dialog {

    public interface DateAndTimeDialogListener {
        void onDateAndTimeSelected(long date);
    }

    private final DateAndTimePicker dateAndTimePicker;
    private boolean cancelled = false;

    private DateAndTimeDialogListener listener;

    public DateAndTimeDialog(ActivityPreferences preferences, Context context, long startDate) {
        this(preferences, context, startDate, R.layout.date_time_dialog, 0);
    }

    public DateAndTimeDialog(ActivityPreferences preferences, Context context, long startDate, int contentView, int title) {
        super(context, preferences.getEditDialogTheme());

        if (title == 0) {
            requestWindowFeature(Window.FEATURE_NO_TITLE);
        } else {
            setTitle(title);
        }
        /** Design the dialog in main.xml file */
        setContentView(contentView);

        LayoutParams params = getWindow().getAttributes();
        params.height = LayoutParams.FILL_PARENT;
        params.width = LayoutParams.FILL_PARENT;
        getWindow().setAttributes(params);

        dateAndTimePicker = (DateAndTimePicker) findViewById(R.id.date_and_time);
        dateAndTimePicker.initializeWithDate(startDate);

        Button okButton = (Button) findViewById(R.id.ok);
        Button cancelButton = (Button) findViewById(R.id.cancel);

        okButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dismiss();
                if (listener != null) {
                    listener.onDateAndTimeSelected(dateAndTimePicker.constructDueDate());
                }
            }
        });

        cancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cancelled = true;
                cancel();
            }
        });

        setOnCancelListener(new OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                if (!cancelled) { // i.e. if back button pressed, which we treat as an "OK"
                    if (listener != null) {
                        listener.onDateAndTimeSelected(dateAndTimePicker.constructDueDate());
                    }
                } else {
                    cancelled = false; // reset
                }
            }
        });
    }

    public void setDateAndTimeDialogListener(DateAndTimeDialogListener listener) {
        this.listener = listener;
    }

    public void setSelectedDateAndTime(long date) {
        dateAndTimePicker.initializeWithDate(date);
    }

    public boolean hasTime() {
        return dateAndTimePicker.hasTime();
    }
}
