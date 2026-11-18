package fr.toeic900

import android.app.Application
import fr.toeic900.reminder.Reminder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Point d'entrée de l'application : créé par Android avant le premier écran. */
class Toeic900App : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.importContent()
        // L'alarme du rappel ne survit pas toujours à une mise à jour de l'app : on la reprogramme.
        CoroutineScope(Dispatchers.IO).launch { Reminder.reschedule(this@Toeic900App) }
    }
}
