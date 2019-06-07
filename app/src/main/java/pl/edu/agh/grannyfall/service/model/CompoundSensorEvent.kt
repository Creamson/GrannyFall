package pl.edu.agh.grannyfall.service.model

import com.github.pwittchen.reactivesensors.library.ReactiveSensorEvent

class CompoundSensorEvent(
    accelerometerEvent: ReactiveSensorEvent?,
    gyroscopeEvent: ReactiveSensorEvent?
) {

    val rawData: SensorData

    init {
        val accelerometerMeasurements = accelerometerEvent?.sensorEvent?.values
        val gyroscopeMeasurements = gyroscopeEvent?.sensorEvent?.values
        val currentMillis = System.currentTimeMillis()
        this.rawData = SensorData(
            currentMillis,
            accelerometerMeasurements?.get(0),
            accelerometerMeasurements?.get(1),
            accelerometerMeasurements?.get(2),
            gyroscopeMeasurements?.get(0),
            gyroscopeMeasurements?.get(1),
            gyroscopeMeasurements?.get(2)
        )
    }
}

data class SensorData(
    val measurementMillis: Long,
    val accelerometerX: Float?,
    val accelerometerY: Float?,
    val accelerometerZ: Float?,
    val gyroscopeX: Float?,
    val gyroscopeY: Float?,
    val gyroscopeZ: Float?
) {
    override fun toString(): String {
        return "$measurementMillis, $accelerometerX, $accelerometerY, $accelerometerZ, $gyroscopeX, $gyroscopeY, $gyroscopeZ"
    }

    fun toArrayUnsafe(): Array<Float> {
        return arrayOf(
            accelerometerX!!,
            accelerometerY!!,
            accelerometerZ!!,
            gyroscopeX!!,
            gyroscopeY!!,
            gyroscopeZ!!
        )
    }
}