package com.simplemobiletools.calendar.adapters

import android.content.Intent
import android.support.v4.content.ContextCompat.startActivity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import com.simplemobiletools.calendar.R
import com.simplemobiletools.calendar.activities.MainActivity
import com.simplemobiletools.calendar.activities.SimpleActivity
import com.simplemobiletools.calendar.activities.PopupActivity
import com.simplemobiletools.calendar.dialogs.DeleteEventDialog
import com.simplemobiletools.calendar.extensions.config
import com.simplemobiletools.calendar.extensions.dbHelper
import com.simplemobiletools.calendar.extensions.shareEvents
import com.simplemobiletools.calendar.helpers.Formatter
import com.simplemobiletools.calendar.models.Event
import com.simplemobiletools.commons.adapters.MyRecyclerViewAdapter
import com.simplemobiletools.commons.extensions.applyColorFilter
import com.simplemobiletools.commons.extensions.beInvisible
import com.simplemobiletools.commons.extensions.beInvisibleIf
import com.simplemobiletools.commons.views.MyRecyclerView
import kotlinx.android.synthetic.main.activity_event.*
import kotlinx.android.synthetic.main.event_item_day_view.view.*
import com.simplemobiletools.calendar.extensions.*

class DayEventsAdapter(activity: SimpleActivity, val events: ArrayList<Event>, recyclerView: MyRecyclerView, itemClick: (Any) -> Unit)
    : MyRecyclerViewAdapter(activity, recyclerView, null, itemClick) {

    private var allDayString = resources.getString(R.string.all_day)
    private var replaceDescriptionWithLocation = activity.config.replaceDescription

    init {
        setupDragListener(true)
    }

    override fun getActionMenuId() = R.menu.cab_day

    override fun prepareActionMode(menu: Menu) {}

    override fun prepareItemSelection(view: View) {}

    override fun markItemSelection(select: Boolean, view: View?) {
        view?.event_item_frame?.isSelected = select
    }

    override fun actionItemPressed(id: Int) {
        when (id) {
            R.id.cab_share -> shareEvents()
            R.id.cab_delete -> askConfirmDelete()
        }
    }

    override fun getSelectableItemCount() = events.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = createViewHolder(R.layout.event_item_day_view, parent)

    override fun onBindViewHolder(holder: MyRecyclerViewAdapter.ViewHolder, position: Int) {
        val event = events[position]
        val view = holder.bindView(event) { itemView, layoutPosition ->
            setupView(itemView, event)
        }
        bindViewHolder(holder, position, view)
    }

    override fun getItemCount() = events.size

    private fun setupView(view: View, event: Event) {
        view.apply {
            event_item_finish.setChecked(event.isFinished)
            event_item_finish.setOnClickListener {
                event.isFinished = event_item_finish.isChecked()
                context!!.dbHelper.update(event, true)

                if(event.isFinished && event.delay_time == -1 && event.check_location != 0) {
                    val intent = Intent(context, PopupActivity::class.java)
                    context.startActivity(intent)
                    event.delay_time = -2
                    context!!.dbHelper.update(event, true)
                }

            }
            event_section_title.text = event.title
            event_item_description.text = if (replaceDescriptionWithLocation) event.location else event.description
            event_item_start.text = if (event.getIsAllDay()) allDayString else Formatter.getTimeFromTS(context, event.startTS)
            event_item_end.beInvisibleIf(event.startTS == event.endTS)
            event_item_color.applyColorFilter(event.color)

            if (event.startTS != event.endTS) {
                val startCode = Formatter.getDayCodeFromTS(event.startTS)
                val endCode = Formatter.getDayCodeFromTS(event.endTS)

                event_item_end.apply {
                    text = Formatter.getTimeFromTS(context, event.endTS)
                    if (startCode != endCode) {
                        if (event.getIsAllDay()) {
                            text = Formatter.getDateFromCode(context, endCode, true)
                        } else {
                            append(" (${Formatter.getDateFromCode(context, endCode, true)})")
                        }
                    } else if (event.getIsAllDay()) {
                        beInvisible()
                    }
                }
            }

            event_item_start.setTextColor(textColor)
            event_item_end.setTextColor(textColor)
            event_section_title.setTextColor(textColor)
            event_item_description.setTextColor(textColor)
        }
    }

    private fun shareEvents() {
        val eventIds = ArrayList<Int>(selectedPositions.size)
        selectedPositions.forEach {
            eventIds.add(events[it].id)
        }
        activity.shareEvents(eventIds.distinct())
    }

    private fun askConfirmDelete() {
        val eventIds = ArrayList<Int>(selectedPositions.size)
        val timestamps = ArrayList<Int>(selectedPositions.size)
        selectedPositions.forEach {
            eventIds.add(events[it].id)
            timestamps.add(events[it].startTS)
        }

        DeleteEventDialog(activity, eventIds) {
            val eventsToDelete = ArrayList<Event>(selectedPositions.size)
            selectedPositions.sortedDescending().forEach {
                eventsToDelete.add(events[it])
            }
            events.removeAll(eventsToDelete)

            if (it) {
                val eventIDs = Array(eventIds.size, { i -> (eventIds[i].toString()) })
                activity.dbHelper.deleteEvents(eventIDs, true)
            } else {
                eventIds.forEachIndexed { index, value ->
                    activity.dbHelper.addEventRepeatException(value, timestamps[index], true)
                }
            }
            removeSelectedItems()
        }
    }



}
