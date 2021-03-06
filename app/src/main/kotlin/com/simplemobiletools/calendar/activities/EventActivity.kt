package com.simplemobiletools.calendar.activities

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.Menu
import android.view.MenuItem
import android.view.WindowManager
import android.view.View
import android.widget.ImageView
import com.simplemobiletools.calendar.R
import com.simplemobiletools.calendar.dialogs.*
import com.simplemobiletools.calendar.extensions.*
import com.simplemobiletools.calendar.helpers.*
import com.simplemobiletools.calendar.helpers.Formatter
import com.simplemobiletools.calendar.models.CalDAVCalendar
import com.simplemobiletools.calendar.models.Event
import com.simplemobiletools.commons.dialogs.RadioGroupDialog
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.*
import com.simplemobiletools.commons.models.RadioItem
import kotlinx.android.synthetic.main.activity_event.*
import kotlinx.android.synthetic.main.activity_event.view.*
import org.joda.time.DateTime
import java.util.*
import java.util.regex.Pattern
import android.app.Activity
import android.media.ExifInterface
import android.os.Environment
import android.util.Log
import android.widget.PopupMenu
import com.facebook.drawee.backends.pipeline.Fresco
import com.stfalcon.frescoimageviewer.ImageViewer
import kotlinx.android.synthetic.main.widget_config_list.*
import java.io.File
import java.io.FilenameFilter
import java.net.URI
import java.text.SimpleDateFormat
import kotlin.collections.ArrayList


class EventActivity : SimpleActivity() {
    private val LAT_LON_PATTERN = "^[-+]?([1-8]?\\d(\\.\\d+)?|90(\\.0+)?)([,;])\\s*[-+]?(180(\\.0+)?|((1[0-7]\\d)|([1-9]?\\d))(\\.\\d+)?)\$"
    private var mReminder1Minutes = 0
    private var mReminder2Minutes = 0
    private var mReminder3Minutes = 0
    private var mRepeatInterval = 0
    private var mRepeatLimit = 0
    private var mRepeatRule = 0
    private var mEventTypeId = DBHelper.REGULAR_EVENT_TYPE_ID
    private var mDialogTheme = 0
    private var mEventOccurrenceTS = 0
    private var mEventCalendarId = STORED_LOCALLY_ONLY
    private var wasActivityInitialized = false
    private var locationId = ""
    private var delayTime = 0
    private var delayTime2 = 0
    private var alphadelay = 0

