package pl.edu.agh.grannyfall.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.PowerManager
import android.support.v4.app.NotificationCompat
import android.widget.Toast
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.BehaviorSubject
import io.reactivex.subjects.Subject
import pl.edu.agh.grannyfall.MainActivity
import pl.edu.agh.grannyfall.R
import pl.edu.agh.grannyfall.service.model.SensorData
import java.io.InputStream
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.logging.Level
import java.util.logging.Logger


class BehaviourTrackingService : Service() {

    private lateinit var notificationBuilder: NotificationCompat.Builder
    private lateinit var wakeLock: PowerManager.WakeLock
    private lateinit var eventSource: EventSource
    private lateinit var uploader: DataUploader

    private lateinit var disposable: Disposable
    private val schedulerSubject: Subject<Long> = BehaviorSubject.create()
    val uploadWithoutWifi = AtomicBoolean(false)

    override fun onCreate() {
        super.onCreate()
        setupNotificationBuilder()

        eventSource = EventSource(this)
        uploader = DataUploader(this)

        val powerManager = this.getSystemService(Context.POWER_SERVICE) as PowerManager

        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            LOCK_NAME
        )
        wakeLock.acquire()
    }

    private fun setupNotificationBuilder() {
        this.notificationBuilder = NotificationCompat.Builder(this)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Granny Fall")
            .setOngoing(true)
            .setPriority(Notification.PRIORITY_HIGH)
            .setStyle(NotificationCompat.BigTextStyle().bigText("Recording behaviour."))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        whenNotNull(intent) {
            isRunning.set(true)
            eventSource.start()

            disposable = Observable.interval(10, TimeUnit.MILLISECONDS)
                .subscribeOn(Schedulers.computation())
                .subscribe(schedulerSubject::onNext)

            schedulerSubject.subscribeOn(Schedulers.computation())
                .map { eventSource.lastCompoundEvent() }
                .map { it.rawData }
                .buffer(10, TimeUnit.MINUTES)
//                .buffer(15, TimeUnit.SECONDS)
                .observeOn(Schedulers.io())
                .subscribe {
                    uploader.upload(it, uploadWithoutWifi.get())
                }

            this.notificationBuilder
                .setContentIntent(contentIntent(this))
            startForeground(
                ROUTE_UPDATES_FOREGROUND_ID,
                this.notificationBuilder.build()
            )
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun contentIntent(context: Context): PendingIntent {
        val openActivityIntent = Intent(context, MainActivity::class.java)
            .putExtra("fromService", true)

        return PendingIntent.getActivity(
            context,
            ROUTE_UPDATES_PENDING_INTENT_ID,
            openActivityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT
        )
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
        return BehaviourServiceBinder(this)
    }

    companion object {
        private const val LOCK_NAME: String = "myapp:lockname"
        private const val ROUTE_UPDATES_PENDING_INTENT_ID: Int = 3336
        private const val ROUTE_UPDATES_FOREGROUND_ID: Int = 4329

        val isRunning = AtomicBoolean()
    }
}

class BehaviourServiceBinder(private val service: BehaviourTrackingService) : Binder() {
    var uploadWithoutWifi: Boolean
        get() = service.uploadWithoutWifi.get()
        set(value) = service.uploadWithoutWifi.set(value)
}

inline fun <T : Any, R> whenNotNull(input: T?, callback: (T) -> R): R? {
    return input?.let(callback)
}
