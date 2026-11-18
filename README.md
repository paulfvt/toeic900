# TOEIC 900

Application Android personnelle de préparation au TOEIC : 100 % hors ligne, gratuite, au contenu
entièrement original. Objectif : viser un score de 900.

**État : Phase 3 terminée (18/11/2026).** L'app couvre tout le Reading du TOEIC :

- **Part 5** (phrases à compléter) : 300 questions sur 16 compétences de grammaire et de vocabulaire ;
- **Part 6** (textes à trous) : 25 textes de 4 questions, dont une insertion de phrase par texte ;
- **Part 7** (lecture de documents) : 50 jeux, 34 documents seuls et 16 jeux de 2 ou 3 documents (186 questions) ;
- **24 fiches de cours** (règles, exemples, pièges), ouvrables depuis chaque correction ;
- **un chemin de leçons** : 7 unités, 31 étapes débloquées l'une après l'autre (70 % de bonnes réponses pour valider) ;
- choix de la part, de la compétence et de la durée (5, 15, 30 minutes ou libre) ;
- correction immédiate : bonne réponse, règle, pourquoi chaque autre réponse est fausse, piège à retenir ;
  en Part 7, les passages qui justifient la réponse sont surlignés dans le document ;
- révision espacée : chaque compétence avance dans 5 boîtes (revue 1, 2, 4, 8 puis 16 jours plus tard) ;
- explications en français ou en anglais (Réglages).

Depuis la Phase 3, elle suit aussi la progression et entretient la motivation :