    lateinit var mEventStartDateTime: DateTime
    lateinit var mEventEndDateTime: DateTime
    lateinit var mEvent: Event

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_event)

        supportActionBar?.setHomeAsUpIndicator(R.drawable.ic_cross)
        val intent = intent ?: return
        mDialogTheme = getDialogTheme()

        val eventId = intent.getIntExtra(EVENT_ID, 0)
        val event = dbHelper.getEventWithId(eventId)

        val arr = getIntent().getByteArrayExtra("croppedImage")
        if(arr != null) {
            var croppedImage = BitmapFactory.decodeByteArray(arr, 0, arr.size)
            event_croppedImage.setImageBitmap(croppedImage)
        }

        if (eventId != 0 && event == null) {
            finish()
            return
        }

        if (event != null) {
            mEvent = event
            mEventOccurrenceTS = intent.getIntExtra(EVENT_OCCURRENCE_TS, 0)
            event_pictures.visibility = View.VISIBLE
            setupEditEvent()
        } else {
            mEvent = Event()
            mReminder1Minutes = config.defaultReminderMinutes
            mReminder2Minutes = config.defaultReminderMinutes3
            mReminder3Minutes = config.defaultReminderMinutes2
            event_pictures.visibility = View.GONE
            setupNewEvent()
        }

        delayTime = config.defaultDelayAlarmTime
        delayTime2 = config.defaultDelayAlarmTime2Value



        checkReminderTexts()
        updateRepetitionText()
        updateStartTexts()
        updateEndTexts()
        updateEventType()
        updateCalDAVCalendar()

        event_pictures.setOnClickListener { showRelatedPictures() }


        // 도착장소 id를 반환하는 거..
        find_location.setOnClickListener{ findLocation() }

        event_show_on_map.setOnClickListener { showOnMap() }
        event_start_date.setOnClickListener { setupStartDate() }
        event_start_time.setOnClickListener { setupStartTime() }
        event_end_date.setOnClickListener { setupEndDate() }
        event_end_time.setOnClickListener { setupEndTime() }

        event_all_day.setOnCheckedChangeListener { compoundButton, isChecked -> toggleAllDay(isChecked) }
        event_repetition.setOnClickListener { showRepeatIntervalDialog() }
        event_repetition_rule_holder.setOnClickListener { showRepetitionRuleDialog() }
        event_repetition_limit_holder.setOnClickListener { showRepetitionTypePicker() }

        event_reminder_1.setOnClickListener { showReminder1Dialog() }
        event_reminder_2.setOnClickListener { showReminder2Dialog() }
        event_reminder_3.setOnClickListener { showReminder3Dialog() }

        event_type_holder.setOnClickListener { showEventTypeDialog() }

        if (mEvent.flags and FLAG_ALL_DAY != 0)
            event_all_day.toggle()

        updateTextColors(event_scrollview)
        event_pictures.setTextColor(config.textColor)
        updateIconColors()
        wasActivityInitialized = true
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_event, menu)
        if (wasActivityInitialized) {
            menu.findItem(R.id.delete).isVisible = mEvent.id != 0
            menu.findItem(R.id.share).isVisible = mEvent.id != 0
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.save -> saveEvent()
            R.id.delete -> deleteEvent()
            R.id.share -> shareEvent()
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    private fun setupEditEvent() {
        val realStart = if (mEventOccurrenceTS == 0) mEvent.startTS else mEventOccurrenceTS
        val duration = mEvent.endTS - mEvent.startTS
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        supportActionBar?.title = resources.getString(R.string.edit_event)
        mEventStartDateTime = Formatter.getDateTimeFromTS(realStart)
        mEventEndDateTime = Formatter.getDateTimeFromTS(realStart + duration)
        event_title.setText(mEvent.title)
        event_location.setText(mEvent.location)
        location_description.setText(mEvent.locat_description).toString()
        event_category.setText(mEvent.category)
        event_description.setText(mEvent.description)
        event_description.movementMethod = LinkMovementMethod.getInstance()
        event_finish.setChecked(mEvent.isFinished)
        event_finish.setVisibility(View.VISIBLE)
        event_finish_description.setVisibility(View.VISIBLE)

        if(mEvent.check_location == 1) {
            location_check.radio1.isChecked = true
        }
        if(mEvent.check_location == 2)
            location_check.radio2.isChecked = true
        if(mEvent.check_location == 3)
            location_check.radio3.isChecked = true

        mReminder1Minutes = mEvent.reminder1Minutes
        mReminder2Minutes = mEvent.reminder2Minutes
        mReminder3Minutes = mEvent.reminder3Minutes
        mRepeatInterval = mEvent.repeatInterval
        mRepeatLimit = mEvent.repeatLimit
        mRepeatRule = mEvent.repeatRule
        mEventTypeId = mEvent.eventType
        mEventCalendarId = mEvent.getCalDAVCalendarId()
        locationId = mEvent.locat_placeid
        checkRepeatTexts(mRepeatInterval)
        if(mEvent.delay_time == -2)
            delayTime = -2
        else
            delayTime = mEvent.delay_time
        delayTime2 = mEvent.delay_time2

    }

    private fun setupNewEvent() {
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        supportActionBar?.title = resources.getString(R.string.new_event)
        val isLastCaldavCalendarOK = config.caldavSync && config.getSyncedCalendarIdsAsList().contains(config.lastUsedCaldavCalendar.toString())
        mEventCalendarId = if (isLastCaldavCalendarOK) config.lastUsedCaldavCalendar else STORED_LOCALLY_ONLY

        if (intent.action == Intent.ACTION_EDIT || intent.action == Intent.ACTION_INSERT) {
            val startTS = (intent.getLongExtra("beginTime", System.currentTimeMillis()) / 1000).toInt()
            mEventStartDateTime = Formatter.getDateTimeFromTS(startTS)

            val endTS = (intent.getLongExtra("endTime",
                    intent.getLongExtra("beginTime", System.currentTimeMillis())) / 1000).toInt()
            mEventEndDateTime = Formatter.getDateTimeFromTS(endTS)

            event_title.setText(intent.getStringExtra("title"))
            event_location.setText(intent.getStringExtra("eventLocation"))
            event_category.setText(intent.getStringExtra("category"))
            event_description.setText(intent.getStringExtra("description"))
            event_description.movementMethod = LinkMovementMethod.getInstance()
        } else {
            val startTS = intent.getIntExtra(NEW_EVENT_START_TS, 0)
            val dateTime = Formatter.getDateTimeFromTS(startTS)
            mEventStartDateTime = dateTime

            val addHours = if (intent.getBooleanExtra(NEW_EVENT_SET_HOUR_DURATION, false)) 1 else 0
            mEventEndDateTime = mEventStartDateTime.plusHours(addHours)
        }
    }


    private fun showRelatedPictures(){

        fun getRelatedList(): ArrayList<String>? {

            fun distance(lat1:Double, lon1:Double, lat2:Double, lon2:Double):Double{

                fun deg2rad(deg:Double):Double{
                    return (deg * Math.PI / 180.0)
                }

                fun rad2deg(rad:Double):Double{
                    return (rad * 180.0 / Math.PI)
                }

                val theta = lon1 - lon2
                Log.d("showPictures", "theta is " + theta.toString())
                var dist = Math.sin(deg2rad(lat1)) * Math.sin(deg2rad(lat2)) + Math.cos(deg2rad(lat1)) * Math.cos(deg2rad(lat2)) * Math.cos(deg2rad(theta))
                Log.d("showPictures", "dist is " + dist.toString())
                dist = Math.acos(dist)
                Log.d("showPictures", "dist is " + dist.toString())
                dist = rad2deg(dist)
                Log.d("showPictures", "dist is " + dist.toString())
                dist *= (60 * 1.1515)
                Log.d("showPictures", "dist is " + dist.toString())
                dist *= 1609.344
                Log.d("showPictures", "dist is " + dist.toString())

                return dist
            }

            try {
                val fileFilter = FilenameFilter { dir, name ->
                    name.endsWith("jpg") || name.endsWith("JPG")
                }
                val file = File(Environment.getExternalStorageDirectory().absolutePath + "/DCIM/Camera")
                val files = file.listFiles(fileFilter)
                val titleList = arrayListOf<URI>()
                val weakRelatedList = arrayListOf<String>()

                val it = files.iterator()
                while(it.hasNext()){
                    titleList.add(it.next().toURI())
                }
                Log.d("showPictures", "titleList is " + titleList.toString())
                Log.d("showPictures", "startTs is " + mEventStartDateTime.millis.toString())
                Log.d("showPictures", "endTs is " + mEventEndDateTime.millis.toString())

                if(mEventStartDateTime.millis == mEventEndDateTime.millis ){
                    val it = titleList.iterator()
                    while(it.hasNext()){
                        val curURI = it.next()
                        val exif = ExifInterface(curURI.path)
                        val simpleDateFormat = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.KOREA)
                        val extractedDateTime = exif.getAttribute(ExifInterface.TAG_DATETIME)
                        if(extractedDateTime == null)
                            continue
                        val extractedTime = simpleDateFormat.parse(extractedDateTime).time
                        Log.d("showPictures", "start == end is " + extractedTime.toString())
                        if(mEventStartDateTime.millis <= extractedTime)
                            weakRelatedList.add(curURI.toString())
                    }
                }
                else{
                    val it = titleList.iterator()
                    while(it.hasNext()){
                        val curURI = it.next()
                        val exif = ExifInterface(curURI.path)
                        val simpleDateFormat = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.KOREA)
                        val extractedDateTime = exif.getAttribute(ExifInterface.TAG_DATETIME)
                        if(extractedDateTime == null)
                            continue
                        val extractedTime = simpleDateFormat.parse(extractedDateTime).time
                        Log.d("showPictures", "start != end is " + extractedTime.toString())
                        if(mEventStartDateTime.millis <= extractedTime &&
                                mEventEndDateTime.millis >= extractedTime)
                            weakRelatedList.add(curURI.toString())
                    }
                }

                Log.d("showPictures", "시간 기반: " + weakRelatedList.toString())

                val locatLatitude = mEvent.locat_latitude.toDoubleOrNull()
                val locatLongitude = mEvent.locat_longitude.toDoubleOrNull()
                Log.d("showPictures", "일정 목적지: " + locatLatitude.toString() + " " + locatLongitude.toString())

                if(locatLatitude == null || locatLongitude == null)
                    return weakRelatedList
                else{
                    val strongRelatedList = arrayListOf<String>()
                    val it = weakRelatedList.iterator()
                    while(it.hasNext()){
                        val curURI = URI.create(it.next())
                        val exif = ExifInterface(curURI.path)
                        val latLong = FloatArray(2)
                        Log.d("showPictures", "사진 경로: " + curURI.path.toString())
                        if(exif.getLatLong(latLong)){
                            val pictureLatitude:Double = latLong[0].toDouble()
                            val pictureLongitude:Double = latLong[1].toDouble()
                            Log.d("showPictures", "사진 위치: " + pictureLatitude.toString() + " " + pictureLongitude.toString())
                            val dist = distance(pictureLatitude, pictureLongitude,
                                    locatLatitude, locatLongitude)
                            Log.d("showPictures", "거리: " + dist.toString())
                            if(dist <= 1000)
                                strongRelatedList.add(curURI.toString())
                        }
                    }
                    Log.d("showPictures", "위치 기반: " + strongRelatedList.toString())
                    if(strongRelatedList.isNotEmpty())
                        return strongRelatedList
                    else
                        return null
                }
            } catch (e: Exception) {
                return null
            }

        }

        val imgs = getRelatedList()

        if(imgs != null){
            val imageViewer = ImageViewer.Builder(this, imgs)
            imageViewer.setStartPosition(0)
            imageViewer.show()
        }
    }

    private fun showReminder1Dialog() {
        showPickSecondsDialogHelper(mReminder1Minutes) {
            mReminder1Minutes = if (it <= 0) it else it / 60
            checkReminderTexts()
        }
    }

    private fun showReminder2Dialog() {
        showPickSecondsDialogHelper(mReminder2Minutes) {
            mReminder2Minutes = if (it <= 0) it else it / 60
            checkReminderTexts()
        }
    }

    private fun showReminder3Dialog() {
        showPickSecondsDialogHelper(mReminder3Minutes) {
            mReminder3Minutes = if (it <= 0) it else it / 60
            checkReminderTexts()
        }
    }

    private fun showRepeatIntervalDialog() {
        showEventRepeatIntervalDialog(mRepeatInterval) {
            setRepeatInterval(it)
        }
    }

    private fun setRepeatInterval(interval: Int) {
        mRepeatInterval = interval
        updateRepetitionText()
        checkRepeatTexts(interval)

        if (mRepeatInterval.isXWeeklyRepetition()) {
            setRepeatRule(Math.pow(2.0, (mEventStartDateTime.dayOfWeek - 1).toDouble()).toInt())
        } else if (mRepeatInterval.isXMonthlyRepetition()) {
            setRepeatRule(REPEAT_MONTH_SAME_DAY)
        }
    }

    private fun checkRepeatTexts(limit: Int) {
        event_repetition_limit_holder.beGoneIf(limit == 0)
        checkRepetitionLimitText()

        event_repetition_rule_holder.beVisibleIf(mRepeatInterval.isXWeeklyRepetition() || mRepeatInterval.isXMonthlyRepetition())
        checkRepetitionRuleText()
    }

    private fun showRepetitionTypePicker() {
        hideKeyboard()
        RepeatLimitTypePickerDialog(this, mRepeatLimit, mEventStartDateTime.seconds()) {
            setRepeatLimit(it)
        }
    }

    private fun setRepeatLimit(limit: Int) {
        mRepeatLimit = limit
        checkRepetitionLimitText()
    }

    private fun checkRepetitionLimitText() {
        event_repetition_limit.text = when {
            mRepeatLimit == 0 -> {
                event_repetition_limit_label.text = getString(R.string.repeat)
                resources.getString(R.string.forever)
            }
            mRepeatLimit > 0 -> {
                event_repetition_limit_label.text = getString(R.string.repeat_till)
                val repeatLimitDateTime = Formatter.getDateTimeFromTS(mRepeatLimit)
                Formatter.getFullDate(applicationContext, repeatLimitDateTime)
            }
            else -> {
                event_repetition_limit_label.text = getString(R.string.repeat)
                "${-mRepeatLimit} ${getString(R.string.times)}"
            }
        }
    }

    private fun showRepetitionRuleDialog() {
        hideKeyboard()
        if (mRepeatInterval.isXWeeklyRepetition()) {
            RepeatRuleWeeklyDialog(this, mRepeatRule) {
                setRepeatRule(it)
            }
        } else if (mRepeatInterval.isXMonthlyRepetition()) {
            val items = arrayListOf(RadioItem(REPEAT_MONTH_SAME_DAY, getString(R.string.repeat_on_the_same_day)))

            // split Every Last Sunday and Every Fourth Sunday of the month, if the month has 4 sundays
            if (isLastWeekDayOfMonth()) {
                val order = (mEventStartDateTime.dayOfMonth - 1) / 7 + 1
                if (order == 4) {
                    items.add(RadioItem(REPEAT_MONTH_ORDER_WEEKDAY, getRepeatXthDayString(true, REPEAT_MONTH_ORDER_WEEKDAY)))
                    items.add(RadioItem(REPEAT_MONTH_ORDER_WEEKDAY_USE_LAST, getRepeatXthDayString(true, REPEAT_MONTH_ORDER_WEEKDAY_USE_LAST)))
                } else if (order == 5) {
                    items.add(RadioItem(REPEAT_MONTH_ORDER_WEEKDAY_USE_LAST, getRepeatXthDayString(true, REPEAT_MONTH_ORDER_WEEKDAY_USE_LAST)))
                }
            } else {
                items.add(RadioItem(REPEAT_MONTH_ORDER_WEEKDAY, getRepeatXthDayString(true, REPEAT_MONTH_ORDER_WEEKDAY)))
            }

            if (isLastDayOfTheMonth()) {
                items.add(RadioItem(REPEAT_MONTH_LAST_DAY, getString(R.string.repeat_on_the_last_day)))
            }

            RadioGroupDialog(this, items, mRepeatRule) {
                setRepeatRule(it as Int)
            }
        }
    }

    private fun isLastDayOfTheMonth() = mEventStartDateTime.dayOfMonth == mEventStartDateTime.dayOfMonth().withMaximumValue().dayOfMonth

    private fun isLastWeekDayOfMonth() = mEventStartDateTime.monthOfYear != mEventStartDateTime.plusDays(7).monthOfYear

    private fun getRepeatXthDayString(includeBase: Boolean, repeatRule: Int): String {
        val dayOfWeek = mEventStartDateTime.dayOfWeek
        val base = getBaseString(dayOfWeek)
        val order = getOrderString(repeatRule)
        val dayString = getDayString(dayOfWeek)
        return if (includeBase) {
            "$base $order $dayString"
        } else {
            val everyString = getString(if (isMaleGender(mEventStartDateTime.dayOfWeek)) R.string.every_m else R.string.every_f)
            "$everyString $order $dayString"
        }
    }

    private fun getBaseString(day: Int): String {
        return getString(if (isMaleGender(day)) {
            R.string.repeat_every_m
        } else {
            R.string.repeat_every_f
        })
    }

    private fun isMaleGender(day: Int) = day == 1 || day == 2 || day == 4 || day == 5

    private fun getOrderString(repeatRule: Int): String {
        val dayOfMonth = mEventStartDateTime.dayOfMonth
        var order = (dayOfMonth - 1) / 7 + 1
        if (order == 4 && isLastWeekDayOfMonth() && repeatRule == REPEAT_MONTH_ORDER_WEEKDAY_USE_LAST) {
            order = -1
        }

        val isMale = isMaleGender(mEventStartDateTime.dayOfWeek)
        return getString(when (order) {
            1 -> if (isMale) R.string.first_m else R.string.first_f
            2 -> if (isMale) R.string.second_m else R.string.second_f
            3 -> if (isMale) R.string.third_m else R.string.third_f
            4 -> if (isMale) R.string.fourth_m else R.string.fourth_f
            else -> if (isMale) R.string.last_m else R.string.last_f
        })
    }

    private fun getDayString(day: Int): String {
        return getString(when (day) {
            1 -> R.string.monday_alt
            2 -> R.string.tuesday_alt
            3 -> R.string.wednesday_alt
            4 -> R.string.thursday_alt
            5 -> R.string.friday_alt
            6 -> R.string.saturday_alt
            else -> R.string.sunday_alt
        })
    }

    private fun setRepeatRule(rule: Int) {
        mRepeatRule = rule
        checkRepetitionRuleText()
        if (rule == 0) {
            setRepeatInterval(0)
        }
    }

    private fun checkRepetitionRuleText() {
        if (mRepeatInterval.isXWeeklyRepetition()) {
            event_repetition_rule.text = if (mRepeatRule == EVERY_DAY_BIT) getString(R.string.every_day) else getSelectedDaysString(mRepeatRule)
        } else if (mRepeatInterval.isXMonthlyRepetition()) {
            val repeatString = if (mRepeatRule == REPEAT_MONTH_ORDER_WEEKDAY_USE_LAST || mRepeatRule == REPEAT_MONTH_ORDER_WEEKDAY)
                R.string.repeat else R.string.repeat_on

            event_repetition_rule_label.text = getString(repeatString)
            event_repetition_rule.text = getMonthlyRepetitionRuleText()
        }
    }

    private fun getMonthlyRepetitionRuleText() = when (mRepeatRule) {
        REPEAT_MONTH_SAME_DAY -> getString(R.string.the_same_day)
        REPEAT_MONTH_LAST_DAY -> getString(R.string.the_last_day)
        else -> getRepeatXthDayString(false, mRepeatRule)
    }

    private fun showEventTypeDialog() {
        hideKeyboard()
        SelectEventTypeDialog(this, mEventTypeId, false) {
            mEventTypeId = it.id
            updateEventType()
        }
    }

    private fun checkReminderTexts() {
        updateReminder1Text()
        updateReminder2Text()
        updateReminder3Text()
    }

    private fun updateReminder1Text() {
        event_reminder_1.text = getFormattedMinutes(mReminder1Minutes)
        if (mReminder1Minutes == REMINDER_OFF) {
            mReminder2Minutes = REMINDER_OFF
            mReminder3Minutes = REMINDER_OFF
        }
    }

    private fun updateReminder2Text() {
        event_reminder_2.apply {
            beGoneIf(mReminder1Minutes == REMINDER_OFF)
            if (mReminder2Minutes == REMINDER_OFF && mEvent.check_location == 0) {
                text = resources.getString(R.string.add_another_reminder)
                alpha = 0.4f
                mReminder3Minutes = REMINDER_OFF
            }else if(mEvent.check_location == 1){
                text = "위치기반 알림 사용 중"
                alpha = 0.4f
                mReminder3Minutes = REMINDER_OFF
            }
            else {
                text = getFormattedMinutes(mReminder2Minutes)
                alpha = 1f
            }
        }
    }

    private fun updateReminder3Text() {
        event_reminder_3.apply {
            beGoneIf(mReminder2Minutes == REMINDER_OFF || mReminder1Minutes == REMINDER_OFF)
            if (mReminder3Minutes == REMINDER_OFF) {
                text = resources.getString(R.string.add_another_reminder)
                alpha = 0.4f
            } else {
                text = getFormattedMinutes(mReminder3Minutes)
                alpha = 1f
            }
        }
    }

    private fun updateRepetitionText() {
        event_repetition.text = getRepetitionText(mRepeatInterval)
    }

    private fun updateEventType() {
        val eventType = dbHelper.getEventType(mEventTypeId)
        if (eventType != null) {
            event_type.text = eventType.title
            event_type_color.setBackgroundWithStroke(eventType.color, config.backgroundColor)
        }
    }

    private fun updateCalDAVCalendar() {
        if (config.caldavSync) {
            event_caldav_calendar_image.beVisible()
            event_caldav_calendar_holder.beVisible()
            event_caldav_calendar_divider.beVisible()

            val calendars = CalDAVHandler(applicationContext).getCalDAVCalendars(this).filter {
                it.canWrite() && config.getSyncedCalendarIdsAsList().contains(it.id.toString())
            }
            updateCurrentCalendarInfo(if (mEventCalendarId == STORED_LOCALLY_ONLY) null else getCalendarWithId(calendars, getCalendarId()))

            event_caldav_calendar_holder.setOnClickListener {
                hideKeyboard()
                SelectEventCalendarDialog(this, calendars, mEventCalendarId) {
                    if (mEventCalendarId != STORED_LOCALLY_ONLY && it == STORED_LOCALLY_ONLY) {
                        mEventTypeId = DBHelper.REGULAR_EVENT_TYPE_ID
                        updateEventType()
                    }
                    mEventCalendarId = it
                    config.lastUsedCaldavCalendar = it
                    updateCurrentCalendarInfo(getCalendarWithId(calendars, it))
                }
            }
        } else {
            updateCurrentCalendarInfo(null)
        }
    }

    private fun getCalendarId() = if (mEvent.source == SOURCE_SIMPLE_CALENDAR) config.lastUsedCaldavCalendar else mEvent.getCalDAVCalendarId()

    private fun getCalendarWithId(calendars: List<CalDAVCalendar>, calendarId: Int): CalDAVCalendar? =
            calendars.firstOrNull { it.id == calendarId }

    private fun updateCurrentCalendarInfo(currentCalendar: CalDAVCalendar?) {
        event_type_image.beVisibleIf(currentCalendar == null)
        event_type_holder.beVisibleIf(currentCalendar == null)
        event_caldav_calendar_divider.beVisibleIf(currentCalendar == null)
        event_caldav_calendar_email.beGoneIf(currentCalendar == null)

        if (currentCalendar == null) {
            event_caldav_calendar_name.apply {
                text = getString(R.string.store_locally_only)
                setPadding(paddingLeft, paddingTop, paddingRight, resources.getDimension(R.dimen.medium_margin).toInt())
            }
        } else {
            event_caldav_calendar_email.text = currentCalendar.accountName
            event_caldav_calendar_name.apply {
                text = currentCalendar.displayName
                setPadding(paddingLeft, paddingTop, paddingRight, resources.getDimension(R.dimen.tiny_margin).toInt())
            }
        }
    }

    private fun toggleAllDay(isChecked: Boolean) {
        hideKeyboard()
        event_start_time.beGoneIf(isChecked)
        event_end_time.beGoneIf(isChecked)
    }

    private fun shareEvent() {
        shareEvents(arrayListOf(mEvent.id))
    }

    private fun deleteEvent() {
        DeleteEventDialog(this, arrayListOf(mEvent.id)) {
            if (it) {
                dbHelper.deleteEvents(arrayOf(mEvent.id.toString()), true)
            } else {
                dbHelper.addEventRepeatException(mEvent.id, mEventOccurrenceTS, true)
            }
            finish()
        }
    }

    private fun saveEvent() {
        val newTitle = event_title.value
        if (newTitle.isEmpty()) {
            toast(R.string.title_empty)
            event_title.requestFocus()
            return
        }

        // 테스트 중인 코드
        val newlocatdescript = location_description.value
        val newlocatid = locationId
        var checked_location = 0
        var location_latit = ""
        var location_longit = ""
        if(locationId != ""){
            val getLaLo = CurrentModule_Class().GetLongitLatit(locationId)
            location_latit = getLaLo.GetLatit();
            location_longit = getLaLo.GetLongit();
        }

        val newStartTS = mEventStartDateTime.withSecondOfMinute(0).withMillisOfSecond(0).seconds()
        val newEndTS = mEventEndDateTime.withSecondOfMinute(0).withMillisOfSecond(0).seconds()

        if (newStartTS > newEndTS) {
            toast(R.string.end_before_start)
            return
        }

        val wasRepeatable = mEvent.repeatInterval > 0
        val oldSource = mEvent.source
        val newImportId = if (mEvent.id != 0) mEvent.importId else UUID.randomUUID().toString().replace("-", "") + System.currentTimeMillis().toString()

        val newEventType = if (!config.caldavSync || config.lastUsedCaldavCalendar == 0 || mEventCalendarId == STORED_LOCALLY_ONLY) {
            mEventTypeId
        } else {
            dbHelper.getEventTypeWithCalDAVCalendarId(config.lastUsedCaldavCalendar)?.id ?: DBHelper.REGULAR_EVENT_TYPE_ID
        }

        val newSource = if (!config.caldavSync || config.lastUsedCaldavCalendar == 0 || mEventCalendarId == STORED_LOCALLY_ONLY) {
            SOURCE_SIMPLE_CALENDAR
        } else {
            "$CALDAV-${config.lastUsedCaldavCalendar}"
        }

        val reminders = sortedSetOf(mReminder1Minutes, mReminder2Minutes, mReminder3Minutes).filter { it != REMINDER_OFF }
        val reminder1 = reminders.getOrElse(0, { REMINDER_OFF })
        val reminder2 = reminders.getOrElse(1, { REMINDER_OFF })
        var reminder3 = reminders.getOrElse(2, { REMINDER_OFF })

        if(location_check.radio1.isChecked && newlocatid != ""){
            checked_location = 1
            if(config.defaultDelayAlarmTime2 == false)
                reminder3 = config.defaultDelayAlarmTime
        }
        else if(location_check.radio2.isChecked && newlocatid != "") {
            checked_location = 2
            if(config.defaultDelayAlarmTime2 == false)
                reminder3 = config.defaultDelayAlarmTime
        }
        else if(location_check.radio3.isChecked && newlocatid != "") {
            checked_location = 3
            if(config.defaultDelayAlarmTime2 == false)
                reminder3 = config.defaultDelayAlarmTime
        }
        else {
            checked_location = 0
        }

        /*
        config.apply {
            defaultReminderMinutes = reminder1
            defaultReminderMinutes2 = reminder2
            defaultReminderMinutes3 = reminder3
        }
        */

        if(event_finish.isChecked && delayTime == -1 && checked_location != 0){

            val intent = Intent(this, PopupActivity::class.java)
            startActivity(intent)
            mEvent.delay_time = -2

        }

        mEvent.apply {
            startTS = newStartTS
            endTS = newEndTS
            title = newTitle
            description = event_description.value
            reminder1Minutes = reminder1
            reminder2Minutes = reminder2
            reminder3Minutes = reminder3
            repeatInterval = mRepeatInterval
            importId = newImportId
            flags = if (event_all_day.isChecked) (mEvent.flags.addBit(FLAG_ALL_DAY)) else (mEvent.flags.removeBit(FLAG_ALL_DAY))
            repeatLimit = if (repeatInterval == 0) 0 else mRepeatLimit
            repeatRule = mRepeatRule
            eventType = newEventType
            offset = getCurrentOffset()
            isDstIncluded = TimeZone.getDefault().inDaylightTime(Date())
            lastUpdated = System.currentTimeMillis()
            source = newSource
            location = event_location.value
            locat_description = newlocatdescript
            locat_placeid = newlocatid
            check_location = checked_location
            category = event_category.value
            isFinished = event_finish.isChecked()
            locat_latitude = location_latit
            locat_longitude = location_longit
            delay_time = delayTime
            delay_time2 = delayTime2
        }

        // recreate the event if it was moved in a different CalDAV calendar
        if (mEvent.id != 0 && oldSource != newSource) {
            dbHelper.deleteEvents(arrayOf(mEvent.id.toString()), true)
            mEvent.id = 0
        }

        storeEvent(wasRepeatable)
    }

    private fun storeEvent(wasRepeatable: Boolean) {
        if (mEvent.id == 0) {
            dbHelper.insert(mEvent, true) {
                if (DateTime.now().isAfter(mEventStartDateTime.millis)) {
                    if (mEvent.repeatInterval == 0 && mEvent.getReminders().isNotEmpty()) {
                        notifyEvent(mEvent)
                    }
                } else {
                    toast(R.string.event_added)
                }

                finish()
            }
        } else {
            if (mRepeatInterval > 0 && wasRepeatable) {
                EditRepeatingEventDialog(this) {
                    if (it) {
                        dbHelper.update(mEvent, true) {
                            eventUpdated()
                        }
                    } else {
                        dbHelper.addEventRepeatException(mEvent.id, mEventOccurrenceTS, true)
                        mEvent.apply {
                            parentId = id
                            id = 0
                            repeatRule = 0
                            repeatInterval = 0
                            repeatLimit = 0
                        }

                        dbHelper.insert(mEvent, true) {
                            toast(R.string.event_updated)
                            finish()
                        }
                    }
                }
            } else {
                dbHelper.update(mEvent, true) {
                    eventUpdated()
                }
            }
        }
    }

    private fun eventUpdated() {
        toast(R.string.event_updated)
        finish()
    }

    private fun updateStartTexts() {
        updateStartDateText()
        updateStartTimeText()
    }

    private fun updateStartDateText() {
        event_start_date.text = Formatter.getDate(applicationContext, mEventStartDateTime)
        checkStartEndValidity()
    }

    private fun updateStartTimeText() {
        event_start_time.text = Formatter.getTime(this, mEventStartDateTime)
        checkStartEndValidity()
    }

    private fun updateEndTexts() {
        updateEndDateText()
        updateEndTimeText()
    }

    private fun updateEndDateText() {
        event_end_date.text = Formatter.getDate(applicationContext, mEventEndDateTime)
        checkStartEndValidity()
    }

    private fun updateEndTimeText() {
        event_end_time.text = Formatter.getTime(this, mEventEndDateTime)
        checkStartEndValidity()
    }

    private fun checkStartEndValidity() {
        val textColor = if (mEventStartDateTime.isAfter(mEventEndDateTime)) resources.getColor(R.color.red_text) else config.textColor
        event_end_date.setTextColor(textColor)
        event_end_time.setTextColor(textColor)
    }

    private fun showOnMap() {
        if (event_location.value.isEmpty()) {
            toast(R.string.please_fill_location)
            return
        }

        val pattern = Pattern.compile(LAT_LON_PATTERN)
        val locationValue = event_location.value
        val uri = if (pattern.matcher(locationValue).find()) {
            val delimiter = if (locationValue.contains(';')) ";" else ","
            val parts = locationValue.split(delimiter)
            val latitude = parts.first()
            val longitude = parts.last()
            Uri.parse("geo:$latitude,$longitude")
        } else {
            val location = Uri.encode(locationValue)
            Uri.parse("geo:0,0?q=$location")
        }

        val intent = Intent(Intent.ACTION_VIEW, uri)
        if (intent.resolveActivity(packageManager) != null) {
            startActivity(intent)
        } else {
            toast(R.string.no_app_found)
        }
    }

    @SuppressLint("NewApi")
    private fun setupStartDate() {
        hideKeyboard()
        config.backgroundColor.getContrastColor()
        val datepicker = DatePickerDialog(this, mDialogTheme, startDateSetListener, mEventStartDateTime.year, mEventStartDateTime.monthOfYear - 1,
                mEventStartDateTime.dayOfMonth)

        if (isLollipopPlus()) {
            datepicker.datePicker.firstDayOfWeek = if (config.isSundayFirst) Calendar.SUNDAY else Calendar.MONDAY
        }

        datepicker.show()
    }

    private fun setupStartTime() {
        hideKeyboard()
        TimePickerDialog(this, mDialogTheme, startTimeSetListener, mEventStartDateTime.hourOfDay, mEventStartDateTime.minuteOfHour, config.use24HourFormat).show()
    }

    @SuppressLint("NewApi")
    private fun setupEndDate() {
        hideKeyboard()
        val datepicker = DatePickerDialog(this, mDialogTheme, endDateSetListener, mEventEndDateTime.year, mEventEndDateTime.monthOfYear - 1,
                mEventEndDateTime.dayOfMonth)

        if (isLollipopPlus()) {
            datepicker.datePicker.firstDayOfWeek = if (config.isSundayFirst) Calendar.SUNDAY else Calendar.MONDAY
        }

        datepicker.show()
    }

    private fun setupEndTime() {
        hideKeyboard()
        TimePickerDialog(this, mDialogTheme, endTimeSetListener, mEventEndDateTime.hourOfDay, mEventEndDateTime.minuteOfHour, config.use24HourFormat).show()
    }

    private val startDateSetListener = DatePickerDialog.OnDateSetListener { view, year, monthOfYear, dayOfMonth ->
        dateSet(year, monthOfYear, dayOfMonth, true)
    }

    private val startTimeSetListener = TimePickerDialog.OnTimeSetListener { view, hourOfDay, minute ->
        timeSet(hourOfDay, minute, true)
    }

    private val endDateSetListener = DatePickerDialog.OnDateSetListener { view, year, monthOfYear, dayOfMonth -> dateSet(year, monthOfYear, dayOfMonth, false) }

    private val endTimeSetListener = TimePickerDialog.OnTimeSetListener { view, hourOfDay, minute -> timeSet(hourOfDay, minute, false) }

    private fun dateSet(year: Int, month: Int, day: Int, isStart: Boolean) {
        if (isStart) {
            val diff = mEventEndDateTime.seconds() - mEventStartDateTime.seconds()

            mEventStartDateTime = mEventStartDateTime.withDate(year, month + 1, day)
            updateStartDateText()
            checkRepeatRule()

            mEventEndDateTime = mEventStartDateTime.plusSeconds(diff)
            updateEndTexts()
        } else {
            mEventEndDateTime = mEventEndDateTime.withDate(year, month + 1, day)
            updateEndDateText()
        }
    }

    private fun timeSet(hours: Int, minutes: Int, isStart: Boolean) {
        if (isStart) {
            val diff = mEventEndDateTime.seconds() - mEventStartDateTime.seconds()

            mEventStartDateTime = mEventStartDateTime.withHourOfDay(hours).withMinuteOfHour(minutes)
            updateStartTimeText()

            mEventEndDateTime = mEventStartDateTime.plusSeconds(diff)
            updateEndTexts()
        } else {
            mEventEndDateTime = mEventEndDateTime.withHourOfDay(hours).withMinuteOfHour(minutes)
            updateEndTimeText()
        }
    }

    private fun checkRepeatRule() {
        if (mRepeatInterval.isXWeeklyRepetition()) {
            val day = mRepeatRule
            if (day == MONDAY_BIT || day == TUESDAY_BIT || day == WEDNESDAY_BIT || day == THURSDAY_BIT || day == FRIDAY_BIT || day == SATURDAY_BIT || day == SUNDAY_BIT) {
                setRepeatRule(Math.pow(2.0, (mEventStartDateTime.dayOfWeek - 1).toDouble()).toInt())
            }
        } else if (mRepeatInterval.isXMonthlyRepetition()) {
            if (mRepeatRule == REPEAT_MONTH_LAST_DAY && !isLastDayOfTheMonth())
                mRepeatRule = REPEAT_MONTH_SAME_DAY
            checkRepetitionRuleText()
        }
    }

    private fun updateIconColors() {
        val textColor = config.textColor
        event_time_image.applyColorFilter(textColor)
        event_repetition_image.applyColorFilter(textColor)
        event_reminder_image.applyColorFilter(textColor)
        event_type_image.applyColorFilter(textColor)
        event_caldav_calendar_image.applyColorFilter(textColor)
        event_show_on_map.applyColorFilter(getAdjustedPrimaryColor())
        find_location.applyColorFilter(textColor)
    }


    // 입력한 장소의 ID를 가져오는 함수에요.
    private fun findLocation(){
        if (event_location.value.isEmpty()) {
            toast(R.string.please_fill_location)
            return
        }

        val placeid = CurrentModule_Class().GetPlaceData(event_location.value)
        if(placeid.GetPlaceName(0) == null){
            toast("검색 결과가 없습니다.")
            location_description.setText("No Found ㅠㅠㅠㅠ").toString()
            return
        }

        val items = arrayOfNulls<CharSequence>(placeid.GetPlaceNum())
        for(i in 0..(placeid.GetPlaceNum()-1)){
            items.set(i,placeid.GetPlaceName(i))
        }
        val alertDialogBuilder = AlertDialog.Builder(this)

        // 다이얼로그 셋팅 (제목, 선택시 행동, 취소버튼)
        alertDialogBuilder.setTitle("장소를 선택해주세요")

        alertDialogBuilder.setItems(items
        ) { dialog, id ->
            // 다이얼로그 중 사용자가 하나 선택하면 그 정보를 설정한다.
            location_description.setText(items[id]).toString()
            locationId = placeid.GetPlaceid(id)
            dialog.dismiss()
        }
        alertDialogBuilder.setNeutralButton("Cancel"){dialog, which ->
            dialog.cancel()
        }

        // 다이얼로그 생성
        val alertDialog = alertDialogBuilder.create()

        // 다이얼로그 보여주기
        alertDialog.show()


        //location_description.setText(placeid.GetPlaceName(0)).toString()
        /*
        val pattern = Pattern.compile(LAT_LON_PATTERN)
        val locationValue = event_location.value
        val uri = if (pattern.matcher(locationValue).find()) {
            val delimiter = if (locationValue.contains(';')) ";" else ","
            val parts = locationValue.split(delimiter)
            val latitude = parts.first()
            val longitude = parts.last()
            Uri.parse("geo:$latitude,$longitude")
        } else {
            val location = Uri.encode(locationValue)
            Uri.parse("geo:0,0?q=$location")
        }

        val intent = Intent(Intent.ACTION_VIEW, uri)
        if (intent.resolveActivity(packageManager) != null) {
            startActivity(intent)
        } else {
            toast(R.string.no_app_found)
        }
        */
    }


/*
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            1 -> {
                val key = data?.getIntExtra("key", 0)
                alphadelay = key!!
            }
        }
    }
    */

    /*
 private fun showPopup(view: View) {
     var popup: PopupMenu? = null
     popup = PopupMenu(this, view)
     popup.inflate(R.menu.popup_menu)

     popup.setOnMenuItemClickListener(PopupMenu.OnMenuItemClickListener { item: MenuItem? ->

         when (item!!.itemId) {
             R.id.btn_1 -> {
                 config.defaultDelayAlarmTime2Value = config.defaultDelayAlarmTime2Value + 0
             }
             R.id.btn_2 -> {
                 config.defaultDelayAlarmTime2Value = config.defaultDelayAlarmTime2Value - 3
             }
             R.id.btn_3 -> {
                 config.defaultDelayAlarmTime2Value = config.defaultDelayAlarmTime2Value + 4
             }
         }

         true
     })

     popup.show()
 }
 */



}
