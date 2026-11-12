# TOEIC 900

Application Android personnelle de préparation au TOEIC : 100 % hors ligne, gratuite, au contenu
entièrement original. Objectif : viser un score de 900.

**État : Phase 1 terminée.** L'app permet de s'entraîner sur la Part 5 (phrases à compléter) :

- 120 questions originales, réparties sur 16 compétences (forme du mot, temps, prépositions…) ;
- choix de la compétence et de la durée (5, 15, 30 minutes ou libre) ;
- correction immédiate : bonne réponse, règle, pourquoi chaque autre réponse est fausse, piège à retenir ;
- révision espacée : chaque compétence avance dans 5 boîtes (revue 1, 2, 4, 8 puis 16 jours plus tard) ;
- explications en français ou en anglais (Réglages).

Les phases suivantes (Parts 6 et 7, statistiques, audio, tests blancs) sont décrites dans `CLAUDE.md`.

---

## 1. Ce qu'il faut sur le PC

Tout est déjà installé sur le poste de développement :

| Outil | À quoi il sert |
|---|---|
| **Android Studio** | Ouvrir le projet, lancer l'app, et fournir Java (le « JDK ») et le SDK Android |
| **Python 3** | Valider et assembler les questions (dossier `content/`) |
| **Git** | Historique du projet |

Il n'y a rien d'autre à installer. Au premier build, Gradle (l'outil qui fabrique l'app) télécharge
tout seul les bibliothèques nécessaires : il faut donc Internet **pour builder**, jamais pour utiliser l'app.

## 2. Lancer l'app sur l'émulateur (le plus simple)

1. Ouvre **Android Studio**, puis *File › Open* et choisis le dossier `C:\dev\toeic900`.
2. Attends la fin de la synchronisation (barre de progression en bas, « Gradle sync »). La première
   fois, cela prend quelques minutes.
3. En haut de la fenêtre, vérifie que la liste des appareils affiche **Medium Phone API 37** et que
   la configuration à côté affiche **app**.
4. Clique sur le triangle vert ▶ (*Run*). L'émulateur démarre (un téléphone virtuel à l'écran),
   l'app s'installe et s'ouvre.

Pour relancer après une modification du code : ▶ de nouveau.

## 3. Installer l'app sur le Samsung Galaxy S25+

À faire une seule fois sur le téléphone :

1. **Activer les options de développement** : *Paramètres › À propos du téléphone › Informations
   sur le logiciel*, puis touche **7 fois** la ligne *Numéro de version*. Le téléphone demande ton
   code, puis affiche « Le mode développeur a été activé ».
2. **Activer le débogage USB** : *Paramètres › Options de développement* (tout en bas des
   paramètres), active *Débogage USB*.
3. Branche le téléphone au PC avec un câble USB **de données** (certains câbles ne font que charger).
4. Sur le téléphone, une fenêtre « Autoriser le débogage USB ? » apparaît : coche *Toujours
   autoriser depuis cet ordinateur*, puis *Autoriser*.

Ensuite, dans Android Studio, le téléphone apparaît dans la liste des appareils (« samsung SM-S936… ») :
sélectionne-le et clique sur ▶. L'app reste installée quand tu débranches le câble.

Si l'installation est refusée :

- vérifie que l'écran du téléphone est déverrouillé et que tu as bien accepté la fenêtre d'autorisation ;
- sur les Samsung récents, le **Bloqueur automatique** peut bloquer les installations par câble :
  *Paramètres › Sécurité et confidentialité › Bloqueur automatique*, désactive-le le temps de l'installation ;
- change de câble ou de port USB si le téléphone n'apparaît pas du tout.

**Mettre à jour sans perdre ta progression** : réinstalle toujours par-dessus (▶ ou `adb install -r`).
Ne désinstalle pas l'app : cela effacerait ta progression, qui n'existe que sur le téléphone.

> L'installation sur un vrai Galaxy S25+ n'a pas encore été testée : seul l'émulateur l'a été.

## 4. Builder en ligne de commande (facultatif)

Dans un terminal **PowerShell** ouvert dans `C:\dev\toeic900`. Le Java du système est trop ancien :
on indique d'abord à Gradle d'utiliser celui d'Android Studio (à refaire dans chaque nouveau terminal).

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
```

Fabriquer l'app (le fichier produit est `app\build\outputs\apk\debug\app-debug.apk`) :

```powershell
.\gradlew.bat assembleDebug
```

Lancer les tests unitaires (sur le PC, sans émulateur) :

```powershell
.\gradlew.bat testDebugUnitTest
```

Installer l'APK sur l'émulateur démarré ou le téléphone branché (`adb` est l'outil qui parle à l'appareil) :

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" install -r app\build\outputs\apk\debug\app-debug.apk
```

