package pl.edu.agh.grannyfall.service

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import com.github.pwittchen.reactivesensors.library.ReactiveSensorEvent
import com.github.pwittchen.reactivesensors.library.ReactiveSensorFilter
import com.github.pwittchen.reactivesensors.library.ReactiveSensors
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.BehaviorSubject
import pl.edu.agh.grannyfall.service.model.CompoundSensorEvent

class EventSource(private val ctx: Context) {

    private val accelerometerEvents: BehaviorSubject<ReactiveSensorEvent> = BehaviorSubject.create()
    private val gyroscopeEvents: BehaviorSubject<ReactiveSensorEvent> = BehaviorSubject.create()

    private val compositeDisposable = CompositeDisposable()

    fun start() {
        val reactiveSensors = ReactiveSensors(ctx)

        subscribeToGyroscope(reactiveSensors)
        subscribeToAccelerometer(reactiveSensors)
    }

    fun lastCompoundEvent(): CompoundSensorEvent {
        return CompoundSensorEvent(accelerometerEvents.value, gyroscopeEvents.value)
    }

    fun stop() {
        accelerometerEvents.onComplete()
        gyroscopeEvents.onComplete()
        compositeDisposable.dispose()
    }

    private fun subscribeToAccelerometer(reactiveSensors: ReactiveSensors) {
        subscribeToSensor(reactiveSensors, Sensor.TYPE_ACCELEROMETER, accelerometerEvents)
    }

    private fun subscribeToGyroscope(reactiveSensors: ReactiveSensors) {
        subscribeToSensor(reactiveSensors, Sensor.TYPE_GYROSCOPE, gyroscopeEvents)
    }

    private fun subscribeToSensor(
        reactiveSensors: ReactiveSensors,
        sensorType: Int,
        subject: BehaviorSubject<ReactiveSensorEvent>
    ) {
        if (reactiveSensors.hasSensor(sensorType)) {
            val accelerometerDisposable =
                reactiveSensors.observeSensor(sensorType, SensorManager.SENSOR_DELAY_FASTEST)
                    .subscribeOn(Schedulers.computation())
                    .filter(ReactiveSensorFilter.filterSensorChanged())
                    .subscribe(subject::onNext)

            compositeDisposable.add(accelerometerDisposable)
        }
    }
}