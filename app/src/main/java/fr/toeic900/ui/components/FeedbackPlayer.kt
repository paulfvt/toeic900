package fr.toeic900.ui.components

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.toeic900.R
import fr.toeic900.data.settings.ProgressSettings
import fr.toeic900.ui.appContainer

enum class FeedbackSound { CORRECT, WRONG, BADGE }

/**
 * Sons et vibrations des réponses. Les sons (res/raw, synthétisés par content/tools/sounds.py)
 * suivent le volume « multimédia » du téléphone ; les deux se coupent dans les réglages.
 */
class FeedbackPlayer(context: Context) {
    private val pool = SoundPool.Builder()
        .setMaxStreams(2)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()
    private val ids = mapOf(
        FeedbackSound.CORRECT to pool.load(context, R.raw.answer_correct, 1),
        FeedbackSound.WRONG to pool.load(context, R.raw.answer_wrong, 1),
        FeedbackSound.BADGE to pool.load(context, R.raw.badge_unlocked, 1),
    )

    fun play(sound: FeedbackSound, settings: ProgressSettings, view: View) {
        if (settings.sounds) ids[sound]?.let { pool.play(it, 1f, 1f, 1, 0, 1f) }
        if (settings.haptics) {
            val constant = when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && sound == FeedbackSound.WRONG -> HapticFeedbackConstants.REJECT
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> HapticFeedbackConstants.CONFIRM
                sound == FeedbackSound.WRONG -> HapticFeedbackConstants.LONG_PRESS
                else -> HapticFeedbackConstants.VIRTUAL_KEY
            }
            view.performHapticFeedback(constant)
        }
    }

    fun release() = pool.release()
}

/** Un lecteur lié à l'écran : libéré quand l'écran disparaît. Renvoie une fonction « joue ce son ». */
@Composable
fun rememberFeedback(): (FeedbackSound) -> Unit {
    val context = LocalContext.current
    val view = LocalView.current
    val settings by appContainer().settings.progress.collectAsStateWithLifecycle(ProgressSettings())
    val player = remember { FeedbackPlayer(context.applicationContext) }
    DisposableEffect(player) { onDispose { player.release() } }
    return { sound -> player.play(sound, settings, view) }
}
