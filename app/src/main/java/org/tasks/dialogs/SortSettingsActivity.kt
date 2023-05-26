package org.tasks.dialogs

import android.content.Context
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NO_ANIMATION
import android.content.res.Configuration.UI_MODE_NIGHT_YES
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Chip
import androidx.compose.material.ChipDefaults
import androidx.compose.material.ContentAlpha
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.google.android.material.composethemeadapter.MdcTheme
import com.todoroo.astrid.core.SortHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.tasks.R
import org.tasks.compose.SystemBars
import org.tasks.compose.collectAsStateLifecycleAware

@AndroidEntryPoint
class SortSettingsActivity : ComponentActivity() {

    private val viewModel: SortSettingsViewModel by viewModels()

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = ContextCompat.getColor(this, android.R.color.transparent)
        setContent {
            MdcTheme {
                val scrimColor = if (isSystemInDarkTheme())
                    Color(0x52454545)
                else
                    MaterialTheme.colors.onSurface.copy(.5f)
                // edge-to-edge potentially fixed in material3 v1.2.0
                SystemBars(
                    statusBarColor = scrimColor,
                    navigationBarColor = MaterialTheme.colors.surface,
                )
                val state = viewModel.state.collectAsStateLifecycleAware().value
                val mainSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                val scope = rememberCoroutineScope()
                var showGroupPicker by remember { mutableStateOf(false) }
                var showSortPicker by remember { mutableStateOf(false) }
                var showCompletedPicker by remember { mutableStateOf(false) }
                ModalBottomSheet(
                    onDismissRequest = {
                        val forceReload = viewModel.forceReload
                        val changedGroup = viewModel.changedGroup
                        setResult(
                            RESULT_OK,
                            Intent()
                                .putExtra(EXTRA_FORCE_RELOAD, forceReload)
                                .putExtra(EXTRA_CHANGED_GROUP, changedGroup)
                        )
                        finish()
                        overridePendingTransition(0, 0)
                    },
                    sheetState = mainSheetState,
                    containerColor = MaterialTheme.colors.surface,
                    scrimColor = scrimColor,
                    shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                    content = {
                        BottomSheetContent(
                            groupMode = state.groupMode,
                            sortMode = state.sortMode,
                            completedMode = state.completedMode,
                            sortAscending = state.sortAscending,
                            groupAscending = state.groupAscending,
                            completedAscending = state.completedAscending,
                            manualSort = state.manualSort && manualEnabled,
                            astridSort = state.astridSort && astridEnabled,
                            completedAtBottom = state.completedAtBottom,
                            setSortAscending = { viewModel.setSortAscending(it) },
                            setGroupAscending = { viewModel.setGroupAscending(it) },
                            setCompletedAscending = { viewModel.setCompletedAscending(it) },
                            setCompletedAtBottom = { viewModel.setCompletedAtBottom(it) },
                            clickGroupMode = { showGroupPicker = true },
                            clickSortMode = { showSortPicker = true },
                            clickCompletedMode = { showCompletedPicker = true },
                        )
                    }
                )
                if (showGroupPicker) {
                    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                    val closePicker: () -> Unit = {
                        scope.launch {
                            sheetState.hide()
                            showGroupPicker = false
                        }
                    }
                    ModalBottomSheet(
                        onDismissRequest = closePicker,
                        sheetState = sheetState,
                        containerColor = MaterialTheme.colors.surface,
                        scrimColor = Color.Transparent,
                        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                        content = {
                            Column(
                                modifier = Modifier
                                    .verticalScroll(rememberScrollState())
                                    .fillMaxWidth()
                            ) {
                                SortPicker(
                                    selected = state.groupMode,
                                    options = groupOptions,
                                    onClick = {
                                        viewModel.setGroupMode(it)
                                        closePicker()
                                    }
                                )
                            }
                        }
                    )
                    LaunchedEffect(Unit) {
                        sheetState.show()
                    }
                }
                if (showSortPicker) {
                    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                    val closePicker: () -> Unit = {
                        scope.launch {
                            sheetState.hide()
                            showSortPicker = false
                        }
                    }
                    ModalBottomSheet(
                        onDismissRequest = closePicker,
                        sheetState = sheetState,
                        containerColor = MaterialTheme.colors.surface,
                        scrimColor = Color.Transparent,
                        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                        content = {
                            SortSheetContent(
                                manualSortEnabled = manualEnabled,
                                astridSortEnabled = astridEnabled,
                                setManualSort = {
                                    viewModel.setManual(true)
                                    closePicker()
                                },
                                setAstridSort = {
                                    viewModel.setAstrid(true)
                                    closePicker()
                                },
                                manualSortSelected = (manualEnabled && state.manualSort) || (astridEnabled && state.astridSort),
                                selected = state.sortMode,
                                onSelected = {
                                    viewModel.setSortMode(it)
                                    closePicker()
                                }
                            )
                        }
                    )
                    LaunchedEffect(Unit) {
                        sheetState.show()
                    }
                }
                if (showCompletedPicker) {
                    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                    val closePicker: () -> Unit = {
                        scope.launch {
                            sheetState.hide()
                            showCompletedPicker = false
                        }
                    }
                    ModalBottomSheet(
                        onDismissRequest = closePicker,
                        sheetState = sheetState,
                        containerColor = MaterialTheme.colors.surface,
                        scrimColor = Color.Transparent,
                        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                        content = {
                            Column(
                                modifier = Modifier
                                    .verticalScroll(rememberScrollState())
                                    .fillMaxWidth()
                            ) {
                                SortPicker(
                                    selected = state.completedMode,
                                    options = completedOptions,
                                    onClick = {
                                        viewModel.setCompletedMode(it)
                                        closePicker()
                                    }
                                )
                            }
                        }
                    )
                    LaunchedEffect(Unit) {
                        sheetState.show()
                    }
                }
                LaunchedEffect(Unit) {
                    mainSheetState.show()
                }
            }
        }
    }

    private val manualEnabled: Boolean by lazy {
        intent.getBooleanExtra(EXTRA_MANUAL_ORDER, false)
    }

    private val astridEnabled: Boolean by lazy {
        intent.getBooleanExtra(EXTRA_ASTRID_ORDER, false)
    }

    companion object {
        const val EXTRA_MANUAL_ORDER = "extra_manual_order"
        const val EXTRA_ASTRID_ORDER = "extra_astrid_order"
        const val EXTRA_WIDGET_ID = "extra_widget_id"
        const val EXTRA_FORCE_RELOAD = "extra_force_reload"
        const val EXTRA_CHANGED_GROUP = "extra_changed_group"
        const val WIDGET_NONE = -1

        fun getIntent(
            context: Context,
            manualOrderEnabled: Boolean,
            astridOrderEnabled: Boolean,
            widgetId: Int? = null,
        ) = Intent(context, SortSettingsActivity::class.java)
            .addFlags(FLAG_ACTIVITY_NO_ANIMATION)
            .putExtra(EXTRA_MANUAL_ORDER, manualOrderEnabled)
            .putExtra(EXTRA_ASTRID_ORDER, astridOrderEnabled)
            .apply {
                widgetId?.let {
                    putExtra(EXTRA_WIDGET_ID, it)
                }
            }
    }
}

