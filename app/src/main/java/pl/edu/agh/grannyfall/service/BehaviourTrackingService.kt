package pl.edu.agh.grannyfall.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.support.v4.app.NotificationCompat
import android.support.v4.content.ContextCompat
import io.reactivex.Observable
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.BehaviorSubject
import io.reactivex.subjects.Subject
import pl.edu.agh.grannyfall.MainActivity
import pl.edu.agh.grannyfall.R
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean


class BehaviourTrackingService : Service() {

    private lateinit var wakeLock: PowerManager.WakeLock
    private lateinit var eventSource: EventSource
    private val disposable = CompositeDisposable()
    private lateinit var fallTracker: FallTracker

    private val schedulerSubject: Subject<Long> = BehaviorSubject.create()

    override fun onCreate() {
        super.onCreate()

        eventSource = EventSource(this)

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager

        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            LOCK_NAME
        )
        wakeLock.acquire()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        whenNotNull(intent) {
            isRunning.set(true)
            eventSource.start()
            fallTracker = FallTracker(this, intent!!.getStringExtra("url"))

            disposable.add(Observable.interval(10, TimeUnit.MILLISECONDS)
                .subscribeOn(Schedulers.computation())
                .subscribe(schedulerSubject::onNext))


            val sensorEventSource = schedulerSubject.subscribeOn(Schedulers.computation())
                .map { eventSource.lastCompoundEvent() }
                .map { it.rawData }
                .filter { it.accelerometerX != null && it.gyroscopeX != null }

            disposable.add(fallTracker.start(sensorEventSource))


            startForeground(
                ROUTE_UPDATES_FOREGROUND_ID,
                generateBigTextStyleNotification()
            )
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning.set(false)
        eventSource.stop()
        disposable.dispose()
        schedulerSubject.onComplete()
        cleanUp()
    }

    private fun cleanUp() {
        stopForeground(true)
        this.wakeLock.release()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        isRunning.set(false)
        eventSource.stop()
        disposable.dispose()
        schedulerSubject.onComplete()
        stopSelf()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return BehaviourServiceBinder()
    }

    private fun generateBigTextStyleNotification(): Notification? {

        // 1. Create/Retrieve Notification Channel for O and beyond devices (26+).
        val notificationChannelId = createNotificationChannel(this)


        // 2. Build the BIG_TEXT_STYLE.
        val bigTextStyle = NotificationCompat.BigTextStyle()
            // Overrides ContentText in the big form of the template.
            .bigText("Behaviour Service")
            // Overrides ContentTitle in the big form of the template.
            .setBigContentTitle("Granny Fall")

        // 3. Set up main Intent for notification.
        val notifyIntent = Intent(this, MainActivity::class.java)

        // When creating your Intent, you need to take into account the back state, i.e., what
        // happens after your Activity launches and the user presses the back button.

        // There are two options:
        //      1. Regular activity - You're starting an Activity that's part of the application's
        //      normal workflow.

        //      2. Special activity - The user only sees this Activity if it's started from a
        //      notification. In a sense, the Activity extends the notification by providing
        //      information that would be hard to display in the notification itself.

        // For the BIG_TEXT_STYLE notification, we will consider the activity launched by the main
        // Intent as a special activity, so we will follow option 2.

        // For an example of option 1, check either the MESSAGING_STYLE or BIG_PICTURE_STYLE
        // examples.

        // For more information, check out our dev article:
        // https://developer.android.com/training/notify-user/navigation.html

        // Sets the Activity to start in a new, empty task
        notifyIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK

        val notifyPendingIntent = PendingIntent.getActivity(
            this,
            ROUTE_UPDATES_PENDING_INTENT_ID,
            notifyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT
        )

        // 5. Build and issue the notification.

        // Because we want this to be a new notification (not updating a previous notification), we
        // create a new Builder. Later, we use the same global builder to get back the notification
        // we built here for the snooze action, that is, canceling the notification and relaunching
        // it several seconds later.

        // Notification Channel Id is ignored for Android pre O (26).
        val notificationCompatBuilder = NotificationCompat.Builder(
            applicationContext, notificationChannelId ?: ""
        )

        return notificationCompatBuilder
            // BIG_TEXT_STYLE sets title and content for API 16 (4.1 and after).
            .setStyle(bigTextStyle)
            // Content for API <24 (7.0 and below) devices.
            .setContentText("Behaviour Service Content Text")
            .setContentIntent(notifyPendingIntent)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            // Set primary color (important for Wear 2.0 Notifications).
            .setColor(ContextCompat.getColor(applicationContext, R.color.colorPrimary))

            // SIDE NOTE: Auto-bundling is enabled for 4 or more notifications on API 24+ (N+)
            // devices and all Wear devices. If you have more than one notification and
            // you prefer a different summary notification, set a group key and create a
            // summary notification via
            // .setGroupSummary(true)
            // .setGroup(GROUP_KEY_YOUR_NAME_HERE)

            .setCategory(Notification.CATEGORY_REMINDER)

            // Sets priority for 25 and below. For 26 and above, 'priority' is deprecated for
            // 'importance' which is set in the NotificationChannel. The integers representing
            // 'priority' are different from 'importance', so make sure you don't mix them.
            .setPriority(Notification.PRIORITY_DEFAULT)

            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .build()
    }


    companion object {
        private const val LOCK_NAME: String = "grannyfall:lockname"
        private const val ROUTE_UPDATES_PENDING_INTENT_ID: Int = 3336
        private const val ROUTE_UPDATES_FOREGROUND_ID: Int = 4329

        val isRunning = AtomicBoolean()
    }
}

fun createNotificationChannel(
    context: Context
): String? {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

        val channelId = "grannyfall:behaviourTrackingNotification"

        val notificationChannel = NotificationChannel(
            channelId,
            "GrannyFall channel",
            NotificationManager.IMPORTANCE_HIGH
        )
        notificationChannel.description =
            "This is a channel used for displaying a foreground notification for the GrannyFall service"
        notificationChannel.enableVibration(false)
        notificationChannel.lockscreenVisibility = Notification.VISIBILITY_PRIVATE

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(notificationChannel)

        return channelId
    } else {
        // Returns null for pre-O (26) devices.
        return null
    }
}

class BehaviourServiceBinder : Binder()

inline fun <T : Any, R> whenNotNull(input: T?, callback: (T) -> R): R? {
    return input?.let(callback)
}
