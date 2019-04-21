package pl.edu.agh.grannyfall

import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.View
import kotlinx.android.synthetic.main.activity_main.*
import pl.edu.agh.grannyfall.service.BehaviourTrackingService

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (BehaviourTrackingService.isRunning.get()) {
            buttonStartRecording.visibility = View.INVISIBLE
            buttonStopRecording.visibility = View.VISIBLE
        }

        setupButtonListeners()
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
        startService(startServiceIntent)
    }

    private fun stopService() {
        stopService(Intent(this, BehaviourTrackingService::class.java))
    }

    override fun onSaveInstanceState(outState: Bundle?) {
        super.onSaveInstanceState(outState)
        outState!!.putBoolean("started", true)
    }
}
