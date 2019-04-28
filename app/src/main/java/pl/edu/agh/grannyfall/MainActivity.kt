package pl.edu.agh.grannyfall

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.support.v7.app.AppCompatActivity
import android.view.View
import kotlinx.android.synthetic.main.activity_main.*
import pl.edu.agh.grannyfall.service.BehaviourServiceBinder
import pl.edu.agh.grannyfall.service.BehaviourTrackingService

class MainActivity : AppCompatActivity() {

    private var serviceBinder: BehaviourServiceBinder? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceDisconnected(name: ComponentName?) {
            serviceBinder = null
        }

        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            serviceBinder = service as BehaviourServiceBinder
        }

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (BehaviourTrackingService.isRunning.get()) {
            buttonStartRecording.visibility = View.INVISIBLE
            buttonStopRecording.visibility = View.VISIBLE
        }

        setupButtonListeners()

        switchWifiRequired.setOnCheckedChangeListener { _, isChecked ->
            serviceBinder?.uploadWithoutWifi = isChecked
        }
    }

    private fun setupButtonListeners() {
        buttonStartRecording.setOnClickListener {
            buttonStartRecording.visibility = View.INVISIBLE
            buttonStopRecording.visibility = View.VISIBLE
            startService()
        }

        buttonStopRecording.setOnClickListener {
            buttonStartRecording.visibility = View.VISIBLE
            buttonStopRecording.visibility = View.INVISIBLE
            stopService()
        }
    }

    private fun startService() {
        val startServiceIntent = Intent(this, BehaviourTrackingService::class.java)
        bindService(startServiceIntent, serviceConnection, Context.BIND_AUTO_CREATE)
        startService(startServiceIntent)
    }

    private fun stopService() {
        stopService(Intent(this, BehaviourTrackingService::class.java))
        if (serviceBinder != null) {
            unbindService(serviceConnection)
        }
    }

    override fun onSaveInstanceState(outState: Bundle?) {
        super.onSaveInstanceState(outState)
        outState!!.putBoolean("started", true)
    }
}
