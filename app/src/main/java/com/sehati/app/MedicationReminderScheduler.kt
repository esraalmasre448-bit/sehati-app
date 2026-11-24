package com.sehati.app

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

object MedicationReminderScheduler {
    private const val PREFS = "medication_local_reminders"
    private const val CHANNEL = "medication_daily"

    fun schedule(context: Context, id: String, medicine: String, dose: String, time: String) {
        val parts = time.split(":")
        if (parts.size != 2) return
        val hour = parts[0].toIntOrNull() ?: return
        val minute = parts[1].toIntOrNull() ?: return
        if (hour !in 0..23 || minute !in 0..59 || id.isBlank()) return

        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(id, listOf(medicine, dose, time).joinToString("\u001f")).apply()

        val next = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
        }
        val intent = Intent(context, MedicationReminderReceiver::class.java).apply {
            putExtra("id", id)
            putExtra("medicine", medicine)
            putExtra("dose", dose)
            putExtra("time", time)
        }
        val pending = PendingIntent.getBroadcast(
            context, id.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarm.setInexactRepeating(
            AlarmManager.RTC_WAKEUP, next.timeInMillis, AlarmManager.INTERVAL_DAY, pending
        )
    }

    fun cancel(context: Context, id: String) {
        val intent = Intent(context, MedicationReminderReceiver::class.java)
        val pending = PendingIntent.getBroadcast(
            context, id.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pending)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(id).apply()
    }

    fun cancelAll(context: Context) {
        val ids = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).all.keys.toList()
        ids.forEach { cancel(context, it) }
    }

    fun restore(context: Context) {
        val all = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).all
        all.forEach { (id, raw) ->
            val values = (raw as? String)?.split("\u001f") ?: return@forEach
            if (values.size == 3) schedule(context, id, values[0], values[1], values[2])
        }
    }

    internal fun channelId() = CHANNEL
}

class MedicationReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val medicine = intent.getStringExtra("medicine").orEmpty()
        val dose = intent.getStringExtra("dose").orEmpty()
        val id = intent.getStringExtra("id").orEmpty()
        if (medicine.isBlank() || id.isBlank()) return

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    MedicationReminderScheduler.channelId(),
                    "تذكيرات الأدوية",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply { description = "تذكير يومي بالأدوية التي سجلها المستخدم" }
            )
        }
        val openApp = PendingIntent.getActivity(
            context, 7001,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, MedicationReminderScheduler.channelId())
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(context)
        }.setSmallIcon(R.drawable.ic_stat_sehati)
            .setContentTitle("موعد الدواء")
            .setContentText("$medicine — $dose")
            .setStyle(Notification.BigTextStyle().bigText("حان وقت $medicine. الجرعة المسجلة: $dose"))
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .build()
        manager.notify(id.hashCode(), notification)
    }
}

class MedicationReminderBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            MedicationReminderScheduler.restore(context)
        }
    }
}
