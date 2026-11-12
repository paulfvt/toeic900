package fr.toeic900

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import fr.toeic900.ui.Toeic900Root
import fr.toeic900.ui.theme.ElanTheme

/** L'unique « activité » de l'app : elle affiche l'interface Compose, qui gère tous les écrans. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ElanTheme {
                Toeic900Root()
            }
        }
    }
}
