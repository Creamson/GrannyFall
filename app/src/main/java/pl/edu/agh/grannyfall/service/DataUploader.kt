package pl.edu.agh.grannyfall.service

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkInfo
import android.widget.Toast
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import pl.edu.agh.grannyfall.service.model.SensorData
import java.text.DateFormat
import java.util.*

class DataUploader(private val ctx: Context) {

    private val cloudStorage: StorageReference = FirebaseStorage.getInstance().reference
    private val connectivityManager: ConnectivityManager =
        ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private var waitList: MutableList<List<SensorData>> = mutableListOf()

    private val mutex: Mutex = Mutex()

    fun upload(dataBatch: List<SensorData>) {
        GlobalScope.launch {
            var currentWaiting: List<List<SensorData>>? = null
            mutex.withLock {
                waitList.add(dataBatch)
                currentWaiting = waitList.toList()
                waitList.clear()
            }
            val network = connectivityManager.activeNetworkInfo

            if (network?.type == ConnectivityManager.TYPE_WIFI) {
                currentWaiting?.forEach {
                    CoroutineScope(Dispatchers.IO).launch { saveData(it) }
                }
            } else {
                mutex.withLock { waitList.addAll(currentWaiting!!) }
            }
        }
    }

    private suspend fun saveData(data: List<SensorData>) = coroutineScope {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = data[0].measurementMillis


        val objectReference = cloudStorage.child("data/${formatter.format(calendar.time)}")
        data.joinToString("\n") { it.toString() }.byteInputStream().use {
            objectReference.putStream(it).addOnSuccessListener {
                CoroutineScope(Dispatchers.Main).launch {
                    Toast.makeText(ctx, "Batch processed: ${data.size}", Toast.LENGTH_SHORT).show()
                }
            }.addOnFailureListener { e ->
                e.printStackTrace()
            }
        }
    }

    companion object {
        private val formatter: DateFormat = DateFormat.getDateTimeInstance()
    }
}