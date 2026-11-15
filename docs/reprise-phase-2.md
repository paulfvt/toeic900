# Reprise de la Phase 2

Rédigé le 15/11/2026 par la session qui a commencé la Phase 2, pour la session qui la termine.
À lire en entier avant de toucher au code, avec `CLAUDE.md`. À supprimer une fois la phase bouclée.

## 1. Où on en est

Branche `main`, rien de poussé (pas de dépôt distant).

| Commit | Contenu | État vérifié |
|---|---|---|
| `638243f` | Pipeline : 8 tags de lecture, schémas, `validate.py`, `build.py` format 2 | tests Python verts |
| `7fd4296` | 24 fiches, 8 textes Part 6, 12 jeux Part 7, `content/lessons/path.json` | `validate.py` : 0 erreur ; **pas relu** |
| `76c9bbf` | App : domaine, bases, tous les écrans | 96 tests Kotlin et 97 tests Python verts ; `compileDebugKotlin` passe ; **jamais lancée** |
| commit « en cours » qui suit | Test de migration, tests sur appareil réécrits, réglage du sélecteur, dépendance `room-testing` | **ni compilé ni testé** |

Le dernier commit contient du code que personne n'a encore compilé : c'est le premier point à traiter.

## 2. Ce qui reste, dans l'ordre

### A. Faire passer le commit « en cours » (PC)

```
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat testDebugUnitTest
```

Attendu : 103 tests (96 + 6 dans `UserMigrationSqlTest` + 1 dans `UnitPickerTest`). Points à surveiller :

- `QuestionPicker.kt` : j'ai ajouté une préférence pour les compétences dues qui ont encore une unité jamais vue (`freshTags`). Un test existant de `QuestionPickerTest` peut en dépendre : s'il casse, lire le test avant de toucher à la règle.
- `UserMigrationSqlTest` lit `app/schemas/…UserDatabase/1.json` et `2.json` et vérifie que `MIGRATION_1_2_SQL` mène exactement de l'un à l'autre.

### B. Tests sur appareil (émulateur)

```
.\gradlew.bat installDebug installDebugAndroidTest
adb shell am instrument -w fr.toeic900.test/androidx.test.runner.AndroidJUnitRunner
```

Attendu : `OK (11 tests)` (8 dans `DatabasesInstrumentedTest`, 3 dans `UserDatabaseMigrationTest`).

- `MigrationTestHelper` a besoin des schémas exportés dans les ressources du test. Si le test échoue sur un fichier `…UserDatabase/1.json` introuvable, ajouter dans `app/build.gradle.kts`, bloc `android` : `sourceSets { getByName("androidTest").assets.srcDir("$projectDir/schemas") }`. Je n'ai pas vérifié si le plugin Gradle de Room le fait seul.
- Le constructeur utilisé est `MigrationTestHelper(instrumentation, UserDatabase::class.java)` : à vérifier contre Room 2.8.5 si la compilation le refuse.
- Avant toute commande `adb install` ou `uninstall` : `adb devices` ne doit lister que `emulator-5554`. Ne jamais désinstaller sur le vrai téléphone.

### C. Parcours à l'écran (émulateur, 411 dp puis 384 dp)

La largeur du S25+ se simule par `adb shell wm density 450` (retour : `adb shell wm density reset`).
Parcours exigés par le brief de phase : une leçon du chemin jouée, une fiche ouverte depuis une correction, un texte Part 6 et un jeu Part 7 joués de bout en bout.

Ce que je crois fragile, sans l'avoir vu :

1. Feuille de correction : la ligne « Voir la fiche » + « Question suivante » risque de ne pas tenir à 384 dp.
2. Onglet Exercices : quatre pastilles « Toutes / Part 5 / Part 6 / Part 7 » sur une ligne, juste à 384 dp.
3. Vue de lecture Parts 6-7 (`StimulusArea` dans `TrainingScreen.kt`) : panneau de question limité à 62 % de la hauteur, replié par défaut en Part 7 (« Répondre » pour le déplier). Vérifier que c'est compréhensible et que les options longues défilent.
4. Barre du bas : placée sous `safeDrawing` ; vérifier la zone des gestes.
5. Navigation après une leçon : « Étape suivante » fait `backToPath()` puis ouvre l'étape ; à tester depuis l'accueil ET depuis l'onglet Chemin.
6. Chronomètre : il doit s'arrêter quand on ouvre une fiche depuis une correction (`LifecycleResumeEffect`).
7. Mode sombre : aucun nouvel écran n'a été regardé.

### D. Mise à jour d'une vraie base de la Phase 1 (émulateur uniquement)

1. `adb uninstall fr.toeic900`, puis installer l'APK de la Phase 1 (section 6).
2. Jouer quelques réponses, noter les chiffres de l'accueil et une boîte dans le choix de session.
3. `adb install -r app\build\outputs\apk\debug\app-debug.apk` (ne pas désinstaller entre les deux).
4. Vérifier : l'app s'ouvre, les chiffres et les boîtes sont là, le chemin démarre à la première étape, une nouvelle session s'enregistre.

### E. Relecture critique du contenu (rien n'a été relu)

Critères : une seule bonne réponse défendable, distracteurs plausibles, tag juste, réponse justifiée par le texte (Parts 6-7), français naturel. Priorités :

