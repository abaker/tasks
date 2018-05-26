package org.tasks.activities;

import static com.google.common.collect.Lists.transform;

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.widget.Toast;
import com.google.common.base.Strings;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import org.tasks.R;
import org.tasks.calendars.AndroidCalendar;
import org.tasks.calendars.CalendarProvider;
import org.tasks.dialogs.DialogBuilder;
import org.tasks.injection.DialogFragmentComponent;
import org.tasks.injection.InjectingDialogFragment;
import org.tasks.preferences.PermissionChecker;
import org.tasks.themes.Theme;
import org.tasks.ui.SingleCheckedArrayAdapter;

public class CalendarSelectionDialog extends InjectingDialogFragment {

  private static final String EXTRA_SELECTED = "extra_selected";
  private final List<AndroidCalendar> calendars = new ArrayList<>();
  @Inject DialogBuilder dialogBuilder;
  @Inject CalendarProvider calendarProvider;
  @Inject PermissionChecker permissionChecker;
  @Inject Theme theme;
  private CalendarSelectionHandler handler;
  private SingleCheckedArrayAdapter adapter;

  public static CalendarSelectionDialog newCalendarSelectionDialog(String selected) {
    CalendarSelectionDialog dialog = new CalendarSelectionDialog();
    Bundle arguments = new Bundle();
    arguments.putString(EXTRA_SELECTED, selected);
    dialog.setArguments(arguments);
    return dialog;
  }

  @NonNull
  @Override
  public Dialog onCreateDialog(Bundle savedInstanceState) {
    Bundle arguments = getArguments();
    String selected = arguments.getString(EXTRA_SELECTED);

    theme.applyToContext(getActivity());

    int selectedIndex = -1;
    calendars.clear();
    calendars.addAll(calendarProvider.getCalendars());
    if (calendars.isEmpty()) {
      Toast.makeText(getActivity(), R.string.no_calendars_found, Toast.LENGTH_LONG).show();
      handler.cancel();
    } else {
      calendars.add(0, new AndroidCalendar(null, getString(R.string.dont_add_to_calendar), -1));
      List<String> calendarNames = transform(calendars, AndroidCalendar::getName);
      adapter =
          new SingleCheckedArrayAdapter(getActivity(), calendarNames, theme.getThemeAccent()) {
            @Override
            protected int getDrawable(int position) {
              return R.drawable.ic_event_24dp;
            }

            @Override
            protected int getDrawableColor(int position) {
              return calendars.get(position).getColor();
            }
          };
      selectedIndex = Strings.isNullOrEmpty(selected) ? 0 : calendarNames.indexOf(selected);
    }

    return dialogBuilder
        .newDialog()
        .setSingleChoiceItems(
            adapter,
            selectedIndex,
            (dialog, which) -> handler.selectedCalendar(calendars.get(which)))
        .setOnDismissListener(dialogInterface -> handler.cancel())
        .show();
  }

  @Override
  public void onResume() {
    super.onResume();

    if (!permissionChecker.canAccessCalendars()) {
      handler.cancel();
    }
  }

  @Override
  public void onCancel(DialogInterface dialog) {
    super.onCancel(dialog);

    handler.cancel();
  }

  @Override
  protected void inject(DialogFragmentComponent component) {
    component.inject(this);
  }

  public void setCalendarSelectionHandler(CalendarSelectionHandler handler) {
    this.handler = handler;
  }

  public interface CalendarSelectionHandler {

    void selectedCalendar(AndroidCalendar calendar);

    void cancel();
  }
}