Si Gradle répond « SDK location not found » : crée le fichier `local.properties` à la racine avec la
ligne suivante (barres obliques `/`, pas `\`). Android Studio le crée tout seul quand il ouvre le projet.

```
sdk.dir=C:/Users/utilisateur/AppData/Local/Android/Sdk
```

## 5. Modifier ou régénérer le contenu

Les questions sont des fichiers JSON dans `content/bank/part5/`. L'app n'embarque pas ces fichiers
directement, mais un fichier assemblé : `app/src/main/assets/content/content.json`.

Après toute modification des questions, depuis la racine du dépôt :

```powershell
python content/tools/validate.py
```

Ce script vérifie le format, l'unicité de la bonne réponse, les tags, les doublons et les
répartitions (lettres, difficulté, nombre de questions par compétence). Il doit finir par `0 erreur(s)`.

```powershell
python content/tools/build.py
```

Ce script revalide, puis réécrit `content.json` avec une nouvelle « version » calculée à partir du
contenu. Rebuilde ensuite l'app (▶). Au démarrage, l'app voit que la version a changé et reconstruit sa
base de contenu. **Ta progression n'est pas touchée** : elle vit dans une autre base, qui ne connaît
les questions que par leur identifiant (`p5-0042`). Pour cette raison, ne renumérote jamais une
question existante.

Tests du pipeline de contenu :

```powershell
python -m unittest discover -s content/tools/tests
```

Pour écrire de nouvelles questions : `content/GUIDE-REDACTION.md` (règles, format, exemples) et
`content/schemas/` (schéma JSON et liste des compétences).

## 6. Organisation du dépôt

```
toeic900/
├─ app/                          l'application Android
│  ├─ src/main/java/fr/toeic900/
│  │  ├─ domain/                 logique pure, sans Android : boîtes de Leitner, choix de la question
│  │  ├─ data/                   bases de données (Room), import du contenu, réglages
│  │  └─ ui/                     écrans (Jetpack Compose) et thème « Élan »
│  ├─ src/main/assets/content/   content.json, généré par build.py
│  ├─ src/test/                  tests unitaires (sur le PC)
│  ├─ src/androidTest/           tests sur appareil (émulateur ou téléphone)
│  └─ schemas/                   structure des deux bases, exportée par Room
├─ content/                      questions (JSON), schémas, scripts Python
├─ design-system/                maquettes et tokens visuels « Élan »
├─ docs/                         brief et proposition d'architecture
└─ licenses/                     licences des polices Nunito et Nunito Sans (OFL)
```

Deux bases de données sur le téléphone :

- `content.db` : les questions. Jetable, reconstruite à chaque nouvelle version du contenu.
- `user.db` : ta progression (boîtes, réponses, sessions). Jamais détruite.

## 7. Tests

| Tests | Commande | Où |
|---|---|---|
| Logique (Leitner, choix des questions, import) : 43 tests | `.\gradlew.bat testDebugUnitTest` | PC |
| Contenu (validation, assemblage) : 36 tests | `python -m unittest discover -s content/tools/tests` | PC |
| Bases de données réelles : 4 tests | voir ci-dessous | émulateur ou téléphone |

Tests sur appareil, émulateur démarré :

```powershell
.\gradlew.bat installDebug installDebugAndroidTest
```

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" shell am instrument -w fr.toeic900.test/androidx.test.runner.AndroidJUnitRunner
```

La sortie doit se terminer par `OK (4 tests)`. La commande habituelle
`.\gradlew.bat connectedDebugAndroidTest` se termine sans erreur sur ce poste mais n'exécute aucun
test (cause non élucidée) : ne pas s'y fier pour l'instant.

## 8. Limites connues (Phase 1)

- Les questions sont originales et relues, mais elles ne sont pas calibrées sur de vrais candidats :
  leur difficulté est estimée.
- Une session interrompue par la fermeture de l'app n'est pas reprise ; les réponses déjà données
  sont, elles, enregistrées.
- Les textes de l'interface sont en français uniquement et écrits dans le code.
- Pas encore de statistiques, de score estimé, de fiches de cours ni de notifications (Phases 2 et 3).