@Composable
fun SortSheetContent(
    manualSortSelected: Boolean,
    manualSortEnabled: Boolean,
    astridSortEnabled: Boolean,
    selected: Int,
    setManualSort: (Boolean) -> Unit,
    setAstridSort: (Boolean) -> Unit,
    onSelected: (Int) -> Unit,
) {
    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .fillMaxWidth()
    ) {
        SortPicker(
            selected = if (manualSortSelected) -1 else selected,
            options = sortOptions,
            onClick = { onSelected(it) },
        )
        if (astridSortEnabled) {
            SortOption(
                resId = R.string.astrid_sort_order,
                selected = manualSortSelected,
                onClick = { setAstridSort(true) }
            )
        }
        SortOption(
            resId = R.string.SSD_sort_my_order,
            selected = manualSortSelected && !astridSortEnabled,
            enabled = manualSortEnabled,
            onClick = { setManualSort(true) },
        )
    }
}

val sortOptions = linkedMapOf(
    R.string.SSD_sort_due to SortHelper.SORT_DUE,
    R.string.SSD_sort_start to SortHelper.SORT_START,
    R.string.SSD_sort_importance to SortHelper.SORT_IMPORTANCE,
    R.string.SSD_sort_alpha to SortHelper.SORT_ALPHA,
    R.string.SSD_sort_modified to SortHelper.SORT_MODIFIED,
    R.string.SSD_sort_auto to SortHelper.SORT_AUTO,
    R.string.sort_created to SortHelper.SORT_CREATED,
)

