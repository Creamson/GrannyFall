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

        val notificationChannelId = createNotificationChannel(this)

        val bigTextStyle = NotificationCompat.BigTextStyle()
            .bigText("Behaviour Service")
            .setBigContentTitle("Granny Fall")

        val notifyIntent = Intent(this, MainActivity::class.java)

        notifyIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK

        val notifyPendingIntent = PendingIntent.getActivity(
            this,
            ROUTE_UPDATES_PENDING_INTENT_ID,
            notifyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notificationCompatBuilder = NotificationCompat.Builder(
            applicationContext, notificationChannelId ?: ""
        )

        return notificationCompatBuilder
            .setStyle(bigTextStyle)
            .setContentText("Behaviour Service Content Text")
            .setContentIntent(notifyPendingIntent)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setColor(ContextCompat.getColor(applicationContext, R.color.colorPrimary))
            .setCategory(Notification.CATEGORY_REMINDER)
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
        return null
    }
}

class BehaviourServiceBinder : Binder()

inline fun <T : Any, R> whenNotNull(input: T?, callback: (T) -> R): R? {
    return input?.let(callback)
}
