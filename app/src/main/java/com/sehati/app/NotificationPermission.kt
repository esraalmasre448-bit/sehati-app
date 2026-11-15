package com.sehati.app

import android.Manifest
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build

/**
 * إعداد قناة الإشعارات وطلب إذن Android 13+ مرة واحدة عند تشغيل التطبيق.
 * رفض الإذن لا يمنع استخدام التطبيق؛ يمكن تفعيله لاحقاً من إعدادات النظام.
 */
object NotificationPermission {
    private const val CHANNEL_ID = "sehati_general"
    private const val PREFS = "sehati_notification_permission"
    private const val REQUESTED_KEY = "requested_once"
    private const val REQUEST_CODE = 7301

    fun prepareAndRequest(activity: Activity) {
        createChannel(activity)

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        ) return

        val prefs = activity.getSharedPreferences(PREFS, Activity.MODE_PRIVATE)
        if (prefs.getBoolean(REQUESTED_KEY, false)) return

        // نسجل المحاولة قبل إظهار نافذة النظام لمنع تكرار الطلب عند إعادة إنشاء Activity.
        prefs.edit().putBoolean(REQUESTED_KEY, true).apply()
        activity.requestPermissions(
            arrayOf(Manifest.permission.POST_NOTIFICATIONS),
            REQUEST_CODE
        )
    }

    private fun createChannel(activity: Activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = activity.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            "إشعارات الطريق الصحي",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "تنبيهات المواعيد والدور والرسائل والوصفات الطبية"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 350, 180, 350)
            setShowBadge(true)
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
        }
        manager.createNotificationChannel(channel)
    }
}