val groupOptions = linkedMapOf(
    R.string.none to SortHelper.GROUP_NONE,
    R.string.SSD_sort_due to SortHelper.SORT_DUE,
    R.string.SSD_sort_start to SortHelper.SORT_START,
    R.string.SSD_sort_importance to SortHelper.SORT_IMPORTANCE,
    R.string.SSD_sort_modified to SortHelper.SORT_MODIFIED,
    R.string.sort_created to SortHelper.SORT_CREATED,
    R.string.sort_list to SortHelper.SORT_LIST,
)

private val completedOptions = linkedMapOf(
    R.string.sort_completed to SortHelper.SORT_COMPLETED,
    R.string.SSD_sort_due to SortHelper.SORT_DUE,
    R.string.SSD_sort_start to SortHelper.SORT_START,
    R.string.SSD_sort_importance to SortHelper.SORT_IMPORTANCE,
    R.string.SSD_sort_alpha to SortHelper.SORT_ALPHA,
    R.string.SSD_sort_modified to SortHelper.SORT_MODIFIED,
    R.string.sort_created to SortHelper.SORT_CREATED,
)

@Composable
fun SortPicker(
    selected: Int,
    options: Map<Int, Int>,
    onClick: (Int) -> Unit,
) {
    options.forEach { (resId, sortMode) ->
        SortOption(
            resId = resId,
            selected = selected == sortMode,
            onClick = { onClick(sortMode) },
        )
    }
}

@Composable
fun SortOption(
    resId: Int,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = stringResource(id = resId),
            style = MaterialTheme.typography.h6.copy(
                color = when {
                    selected -> MaterialTheme.colors.primary
                    enabled -> MaterialTheme.colors.onSurface
                    else -> MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.disabled)
                }
            ),
        )
        if (!enabled) {
            Text(
                text = stringResource(id = R.string.sort_not_available),
                style = MaterialTheme.typography.body2.copy(
                    color = MaterialTheme.colors.error,
                    fontStyle = FontStyle.Italic,
                ),
            )
        }
    }
}

