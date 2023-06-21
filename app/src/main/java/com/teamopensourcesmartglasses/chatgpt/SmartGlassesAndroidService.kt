package com.teamopensourcesmartglasses.chatgpt

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import com.teamopensmartglasses.sgmlib.events.KillTpaEvent
import org.greenrobot.eventbus.Subscribe

//a service provided for third party apps to extend, that make it easier to create a service in Android that will continually run in the background
abstract class SmartGlassesAndroidService(
    private val mainActivityClass: Class<*>,
    private val myChannelId: String,
    private val myNotificationId: Int,
    private val notificationAppName: String,
    private val notificationDescription: String,
    private val notificationDrawable: Int
) : LifecycleService() {
    // Service Binder given to clients
    private val binder: IBinder = LocalBinder()

    //service stuff
    private fun updateNotification(): Notification {
        val context = applicationContext
        val action = PendingIntent.getActivity(
            context,
            0, Intent(context, mainActivityClass),
            PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_MUTABLE
        ) // Flag indicating that if the described PendingIntent already exists, the current one should be canceled before generating a new one.
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val builder: NotificationCompat.Builder
        val CHANNEL_ID = myChannelId
        val channel = NotificationChannel(
            CHANNEL_ID, notificationAppName,
            NotificationManager.IMPORTANCE_HIGH
        )
        channel.description = notificationDescription
        manager.createNotificationChannel(channel)
        builder = NotificationCompat.Builder(this, CHANNEL_ID)
        return builder.setContentIntent(action)
            .setContentTitle(notificationAppName)
            .setContentText(notificationDescription)
            .setSmallIcon(notificationDrawable)
            .setTicker("...")
            .setContentIntent(action)
            .setOngoing(true).build()
    }

    inner class LocalBinder : Binder() {
        // Return this instance of LocalService so clients can call public methods
        val service: SmartGlassesAndroidService
            get() =// Return this instance of LocalService so clients can call public methods
                this@SmartGlassesAndroidService
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent != null) {
            var action = intent.action
            val extras = intent.extras

            //True when service is started from SGM
            if (action === INTENT_ACTION && extras != null) {
                action = extras[TPA_ACTION] as String?
            }
            when (action) {
                ACTION_START_FOREGROUND_SERVICE -> {
                    // start the service in the foreground
                    Log.d("TEST", "starting foreground")
                    startForeground(myNotificationId, updateNotification())
                }

                ACTION_STOP_FOREGROUND_SERVICE -> {
                    stopForeground(true)
                    stopSelf()
                }
            }
        }
        return START_STICKY
    }

    @Subscribe
    fun onKillTpaEvent(receivedEvent: KillTpaEvent?) {
        //if(receivedEvent.uuid == this.appUUID) //TODO: Figure out implementation here...
        if (true) {
            stopSelf()
        }
    }

    companion object {
        const val INTENT_ACTION = "SGM_COMMAND_INTENT"
        const val TPA_ACTION = "tpaAction"
        const val ACTION_START_FOREGROUND_SERVICE = "SGMLIB_ACTION_START_FOREGROUND_SERVICE"
        const val ACTION_STOP_FOREGROUND_SERVICE = "SGMLIB_ACTION_STOP_FOREGROUND_SERVICE"
    }
}