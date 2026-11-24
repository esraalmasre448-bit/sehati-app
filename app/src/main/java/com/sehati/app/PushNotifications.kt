package com.sehati.app

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

object AppVisibility {
    @Volatile var foreground: Boolean = false
}

/**
 * Registers one FCM token per installation. A user can have several phones;
 * each installation has a separate document and can only write its own token.
 */
object PushTokenManager {
    private const val PREFS = "sehati_push_tokens"
    private const val DEVICE_KEY = "installation_id"

    private fun installationId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val current = prefs.getString(DEVICE_KEY, null)
        if (!current.isNullOrBlank()) return current
        val created = UUID.randomUUID().toString().replace("-", "")
        prefs.edit().putString(DEVICE_KEY, created).apply()
        return created
    }

    fun sync(context: Context) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token -> saveToken(context, uid, token) }
            .addOnFailureListener { e -> Log.w("SehatiPush", "FCM token unavailable", e) }
    }

    fun saveCurrentUserToken(context: Context, token: String) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        saveToken(context, uid, token)
    }

    private fun saveToken(context: Context, uid: String, token: String) {
        if (token.isBlank()) return
        val docId = "${uid}_${installationId(context)}"
        FirebaseFirestore.getInstance().collection("fcmTokens").document(docId)
            .set(
                mapOf(
                    "uid" to uid,
                    "token" to token,
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            )
            .addOnFailureListener { e -> Log.w("SehatiPush", "Could not register this device", e) }
    }

    /** Delete this installation's old account token before signing out. */
    fun unregister(context: Context, onComplete: () -> Unit) {
        DoctorWorkplaceGeofencing.remove(context)
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid.isNullOrBlank()) {
            onComplete()
            return
        }
        val docId = "${uid}_${installationId(context)}"
        FirebaseFirestore.getInstance().collection("fcmTokens").document(docId)
            .delete()
            .addOnCompleteListener { onComplete() }
    }
}

/** Sends authenticated event references; the Worker verifies the caller and reads the source data. */
object PushSender {
    private fun post(path: String, payload: org.json.JSONObject) {
        val endpoint = BuildConfig.PUSH_WORKER_URL.trim().trimEnd('/')
        if (endpoint.isBlank()) {
            Log.w("SehatiPush", "PUSH_WORKER_URL is not configured; push delivery is disabled")
            return
        }
        val user = FirebaseAuth.getInstance().currentUser ?: return
        user.getIdToken(false).addOnSuccessListener { tokenResult ->
            val idToken = tokenResult.token ?: return@addOnSuccessListener
            Thread {
                var connection: HttpURLConnection? = null
                try {
                    connection = (URL(endpoint + path).openConnection() as HttpURLConnection).apply {
                        requestMethod = "POST"
                        connectTimeout = 10000
                        readTimeout = 15000
                        doOutput = true
                        setRequestProperty("Authorization", "Bearer $idToken")
                        setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    }
                    val body = payload.toString()
                    connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                    val status = connection.responseCode
                    if (status !in 200..299) {
                        Log.w("SehatiPush", "Push relay returned HTTP $status for $path")
                    }
                } catch (e: Exception) {
                    Log.w("SehatiPush", "Push relay request failed for $path", e)
                } finally {
                    connection?.disconnect()
                }
            }.start()
        }.addOnFailureListener { e ->
            Log.w("SehatiPush", "Could not obtain Firebase ID token", e)
        }
    }

    fun sendAfterMessage(convId: String, messageId: String) {
        post(
            "/send",
            org.json.JSONObject().put("convId", convId).put("messageId", messageId)
        )
    }

    fun notifyAppointment(appointmentId: String, event: String) {
        post(
            "/notify-appointment",
            org.json.JSONObject().put("appointmentId", appointmentId).put("event", event)
        )
    }

    fun notifyQueueAdvanced(date: String) {
        post("/notify-queue", org.json.JSONObject().put("date", date))
    }

    fun notifyPrescription(prescriptionId: String) {
        post("/notify-prescription", org.json.JSONObject().put("prescriptionId", prescriptionId))
    }

    fun notifyFollowUp(appointmentId: String) {
        notifyAppointment(appointmentId, "followup_scheduled")
    }
}

/** Displays FCM alerts when the app is backgrounded; foreground alerts use the in-app watcher. */
class SehatiMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        PushTokenManager.saveCurrentUserToken(this, token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val type = message.data["type"].orEmpty()
        // Chat alerts are intentionally shown in the system tray even while chatting.
        // Appointment and queue alerts are shown locally by the watcher while foregrounded.
        if (AppVisibility.foreground && type != "chat") return

        val title = message.data["title"] ?: message.notification?.title ?: "إشعار جديد"
        val body = message.data["body"] ?: message.notification?.body ?: "لديك تحديث جديد"
        val convId = message.data["convId"].orEmpty()
        val reference = convId.ifBlank {
            message.data["appointmentId"] ?: message.data["date"].orEmpty()
        }

        val open = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            if (convId.isNotBlank()) putExtra("open_conv_id", convId)
        }
        val pending = PendingIntent.getActivity(
            this,
            reference.hashCode(),
            open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, "sehati_general")
            .setSmallIcon(R.drawable.ic_stat_sehati)
            .setLargeIcon(BitmapFactory.decodeResource(resources, R.drawable.ic_launcher))
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(if (type == "chat") NotificationCompat.CATEGORY_MESSAGE else NotificationCompat.CATEGORY_STATUS)
            .build()

        try {
            NotificationManagerCompat.from(this)
                .notify((System.currentTimeMillis() and 0x7fffffff).toInt(), notification)
        } catch (e: SecurityException) {
            Log.w("SehatiPush", "Notification permission is not granted", e)
        }
    }
}