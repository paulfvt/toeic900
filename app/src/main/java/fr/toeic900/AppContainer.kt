package fr.toeic900

import android.content.Context
import fr.toeic900.data.ProgressRepository
import fr.toeic900.data.TrainingRepository
import fr.toeic900.data.content.ContentDatabase
import fr.toeic900.data.content.ContentImporter
import fr.toeic900.data.content.RoomContentStore
import fr.toeic900.data.settings.SettingsRepository
import fr.toeic900.data.user.UserDatabase
import fr.toeic900.domain.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface ContentStatus {
    data object Loading : ContentStatus
    data object Ready : ContentStatus
    data class Failed(val message: String) : ContentStatus
}

/**
 * « Injection manuelle » : cet objet crée une seule fois les bases et les dépôts de données,
 * et les écrans viennent les chercher ici. Pas de framework d'injection : tout est visible
 * dans ce fichier.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val contentDb by lazy { ContentDatabase.build(appContext) }
    private val userDb by lazy { UserDatabase.build(appContext) }

    val settings by lazy { SettingsRepository(appContext) }
    val repository by lazy { TrainingRepository(contentDb.contentDao(), userDb.userDao(), SystemClock) }
    val progress by lazy { ProgressRepository(contentDb.contentDao(), userDb.userDao(), settings, SystemClock) }

    private val _contentStatus = MutableStateFlow<ContentStatus>(ContentStatus.Loading)
    val contentStatus: StateFlow<ContentStatus> = _contentStatus

    /** Au démarrage : importe le contenu embarqué si sa version a changé (sinon, ne fait rien). */
    fun importContent() {
        scope.launch {
            _contentStatus.value = try {
                ContentImporter(
                    readBundleText = { appContext.assets.open(CONTENT_ASSET).bufferedReader().use { it.readText() } },
                    store = RoomContentStore(contentDb.contentDao()),
                ).importIfNeeded()
                ContentStatus.Ready
            } catch (e: Exception) {
                ContentStatus.Failed(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    private companion object {
        const val CONTENT_ASSET = "content/content.json"
    }
}
