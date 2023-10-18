package org.tasks.filters

import android.content.Intent
import com.todoroo.astrid.api.FilterListItem

data class NavigationDrawerAction(
    val title: String,
    val icon: Int,
    val requestCode: Int,
    val intent: Intent? = null,
) : FilterListItem {
    override val itemType = FilterListItem.Type.ACTION

    override fun areItemsTheSame(other: FilterListItem) = this == other
}
