package com.sehati.app

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

/**
 * Registers two workplace geofences. Coordinates are used only for enter/exit detection;
 * the app does not save a route or continuously upload GPS coordinates.
 */
object DoctorWorkplaceGeofencing {
    private const val PREFS = "sehati_workplace_geofences"
    private const val PREF_UID = "doctor_uid"
    private const val REQUEST_CODE = 7314
    private const val RADIUS_METERS = 180f

    fun hasForegroundPermission(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun hasBackgroundPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            context.checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun sync(context: Context, uid: String, onComplete: (() -> Unit)? = null) {
        val app = context.applicationContext
        if (uid.isBlank() || !hasForegroundPermission(app) || !hasBackgroundPermission(app)) {
            onComplete?.invoke()
            return
        }

        FirebaseFirestore.getInstance().collection("doctors").document(uid).get()
            .addOnSuccessListener { doc ->
                if (!doc.exists()) {
                    onComplete?.invoke()
                    return@addOnSuccessListener
                }
                val places = doc.get("workLocations") as? Map<*, *> ?: emptyMap<Any, Any>()
                val geofences = listOf("clinic", "hospital").mapNotNull { id ->
                    val place = places[id] as? Map<*, *> ?: return@mapNotNull null
                    val lat = (place["lat"] as? Number)?.toDouble() ?: return@mapNotNull null
                    val lng = (place["lng"] as? Number)?.toDouble() ?: return@mapNotNull null
                    if (lat !in -90.0..90.0 || lng !in -180.0..180.0) return@mapNotNull null
                    Geofence.Builder()
                        .setRequestId(id)
                        .setCircularRegion(lat, lng, RADIUS_METERS)
                        .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT)
                        .setLoiteringDelay(60_000)
                        .setNotificationResponsiveness(30_000)
                        .setExpirationDuration(Geofence.NEVER_EXPIRE)
                        .build()
                }

                val client = LocationServices.getGeofencingClient(app)
                val pendingIntent = geofencePendingIntent(app)
                if (geofences.isEmpty()) {
                    client.removeGeofences(pendingIntent).addOnCompleteListener { onComplete?.invoke() }
                    return@addOnSuccessListener
                }
                val request = GeofencingRequest.Builder()
                    .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
                    .addGeofences(geofences)
                    .build()

                client.removeGeofences(pendingIntent).addOnCompleteListener {
                    client.addGeofences(request, pendingIntent)
                        .addOnSuccessListener {
                            app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                                .edit().putString(PREF_UID, uid).apply()
                        }
                        .addOnCompleteListener { onComplete?.invoke() }
                }
            }
            .addOnFailureListener { onComplete?.invoke() }
    }

    fun remove(context: Context) {
        val app = context.applicationContext
        try {
            LocationServices.getGeofencingClient(app)
                .removeGeofences(geofencePendingIntent(app))
                .addOnCompleteListener {
                    app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                        .edit().remove(PREF_UID).apply()
                }
        } catch (_: SecurityException) {
            app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().remove(PREF_UID).apply()
        }
    }

    private fun geofencePendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, DoctorWorkplaceGeofenceReceiver::class.java)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        return PendingIntent.getBroadcast(context, REQUEST_CODE, intent, flags)
    }

    internal fun savedDoctorUid(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(PREF_UID, "").orEmpty()
}

class DoctorWorkplaceGeofenceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError()) return
        val transition = event.geofenceTransition
        if (transition != Geofence.GEOFENCE_TRANSITION_ENTER &&
            transition != Geofence.GEOFENCE_TRANSITION_EXIT
        ) return

        val app = context.applicationContext
        val uid = DoctorWorkplaceGeofencing.savedDoctorUid(app)
            .ifBlank { FirebaseAuth.getInstance().currentUser?.uid.orEmpty() }
        if (uid.isBlank()) return

        val ids = event.triggeringGeofences.orEmpty().map { it.requestId }.distinct()
        if (ids.isEmpty()) return
        val pending = goAsync()
        val ref = FirebaseFirestore.getInstance().collection("doctors").document(uid)
        ref.get().addOnSuccessListener { snapshot ->
            val currentId = snapshot.getString("currentWorkLocationId")
            if (transition == Geofence.GEOFENCE_TRANSITION_ENTER) {
                val id = ids.first()
                val places = snapshot.get("workLocations") as? Map<*, *>
                val place = places?.get(id) as? Map<*, *>
                val name = place?.get("name")?.toString().orEmpty()
                    .ifBlank { if (id == "hospital") "المشفى" else "العيادة" }
                ref.update(
                    mapOf(
                        "currentWorkLocationId" to id,
                        "currentWorkPlaceName" to name,
                        "locationStatus" to "AT_WORK",
                        "locationUpdatedAt" to FieldValue.serverTimestamp()
                    )
                )
            } else if (!currentId.isNullOrBlank() && ids.contains(currentId)) {
                ref.update(
                    mapOf(
                        "currentWorkLocationId" to FieldValue.delete(),
                        "currentWorkPlaceName" to FieldValue.delete(),
                        "locationStatus" to "OFFSITE",
                        "locationUpdatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
        }.addOnCompleteListener { pending.finish() }
    }
}


class DoctorWorkplaceGeofenceBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val uid = DoctorWorkplaceGeofencing.savedDoctorUid(context)
        if (uid.isNotBlank()) {
            val pending = goAsync()
            DoctorWorkplaceGeofencing.sync(context, uid) { pending.finish() }
        }
    }
}
