package fr.toeic900.reminder

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import fr.toeic900.MainActivity
import fr.toeic900.R
import fr.toeic900.Toeic900App
import fr.toeic900.domain.GoalType
import fr.toeic900.domain.ReminderTime
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Rappel quotidien à l'heure choisie. Une alarme « inexacte » d'Android (pas de permission
 * spéciale, quelques minutes de décalage possibles pour économiser la batterie) réveille
 * [ReminderReceiver], qui affiche la notification si l'objectif du jour n'est pas encore atteint,
 * puis programme l'alarme du lendemain.
 */
object Reminder {
    private const val CHANNEL_ID = "daily-reminder"
    private const val NOTIFICATION_ID = 1
    private const val REQUEST_CODE = 42

    private fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        Intent(context, ReminderReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** Programme (ou annule) l'alarme selon les réglages. À rappeler à chaque changement de réglage. */
    fun schedule(context: Context, enabled: Boolean, minuteOfDay: Int) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val intent = pendingIntent(context)
        alarms.cancel(intent)
        if (!enabled) return
        val at = ReminderTime.next(System.currentTimeMillis(), minuteOfDay, ZoneId.systemDefault())
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
    }

    /** Relit les réglages et reprogramme : au démarrage de l'app et du téléphone. */
    suspend fun reschedule(context: Context) {
        val settings = (context.applicationContext as Toeic900App).container.settings.progress.first()
        schedule(context, settings.reminderEnabled, settings.reminderMinuteOfDay)
    }

    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun show(context: Context, text: String) {
        if (!canNotify(context)) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Rappel quotidien", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Un rappel par jour, à l'heure choisie, tant que l'objectif du jour n'est pas atteint."
            },
        )
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("TOEIC 900")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // Autorisation retirée entre la vérification et l'envoi : rien à faire.
        }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val app = context.applicationContext as Toeic900App
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val o = app.container.progress.today()
                if (!o.goalMetToday) {
                    val left = o.goal.target - o.todayProgress
                    val unit = if (o.goal.type == GoalType.MINUTES) "min" else if (left > 1) "questions" else "question"
                    val streak = if (o.streak.weeks > 0) " Ta série : ${o.streak.weeks} semaine${if (o.streak.weeks > 1) "s" else ""}." else ""
                    Reminder.show(context, "Encore $left $unit pour l'objectif du jour.$streak")
                }
                Reminder.reschedule(context)
            } finally {
                pending.finish()
            }
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Reminder.reschedule(context)
            } finally {
                pending.finish()
            }
        }
    }
}
