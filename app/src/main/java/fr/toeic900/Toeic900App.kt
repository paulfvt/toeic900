package fr.toeic900

import android.app.Application

/** Point d'entrée de l'application : créé par Android avant le premier écran. */
class Toeic900App : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.importContent()
    }
}