@Composable
fun BottomSheetContent(
    groupMode: Int,
    sortMode: Int,
    completedMode: Int,
    sortAscending: Boolean,
    groupAscending: Boolean,
    completedAscending: Boolean,
    manualSort: Boolean,
    astridSort: Boolean,
    completedAtBottom: Boolean,
    setSortAscending: (Boolean) -> Unit,
    setGroupAscending: (Boolean) -> Unit,
    setCompletedAscending: (Boolean) -> Unit,
    setCompletedAtBottom: (Boolean) -> Unit,
    clickGroupMode: () -> Unit,
    clickSortMode: () -> Unit,
    clickCompletedMode: () -> Unit,
) {
    Row(
        modifier = Modifier.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .weight(1f)
                .clickable { clickGroupMode() }
        ) {
            Text(
                text = stringResource(id = R.string.sort_grouping),
                style = MaterialTheme.typography.h6,
            )
            Text(
                text = stringResource(id = groupMode.modeString),
                style = MaterialTheme.typography.body1,
            )
        }
        if (groupMode != SortHelper.GROUP_NONE) {
            Spacer(modifier = Modifier.width(16.dp))
            val displayAscending = when (groupMode) {
                SortHelper.SORT_IMPORTANCE -> !groupAscending
                else -> groupAscending
            }
            OrderingChip(
                ascending = displayAscending,
                onClick = { setGroupAscending(!groupAscending) }
            )
        }
    }
    Row(
        modifier = Modifier.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable { clickSortMode() }
        ) {
            Text(
                text = stringResource(id = R.string.sort_sorting),
                style = MaterialTheme.typography.h6,
            )
            Text(
                text = stringResource(
                    id = when {
                        manualSort -> R.string.SSD_sort_my_order
                        astridSort -> R.string.astrid_sort_order
                        else -> sortMode.modeString
                    }
                ),
                style = MaterialTheme.typography.body1,
            )
        }
        if (!(manualSort || astridSort)) {
            Spacer(modifier = Modifier.width(16.dp))
            val displayAscending = when (sortMode) {
                SortHelper.SORT_AUTO,
                SortHelper.SORT_IMPORTANCE -> !sortAscending
                else -> sortAscending
            }
            OrderingChip(
                ascending = displayAscending,
                onClick = { setSortAscending(!sortAscending) }
            )
        }
    }
    if (!astridSort) {
        Divider(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .height(1.dp)
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            Text(
                text = stringResource(R.string.completed_tasks_at_bottom),
                style = MaterialTheme.typography.body1,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = completedAtBottom,
                onCheckedChange = { setCompletedAtBottom(it) }
            )
        }
        if (completedAtBottom) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { clickCompletedMode() }
                ) {
                    Text(
                        text = stringResource(id = R.string.completed),
                        style = MaterialTheme.typography.h6,
                    )
                    Text(
                        text = stringResource(id = completedMode.modeString),
                        style = MaterialTheme.typography.body1,
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                val displayAscending = when (completedMode) {
                    SortHelper.SORT_IMPORTANCE -> !completedAscending
                    else -> completedAscending
                }
                OrderingChip(
                    ascending = displayAscending,
                    onClick = { setCompletedAscending(!completedAscending) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun OrderingChip(
    ascending: Boolean,
    onClick: () -> Unit,
) {
    Chip(
        onClick = onClick,
        shape = RoundedCornerShape(4.dp),
        border = ChipDefaults.outlinedBorder,
        colors = ChipDefaults.outlinedChipColors(),
        leadingIcon = {
            Icon(
                imageVector = if (ascending) Icons.Outlined.ArrowUpward else Icons.Outlined.ArrowDownward,
                modifier = Modifier.size(16.dp),
                contentDescription = null,
            )
        },
        content = {
            Text(
                text = stringResource(id = if (ascending) R.string.sort_ascending else R.string.sort_descending),
                style = MaterialTheme.typography.body1,
            )
        },
    )

}

private val Int.modeString: Int
    get() = when (this) {
        SortHelper.GROUP_NONE -> R.string.none
        SortHelper.SORT_ALPHA -> R.string.SSD_sort_alpha
        SortHelper.SORT_DUE -> R.string.SSD_sort_due
        SortHelper.SORT_IMPORTANCE -> R.string.SSD_sort_importance
        SortHelper.SORT_MODIFIED -> R.string.SSD_sort_modified
        SortHelper.SORT_CREATED -> R.string.sort_created
        SortHelper.SORT_START -> R.string.SSD_sort_start
        SortHelper.SORT_LIST -> R.string.sort_list
        SortHelper.SORT_COMPLETED -> R.string.sort_completed
        else -> R.string.SSD_sort_auto
    }

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun PreviewSortBottomSheet() {
    MdcTheme {
        Column {
            BottomSheetContent(
                groupMode = SortHelper.GROUP_NONE,
                sortMode = SortHelper.SORT_AUTO,
                completedMode = SortHelper.SORT_ALPHA,
                sortAscending = false,
                groupAscending = false,
                completedAscending = false,
                manualSort = false,
                astridSort = false,
                completedAtBottom = true,
                clickGroupMode = {},
                clickSortMode = {},
                clickCompletedMode = {},
                setCompletedAtBottom = {},
                setSortAscending = {},
                setGroupAscending = {},
                setCompletedAscending = {},
            )
        }
    }
}
