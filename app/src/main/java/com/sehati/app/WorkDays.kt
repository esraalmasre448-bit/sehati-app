package com.sehati.app

import java.util.Calendar

/** أيام الأسبوع بترتيب العرض (السبت أولاً). الرقم هو Calendar.DAY_OF_WEEK: 1=الأحد ... 7=السبت. */
val WEEK_DAYS: List<Pair<Int, String>> = listOf(
    Calendar.SATURDAY to "السبت",
    Calendar.SUNDAY to "الأحد",
    Calendar.MONDAY to "الاثنين",
    Calendar.TUESDAY to "الثلاثاء",
    Calendar.WEDNESDAY to "الأربعاء",
    Calendar.THURSDAY to "الخميس",
    Calendar.FRIDAY to "الجمعة"
)

/** أيام دوام الطبيب. إذا لم يحدد أيامًا بعد، تُعتبر كل الأيام أيام دوام. */
fun workDaysOf(d: Map<String, Any?>?): Set<Int> {
    val l = d?.get("workDays") as? List<*> ?: return (1..7).toSet()
    val s = l.mapNotNull { (it as? Number)?.toInt() }.filter { it in 1..7 }.toSet()
    return if (s.isEmpty()) (1..7).toSet() else s
}

/** هل حدد الطبيب أيام دوام فعلًا (ليظهر النص للمريض)؟ */
fun hasWorkDays(d: Map<String, Any?>?): Boolean = workDaysOf(d).size < 7

/** نص مثل: السبت، الأحد، الاثنين */
fun workDaysLabel(days: Set<Int>): String =
    WEEK_DAYS.filter { it.first in days }.joinToString("، ") { it.second }

/** أقرب [count] يوم دوام ابتداءً من اليوم، ضمن 30 يومًا قادمة. */
fun workDayCalendars(days: Set<Int>, count: Int = 7): List<Calendar> {
    val out = mutableListOf<Calendar>()
    for (i in 0..30) {
        val c = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, i) }
        if (c.get(Calendar.DAY_OF_WEEK) in days) out.add(c)
        if (out.size >= count) break
    }
    if (out.isEmpty()) out.add(Calendar.getInstance())
    return out
}
