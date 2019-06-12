package pl.edu.agh.grannyfall.service

import android.content.Context
import android.media.MediaPlayer
import android.os.Build
import android.os.VibrationEffect
import android.os.VibrationEffect.createOneShot
import android.os.Vibrator
import android.util.Log
import android.widget.Toast
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.functions.BiFunction
import io.reactivex.schedulers.Schedulers
import okhttp3.OkHttpClient
import pl.edu.agh.grannyfall.R
import pl.edu.agh.grannyfall.service.model.SensorData
import retrofit2.Retrofit
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import kotlin.math.pow


class FallTracker(private val context: Context, baseUrl: String) {

    private val anomalyDetectorClient: AnomalyDetectorClient
    private val size: Single<Int>
    private val threshold: Single<Int>
    private val vibrator: Vibrator
    private val mediaPlayer: MediaPlayer

    init {
        val retrofit = Retrofit.Builder()
            .baseUrl("http://$baseUrl")
            .client(OkHttpClient.Builder().build())
            .addConverterFactory(GsonConverterFactory.create())
            .addCallAdapterFactory(RxJava2CallAdapterFactory.createAsync())
            .build()

        anomalyDetectorClient = retrofit.create(AnomalyDetectorClient::class.java)
        size = anomalyDetectorClient.size().cache()
        threshold = anomalyDetectorClient.threshold()
        vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        mediaPlayer = MediaPlayer.create(context, R.raw.falling)
    }

    private val vibrationDuration = 5000L

    fun start(data: Observable<SensorData>): Disposable {
        return size.zipWith(threshold, BiFunction<Int, Int, Observable<Boolean>> { sizeValue, thresholdValue ->
            data.observeOn(Schedulers.computation())
                .map { it.toArrayUnsafe() }
                .buffer(sizeValue)
                .map { it.flatMap { a -> a.asIterable() } }
                .observeOn(Schedulers.io())
                .flatMapSingle { input ->
                    anomalyDetectorClient
                        .compute(input)
                        .observeOn(Schedulers.computation())
                        .map { output -> meanSquaredErr(input, output) }
                }
                .doOnNext { Log.i("Tag", "mean square error: $it") }
                .map { it > thresholdValue }
        }).flatMapObservable { it }
            .filter { it }
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(createOneShot(vibrationDuration, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    //deprecated in API 26
                    vibrator.vibrate(vibrationDuration)
                }
                mediaPlayer.start()
            }, {
                Toast.makeText(context, "Fail within Fall Tracker.", Toast.LENGTH_LONG).show()
                it.printStackTrace()
            })
    }

    private fun meanSquaredErr(input: List<Float>, output: List<Float>): Float {
        val squaredError = input
            .zip(output)
            .map { (it.first - it.second).pow(2) }
            .reduce { a, b -> a + b }
        return squaredError / input.size.toFloat()
    }
}

interface AnomalyDetectorClient {
    @GET("/threshold")
    fun threshold(): Single<Int>

    @GET("/size")
    fun size(): Single<Int>

    @POST("/compute")
    fun compute(@Body data: List<Float>): Single<List<Float>>
}