- **`p7-0010` à `p7-0012`** (documents multiples) : produits par un script dont l'auteur a été coupé avant sa propre relecture. Refaire les calculs et vérifier dates et noms d'un document à l'autre.
- **`p6-0001` à `p6-0004`** : même cas, aucun compte rendu de relecture.
- **Numéros de ligne** : plusieurs questions de synonyme disent « in paragraph 2, line 1 ». Sur un téléphone, les lignes ne correspondent à rien : réécrire ces énoncés sans numéro de ligne (le validateur ne le contrôle pas ; le guide et son exemple seraient à ajuster aussi).
- Doutes signalés par les rédacteurs : `p7-0001-1` (« pull the trigger », expression idiomatique), `p7-0004-3`, `p7-0006-2`, `p7-0007-3` (une seconde raison possible), `p6-0007-4`, `p6-0008-4` (« replaced » tentant).
- Fiches : `inference.json` contient en français « Tout le reste est à toi, pas du texte » (à reformuler) ; `quantifiers-determiners`, `prepositions`, `conjunctions-connectors`, `vocabulary-in-context` et `collocations` n'ont pas eu de relecture de justesse ; `cross-reference` et `not-true` ont des exemples artificiels ; `sentence-insertion` a des exemples avec `[1]`…`[4]` (vérifier l'affichage).

Après corrections : `python content/tools/build.py`, tests Python et Kotlin, un commit par lot relu.

### F. Part 5 : de 120 à 300

- Deux lots sont écrits et valides mais **non relus et hors banque** : `content/drafts/part5/lot-05.json` (p5-0121 à 0150) et `lot-06.json` (p5-0151 à 0180). Doutes des rédacteurs : `p5-0141`, `0147`, `0148`, `0150`, `0167`, `0173` (« some six months » est de l'anglais correct : probablement à refaire), `0179`.
- Après relecture, les déplacer dans `content/bank/part5/`.
- Restent les lots 07 à 10 (p5-0181 à 0300). Les plans imposés sont dans `content/drafts/part5/plan-lot-NN.md` (tag, difficulté, lettre). Leur liste « options déjà utilisées » ne connaît que les 120 premières questions : relancer `p5_plan.py` après avoir intégré les lots 05 et 06, ou donner ces deux lots à lire aux rédacteurs.
- Volumes visés ensuite, si le quota le permet : 25 textes Part 6 (8 faits), 50 jeux Part 7 (12 faits).

### G. Documentation et bilan

- `README.md` : état, contenu des Parts 6-7, fiches et chemin dans la section « contenu », tableau des tests, limites.
- `CLAUDE.md` : tableau « Phases », section d'état du code réécrite pour la Phase 3, décisions de la section 3 ci-dessous.
- Bilan de fin de phase : fait, reste, limites, et ce qui n'a pas été vérifié.
- `adb emu kill` à la fin.

## 3. Décisions prises (documentées dans le code, à ne pas rouvrir sans raison)

- **Unité servie** : une question en Part 5, un texte entier en Parts 6-7 (`UnitPicker`, `UnitRef`). Leitner reste par compétence : chaque question d'un texte met à jour la boîte de son propre tag.
- **Retour unique** : un texte dont une question a été ratée revient une fois, en entier, 7 jours plus tard ; ce jour-là toutes ses questions sont marquées « retour fait » (`QuestionReview.afterAnswer`, paramètre `controlReturn`).
- **Chemin** : étapes débloquées l'une après l'autre, validées à 70 % de bonnes réponses sur un passage mené au bout ; une étape réussie le reste (`LessonPath`). Les identifiants d'étape (`l-verb-tense`) sont stables comme ceux des questions.
- **Preuves** : chaque question Part 7 porte `evidence`, des citations mot pour mot du document ; l'app les surligne à la correction, le validateur les contrôle.
- **Session rapide de l'accueil** : toutes parts mélangées (`part = 0`).
- **`user.db`** : version 2, migration `MIGRATION_1_2` (une table, trois colonnes, rien de supprimé). `content.db` : version 2, jetable.

## 4. Pièges rencontrés

- Dans l'outil Bash de cette machine, un heredoc interprète `$` et `\\` même avec un délimiteur entre apostrophes : il casse le Kotlin et le Python. Écrire les fichiers avec les outils d'édition.
- L'auteur a refusé une modification de fichier faite par script : modifier les fichiers du dépôt par éditions directes.
- Quota : huit sous-agents en parallèle ont vidé une fenêtre de 5 heures en une demi-heure. Deux à la fois au plus ; un agent coupé se reprend par message et garde son travail.
- Donner à chaque sous-agent un dossier de travail à lui, hors dépôt, et un seul fichier de sortie ; lui faire lancer `validate.py` lui-même.
- `gradlew connectedDebugAndroidTest` rapporte toujours 0 test sur ce poste : passer par `adb shell am instrument`.

## 5. Pour consommer moins

- Repartir d'une session neuve qui lit ce fichier, plutôt que de continuer une longue conversation.
- Ne pas relire les gros JSON en entier : pour relire des questions, un petit script qui affiche énoncé, options et bonne réponse suffit ; n'ouvrir les explications que pour les questions douteuses.
- Lire l'interface par `adb shell uiautomator dump` plutôt que par captures d'écran, sauf pour juger d'une mise en page.
- Fichiers à connaître, les autres peuvent attendre : `domain/QuestionPicker.kt`, `domain/LessonPath.kt`, `data/TrainingRepository.kt`, `ui/AppNav.kt`, `ui/training/TrainingScreen.kt`, `content/tools/validate.py`, `content/GUIDE-REDACTION.md`.

## 6. Hors dépôt

- APK de la Phase 1 (pour l'étape D) : `C:\Users\utilisateur\AppData\Local\Claude-Data\claude_pzs8sxrjxfjjc\side-session-notes\local_9b34e2f1-c166-484c-a77b-896ffabffada\app-phase1.apk`. S'il a disparu : `git worktree add ..\toeic900-phase1 427fd89`, y copier `local.properties`, `gradlew assembleDebug`, puis `git worktree remove`.
- L'émulateur s'appelle `Medium_Phone_API_37.0` ; `adb` est dans `%LOCALAPPDATA%\Android\Sdk\platform-tools`.