- **un demi-test Reading** chronométré (50 questions en 38 min, distinctes de l'entraînement), avec un
  indicateur de rythme, puis les résultats par part et par compétence et la revue de chaque erreur ;
- **un score Reading estimé**, toujours avec sa fourchette : demi-tests 80 %, entraînement 20 %
  (méthode et limites : `docs/score-estime.md`) ;
- **un onglet Progrès** : niveau et XP, série, activité sur 28 jours, réussite par part et par compétence,
  historique des demi-tests ;
- **XP et niveaux, objectif quotidien** (en questions ou en minutes), **série hebdomadaire** (semaine
  validée quand l'objectif est atteint au moins 5 jours sur 7, seuil réglable) et **25 badges** ;
- **un rappel quotidien** à l'heure choisie, seulement si l'objectif du jour n'est pas atteint ;
- **sons, vibrations et animations** à chaque correction (désactivables dans les Réglages).

Les phases suivantes (audio des Parts 1 à 4, diagnostic, tests blancs complets) sont décrites dans `CLAUDE.md`.

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

> Testé le 12/11/2026 sur un Galaxy S25+ (SM-S936B, Android 17) : installation par câble, lancement et tests de base de données réussis.

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

Le contenu est fait de fichiers JSON :

| Dossier | Contenu |
|---|---|
| `content/bank/part5/` | questions Part 5, par lots de 30 (`lot-01.json` à `lot-10.json`) |
| `content/bank/part6/` | textes Part 6, chacun avec ses 4 questions |
| `content/bank/part7/` | jeux Part 7 : un à trois documents, puis leurs questions et les citations qui les justifient (`evidence`) |
| `content/lessons/fiches/` | une fiche de cours par compétence (`<compétence>.json`) |
| `content/lessons/path.json` | le chemin de leçons : unités, étapes, nombre d'exercices par étape |
| `content/bank/mock/` | questions des tests blancs (pool `mock`, identifiants 9001 et suivants), jamais servies à l'entraînement |
| `content/mock/` | la définition de chaque test blanc : ses unités dans l'ordre et sa durée (`reading-1.json`) |
| `content/drafts/` | scripts qui ont produit les plans de rédaction imposés (compétence, difficulté, lettre de la bonne réponse) |

L'app n'embarque pas ces fichiers directement, mais un fichier assemblé :
`app/src/main/assets/content/content.json`.

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
les questions que par leur identifiant (`p5-0042`, `p6-0003-2`, `p7-0012`). Pour cette raison, ne
renumérote jamais une question, un texte ou une étape du chemin existants (`l-verb-tense`).

Tests du pipeline de contenu :

```powershell
python -m unittest discover -s content/tools/tests
```

Pour écrire de nouvelles questions : `content/GUIDE-REDACTION.md` (règles, format, exemples) et
`content/schemas/` (schéma JSON et liste des compétences).

Les trois sons de l'app (`app/src/main/res/raw/`) sont synthétisés par ffmpeg, sans aucun fichier
téléchargé. Pour les régénérer :

```powershell
python content/tools/sounds.py
```

## 6. Organisation du dépôt

```
toeic900/
├─ app/                          l'application Android
│  ├─ src/main/java/fr/toeic900/
│  │  ├─ domain/                 logique pure, sans Android : Leitner, choix des exercices, score estimé, XP, série, badges, rythme
│  │  ├─ data/                   bases de données (Room), import du contenu, réglages, progression, tests blancs
│  │  ├─ reminder/               rappel quotidien (alarme et notification)
│  │  └─ ui/                     écrans (Jetpack Compose) et thème « Élan »
│  ├─ src/main/assets/content/   content.json, généré par build.py
│  ├─ src/test/                  tests unitaires (sur le PC)
│  ├─ src/androidTest/           tests sur appareil (émulateur ou téléphone)
│  └─ schemas/                   structure des deux bases, exportée par Room
├─ content/                      questions (JSON), schémas, scripts Python
├─ design-system/                maquettes et tokens visuels « Élan »
├─ docs/                         proposition d'architecture, score estimé, bilans des Phases 2 et 3
└─ licenses/                     licences des polices Nunito et Nunito Sans (OFL)
```

Deux bases de données sur le téléphone :

- `content.db` : les questions. Jetable, reconstruite à chaque nouvelle version du contenu.
- `user.db` : ta progression (boîtes, réponses, sessions, demi-tests, badges). Jamais détruite :
  chaque changement de structure passe par une migration testée (version 3 depuis la Phase 3).

## 7. Tests

| Tests | Commande | Où |
|---|---|---|
| Logique (Leitner, choix des exercices, chemin, score estimé, XP, série, badges, rythme, rappel, import, migrations SQL) : 147 tests | `.\gradlew.bat testDebugUnitTest` | PC |
| Contenu (validation des Parts 5-7, fiches, chemin, tests blancs, assemblage) : 105 tests | `python -m unittest discover -s content/tools/tests` | PC |
| Bases de données réelles et migrations de `user.db` (1 → 2 → 3) : 13 tests | voir ci-dessous | émulateur ou téléphone |

Tests sur appareil, émulateur démarré :

```powershell
.\gradlew.bat installDebug installDebugAndroidTest
```

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" shell am instrument -w fr.toeic900.test/androidx.test.runner.AndroidJUnitRunner
```

La sortie doit se terminer par `OK (13 tests)`. La commande habituelle
`.\gradlew.bat connectedDebugAndroidTest` se termine sans erreur sur ce poste mais n'exécute aucun
test (cause non élucidée) : ne pas s'y fier pour l'instant.

## 8. Limites connues (fin de Phase 3)

- Les questions sont originales et relues, mais elles ne sont pas calibrées sur de vrais candidats :
  leur difficulté est estimée, et le score estimé repose sur une table de conversion maison
  (`docs/score-estime.md`). Un test officiel avant l'examen reste le meilleur recalage.
- Un seul demi-test Reading : il ne mesure vraiment qu'au premier passage. Les tests blancs complets
  arrivent en Phase 5 ; le Listening (et donc le score total) en Phase 4.
- Une session ou un demi-test interrompus par la fermeture de l'app ne sont pas repris ; les réponses
  d'entraînement déjà données sont, elles, enregistrées.
- Les textes de l'interface sont en français uniquement et écrits dans le code ; seules les
  explications et les fiches passent en anglais.
- Le rappel quotidien utilise une alarme « inexacte » d'Android : il peut arriver avec quelques minutes
  de retard.
