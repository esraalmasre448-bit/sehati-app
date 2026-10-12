package com.sehati.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.Timestamp

data class Notif(val key: String, val title: String, val body: String, val ts: Long, val urgent: Boolean)

class NotifState(val items: List<Notif>, val loaded: Boolean)

fun loadSeen(ctx: Context, uid: String): Set<String>? =
    ctx.getSharedPreferences("sehati_notif", Context.MODE_PRIVATE).getStringSet("seen_$uid", null)

fun saveSeen(ctx: Context, uid: String, keys: Set<String>) {
    ctx.getSharedPreferences("sehati_notif", Context.MODE_PRIVATE)
        .edit().putStringSet("seen_$uid", keys.toSet()).apply()
}

fun toneSetting(ctx: Context): String =
    ctx.getSharedPreferences("sehati_notif", Context.MODE_PRIVATE).getString("tone", "default") ?: "default"

fun saveTone(ctx: Context, v: String) {
    ctx.getSharedPreferences("sehati_notif", Context.MODE_PRIVATE).edit().putString("tone", v).apply()
}

fun toneUri(ctx: Context): Uri? {
    val v = toneSetting(ctx)
    return when (v) {
        "silent" -> null
        "default" -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        else -> Uri.parse(v)
    }
}

fun toneLabel(ctx: Context): String {
    return try {
        val u = toneUri(ctx) ?: return "بدون صوت"
        val t = RingtoneManager.getRingtone(ctx, u)?.getTitle(ctx)
        if (toneSetting(ctx) == "default") "الافتراضية" else (t ?: "مخصصة")
    } catch (e: Exception) { "الافتراضية" }
}

fun playTone(ctx: Context) {
    try {
        val u = toneUri(ctx) ?: return
        RingtoneManager.getRingtone(ctx, u)?.play()
    } catch (e: Exception) { }
}

@Composable
fun rememberNotifs(uid: String, type: String): NotifState {
    val isDoc = type == "doctor" || type == "clinic"
    val ready = uid.isNotEmpty() && type.isNotEmpty()
    var appts by remember(uid, type) { mutableStateOf(listOf<Map<String, Any?>>()) }
    var loaded by remember(uid, type) { mutableStateOf(false) }
    val queues: SnapshotStateMap<String, Long> = remember(uid, type) { mutableStateMapOf() }
    val today = remember { todayKey() }

    DisposableEffect(uid, type) {
        if (!ready) return@DisposableEffect onDispose { }
        val field = if (isDoc) "doctorId" else "patientId"
        val reg = bDb.collection("appointments").whereEqualTo(field, uid).limit(100)
            .addSnapshotListener { s, e ->
                if (e == null) {
                    appts = s?.documents?.map { it.data.orEmpty() + ("id" to it.id) }.orEmpty()
                    loaded = true
                }
            }
        onDispose { reg.remove() }
    }

    val queueIds = if (isDoc) emptyList() else appts
        .filter { it["date"] == today && it["status"] == "CONFIRMED" }
        .map { "${it["doctorId"]}_$today" }
        .distinct().sorted()

    DisposableEffect(queueIds.joinToString(",")) {
        val regs = queueIds.map { qid ->
            bDb.collection("queues").document(qid).addSnapshotListener { s, _ ->
                queues[qid] = s?.getLong("current") ?: 0L
            }
        }
        onDispose { regs.forEach { it.remove() } }
    }

    if (!ready) return NotifState(emptyList(), false)

    val out = mutableListOf<Notif>()
    for (a in appts) {
        val id = a["id"].toString()
        val ts = (a["createdAt"] as? Timestamp)?.toDate()?.time ?: 0L
        val status = a["status"]
        val date = a["date"]?.toString().orEmpty()
        val time = a["time"]?.toString().orEmpty()
        val number = (a["number"] as? Number)?.toLong() ?: 0L
        if (isDoc) {
            val who = a["patientName"]?.toString().orEmpty()
            if (status == "CONFIRMED") {
                out.add(Notif("$id:C", "حجز جديد", "رقم $number - $who - $date الساعة $time", ts, false))
            } else if (status == "CANCELLED") {
                out.add(Notif("$id:X", "حجز ملغى", "$who - $date الساعة $time", ts, false))
            }
        } else {
            val doc = a["doctorName"]?.toString().orEmpty()
            when (status) {
                "CONFIRMED" -> {
                    out.add(Notif("$id:C", "تم تأكيد حجزك", "$doc - $date الساعة $time - رقمك $number", ts, false))
                    val cur = queues["${a["doctorId"]}_$today"]
                    if (date == today && cur != null) {
                        val ahead = number - cur - 1
                        if (cur == number) {
                            out.add(Notif("$id:now", "حان دورك", "تفضّل بالدخول إلى $doc", System.currentTimeMillis(), true))
                        } else if (cur < number && ahead in 0L..2L) {
                            val txt = if (ahead == 0L) "أنت التالي عند $doc" else "بقي $ahead قبلك عند $doc"
                            out.add(Notif("$id:near$ahead", "دورك قريب", txt, System.currentTimeMillis(), true))
                        }
                    }
                }
                "CANCELLED" -> out.add(Notif("$id:X", "موعد ملغى", "موعدك عند $doc بتاريخ $date أُلغي", ts, false))
                "DONE" -> out.add(Notif("$id:D", "اكتملت زيارتك", "زيارتك عند $doc بتاريخ $date", ts, false))
            }
        }
    }
    val ctx = LocalContext.current
    var known by remember(uid, type) { mutableStateOf<Set<String>?>(null) }
    val keysNow = out.map { it.key }.toSet()
    LaunchedEffect(keysNow, loaded) {
        if (!loaded) return@LaunchedEffect
        val k = known
        if (k == null) {
            known = keysNow
        } else {
            val added = (keysNow - k).filter { isDoc || !it.endsWith(":C") }
            known = keysNow
            if (added.isNotEmpty()) playTone(ctx)
        }
    }
    return NotifState(out.sortedByDescending { it.ts }, loaded)
}

@Composable
fun NotifTab(items: List<Notif>, fresh: Set<String>) {
    val ctx = LocalContext.current
    var label by remember { mutableStateOf(toneLabel(ctx)) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) {
            @Suppress("DEPRECATION")
            val u: Uri? = r.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            saveTone(ctx, if (u == null) "silent" else u.toString())
            label = toneLabel(ctx)
        }
    }
    LazyColumn(
        Modifier.fillMaxSize().background(BBg).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        item { Text("الإشعارات", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White) }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("نغمة الإشعار: $label", color = BMuted)
                OutlinedButton(
                    onClick = {
                        val i = Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                        i.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)
                        i.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
                        i.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                        i.putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "نغمة الإشعار")
                        val cur = toneUri(ctx)
                        if (cur != null) i.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, cur)
                        picker.launch(i)
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) { Text("تغيير النغمة", color = Color.White) }
            }
        }
        if (items.isEmpty()) {
            item { Text("لا توجد إشعارات حاليًا.", color = BMuted) }
        }
        items(items, key = { it.key }) { n ->
            val isNew = n.key in fresh
            val edge = if (n.urgent) BGreen else if (isNew) BRed else BLine
            Column(
                Modifier.fillMaxWidth().background(BCard, RoundedCornerShape(14.dp))
                    .border(1.dp, edge, RoundedCornerShape(14.dp)).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    n.title, fontWeight = FontWeight.Bold, fontSize = 16.sp,
                    color = if (n.urgent) BGreen else Color.White
                )
                Text(n.body, color = BMuted)
            }
        }
    }
}
