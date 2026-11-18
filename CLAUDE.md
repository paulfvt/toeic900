# TOEIC 900 — contexte du projet

Application Android personnelle de préparation au TOEIC pour son auteur, **débutant en développement mobile** : chaque étape d'installation, de build et de lancement s'explique comme à un débutant. Objectif : 900. Examen prévu début 2027 (« dans 2 à 3 mois », dit le 12/11/2026 ; date exacte non fixée). Point faible : le Reading.

- Brief complet de l'auteur, **il fait foi** : `docs/brief.md` (non versionné)
- Contexte personnel (profil, score, matériel, téléphone) : `CLAUDE.local.md`, non versionné, lu automatiquement par Claude Code. **Rien de personnel dans les fichiers suivis par git** : le dépôt GitHub est public. Avant chaque push, `git grep` les infos de `CLAUDE.local.md`.
- Proposition d'architecture (stack, modèle de données, phases) : `docs/proposition.md`
- Tokens visuels : `design-system/styles.css`, blocs `[data-dir="elan"]` et `[data-dir="elan"][data-mode="dark"]` ; maquettes : `design-system/concept.html`

Répondre en français.

## Contraintes non négociables

- Android uniquement, usage personnel : pas de Play Store, pas de comptes, pas de serveur.
- 100 % hors ligne à l'usage : aucun appel à une IA ni à une API. Tout le contenu (questions, explications, audios, images) est généré en amont et embarqué.
- 0 € : aucun service payant, aucun abonnement, aucune API facturée. Une offre gratuite de service cloud n'est permise que si le script garantit de ne jamais dépasser le quota gratuit. Si une étape ne peut pas se faire gratuitement, s'arrêter et proposer des alternatives gratuites.
- Contenu 100 % original au format du TOEIC. Jamais de question officielle ETS ni tirée d'un livre publié.
- Questions et textes en anglais ; explications en français par défaut, avec un réglage pour passer en anglais.

## Décisions prises (12/11/2026)

- **Stack** : Kotlin, Jetpack Compose, Material 3, Navigation Compose, Room, DataStore, kotlinx.serialization, JUnit ; Media3 pour l'audio en Phase 4. Un seul module Gradle, injection manuelle. Python pour le pipeline `content/`.
- **Cible** : Samsung Galaxy S25+ ; Android 8 minimum (`minSdk 26`).
- **Emplacement du dépôt** : `C:\dev\toeic900`, hors OneDrive et sans espace dans le chemin. Le dossier `OneDrive\Bureau\App TOEIC` n'a servi qu'aux maquettes et au contexte de départ.
- **Visuel** : direction « Élan » choisie par l'auteur sur maquettes. Traduire les tokens en thème Compose (couleurs, typographie, formes, boutons « à tranche ») sans réinventer de palette. Polices Nunito (interface) et Nunito Sans (textes d'examen) embarquées. Mode sombre vérifié en contraste par calcul et relu à l'écran sur les écrans de la Phase 2 (émulateur).
- **Architecture** : `domain/` en Kotlin pur testable sur JVM (révision espacée, score, série, XP, recommandation), `data/` (Room, import du contenu), `ui/` (Compose et ViewModels).
- **Deux bases reliées par des identifiants stables** (ex. `p5-0142`) : la base de contenu est reconstruite depuis les JSON à chaque version de contenu, la base utilisateur n'est jamais détruite.
- **Révision espacée** : Leitner à 5 boîtes (1, 2, 4, 8, 16 jours) au niveau de la compétence (tag). Une compétence due amène une question encore jamais vue sur ce tag. Une question ratée revient une fois, au moins 7 jours plus tard. Vocabulaire : Leitner par mot.
- **Score estimé** : table de conversion maison et documentée, par section (5 à 495). Tests blancs ≈ 80 % du poids, entraînement ≈ 20 %. Toujours affiché avec une fourchette et présenté comme une estimation.
- **Série hebdomadaire** : objectif quotidien atteint au moins 5 jours sur 7, seuil réglable.
- **Photos de la Part 1** : banques libres (Pexels, Unsplash), la photo d'abord puis la question. Demander l'accord de l'auteur avant tout téléchargement.
- **Volumes visés** : 300 questions Part 5, 25 textes Part 6, 50 documents Part 7, 30 photos, 100 questions Part 2, 30 conversations, 25 exposés, 2 tests blancs de 200 questions, environ 50 questions de diagnostic. Les questions des tests blancs ne viennent jamais de la banque d'entraînement.

## Poste de développement

- Windows 11, PC portable, pas de carte graphique dédiée, environ 55 Go libres.
- Android Studio installé. Utiliser son JDK embarqué : le Java du système est en version 11, trop ancien.
- SDK Android dans `%LOCALAPPDATA%\Android\Sdk` : plateforme `android-37.0`, build-tools 36.0.0, émulateur, appareil virtuel `Medium_Phone` (API 37). `adb` se trouve dans `platform-tools` et n'est pas dans le PATH.
- Pas de Gradle global : passer par le wrapper du projet. Disponibles : git 2.47, Python 3.13, Node 24, ffmpeg.
- Synthèse vocale et génération d'images : processeur seul. Kokoro ou Piper pour les voix (accents US et UK sûrs, AU et CA à vérifier en Phase 4) ; pas de génération d'images en local.

## Phases

| Phase | Contenu | État |
|---|---|---|
| 1 | Squelette, base locale, entraînement Part 5 avec corrections, révision espacée, premier lot de 120 questions Part 5 | terminée le 12/11/2026 |
| 2 | Parts 6 et 7, fiches de cours, chemin de leçons, Part 5 complétée à 300 questions | terminée le 16/11/2026 (bilan : `docs/bilan-phase-2.md`) |
| 3 | Stats, score estimé, XP, série, badges, objectifs, notifications, animations, demi-test Reading | terminée le 18/11/2026 (bilan : `docs/bilan-phase-3.md`) |
| 4 | Pipeline audio et images, Parts 1 à 4 | à faire |
| 5 | Diagnostic complet, tests blancs, session recommandée | à faire |

Le demi-test Reading est avancé en Phase 3 : sans lui, le score estimé n'aurait aucun test blanc sur lequel s'appuyer.

## État du code après la Phase 3 (pour la Phase 4)

- **Outils** : wrapper Gradle 9.8.0, AGP 9.4.1 (Kotlin intégré, pas de plugin `kotlin-android`), Kotlin 2.4.20, KSP 2.3.12, Room 2.8.5, Compose 1.12.1, Material 3 1.4.0. Versions dans `gradle/libs.versions.toml`. Le JDK embarqué d'Android Studio est en version 25 : `JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"`.
- **Paquet** : `fr.toeic900`. `domain/` (Leitner, QuestionReview, QuestionPicker, UnitPicker, LessonPath ; Phase 3 : ScoreEstimator, Progress (XP, objectif, série), Badges, Stats, MockPace, ReminderTime), `data/` (ContentDatabase, UserDatabase et ses migrations, ContentImporter, TrainingRepository, ProgressRepository, MockRepository, SettingsRepository), `reminder/` (alarme et notification), `ui/` (thème Élan, composants dont FeedbackPlayer et Celebrations, `AppNav` à 4 onglets, écrans dont `training/`, `progress/`, `mock/`). Injection manuelle dans `AppContainer`.
- **Décisions de la Phase 2** (documentées dans le code, à ne pas rouvrir sans raison) :
  - unité servie : une question en Part 5, un texte entier en Parts 6-7 (`UnitPicker`, `UnitRef`) ; Leitner reste par compétence, chaque question d'un texte met à jour la boîte de son tag ;
  - retour unique : un texte dont une question a été ratée revient une fois, en entier, 7 jours plus tard ; ce jour-là toutes ses questions sont marquées « retour fait » (`QuestionReview.afterAnswer`, `controlReturn`) ;
  - chemin : étapes débloquées l'une après l'autre, validées à 70 % sur un passage mené au bout, une étape réussie le reste (`LessonPath`) ; identifiants d'étape (`l-verb-tense`) stables comme ceux des questions ;
  - preuves : chaque question Part 7 porte `evidence` (citations mot pour mot), surlignées à la correction et contrôlées par le validateur ;
  - session rapide de l'accueil : toutes parts mélangées (`part = 0`) ;
  - `user.db` en version 2 (migration `MIGRATION_1_2`, testée sur appareil et sur une vraie base de la Phase 1) ; `content.db` en version 2, jetable.
- **Décisions de la Phase 3** (même règle) :
  - score estimé : table maison du taux de bonnes réponses vers 5-495, tests blancs 80 % (le plus récent pèse 1, le précédent 0,6…), entraînement des 30 derniers jours 20 % s'il compte au moins 30 réponses ; fourchette = incertitude statistique + 20 points (+ 30 sans test blanc, alors « provisoire ») ; tout est dans `docs/score-estime.md` ;
  - XP, série, statistiques et badges sont recalculés depuis l'historique (`ProgressRepository.build`) ; seuls sont stockés les jours d'objectif atteint (`goal_day`, pour qu'un objectif relevé n'efface pas le passé) et les badges débloqués (`badge_unlock`, ils restent acquis) ;
  - série : semaine du lundi au dimanche validée à N jours d'objectif (5 par défaut, réglable de 3 à 7) ; la semaine en cours ne casse jamais la série ;
  - badges de score total (750 à 900) en attente du Listening ; badges Reading 375 à 450 seulement avec au moins un demi-test ;
  - tests blancs : pool `mock`, identifiants 9001 et suivants (diagnostic 8000-8999, entraînement jusqu'à 7999, exemples du guide 7991-7992), définis dans `content/mock/*.json` (unités dans l'ordre, minutes), contenu embarqué au format 3 ; leurs réponses vont dans `mock_answer` et ne touchent jamais à Leitner ;
  - demi-test : chronomètre sur l'horloge murale (il continue si l'on quitte l'app), navigation libre, réponses modifiables jusqu'à la remise, copie rendue d'office au bout du temps ; rythme pondéré 22/30/61 s par question de Part 5/6/7, alerte au-delà de 2 min de retard ;
  - rappel : alarme inexacte (`setAndAllowWhileIdle`, pas de permission d'alarme exacte), reprogrammée au démarrage de l'app et du téléphone, notification seulement si l'objectif du jour n'est pas atteint ;
  - sons synthétisés par `content/tools/sounds.py` (ffmpeg), joués par SoundPool ; sons et vibrations coupables séparément ;
  - `user.db` en version 3 (`MIGRATION_2_3`, quatre tables ajoutées, testée sur appareil et sur une vraie base de la Phase 2) ; `content.db` en version 3 (`mock_test`).
- **Contenu** : `content/bank/part5/` (lots 01 à 10, `p5-0001` à `p5-0300`), `part6/` (lots 01 à 05, `p6-0001` à `p6-0025`), `part7/` (lots 01 à 10, `p7-0001` à `p7-0050`), `mock/` (demi-test Reading n° 1 : `p5-9001` à `p5-9015`, `p6-9001` et `p6-9002`, `p7-9001` à `p7-9008`, 50 questions ; plan : `content/drafts/mock_plan.py`), 24 fiches dans `content/lessons/fiches/`, chemin dans `content/lessons/path.json` (7 unités, 31 étapes). Schémas et taxonomie (24 tags) dans `content/schemas/`, guide dans `content/GUIDE-REDACTION.md`. `python content/tools/build.py` valide puis écrit `content.json` (1,2 Mo) ; l'app réimporte quand la version change (environ 4 s au premier lancement sur l'émulateur). Ne jamais renuméroter.
- **Rédaction des lots** : sous-agents Sonnet, deux à la fois au plus, chacun avec son dossier de travail et un fichier de sortie, plan imposé (tag, difficulté, lettre) produit par `content/drafts/p5_plan.py` ou `content/drafts/reading_plan.py`, puis relecture critique ici. Défauts récurrents trouvés à la relecture : distracteur défendable à l'oral, phrase d'insertion qui tient à deux endroits, mot de synonyme déjà testé (« cover » quatre fois), noms de personnes et d'entreprises repris d'un document à l'autre (les rédacteurs les vérifient mal : relancer une détection sur toute la banque).
- **Tests** : `gradlew testDebugUnitTest` (147), `python -m unittest discover -s content/tools/tests` (105), et 13 tests sur appareil lancés par `adb shell am instrument -w fr.toeic900.test/androidx.test.runner.AndroidJUnitRunner` après `gradlew installDebug installDebugAndroidTest`. `gradlew connectedDebugAndroidTest` rapporte 0 test sur ce poste : ne pas s'y fier.
- **Émulateur** : `Medium_Phone_API_37.0` (411 dp ; `adb shell wm density 450` simule les 384 dp du S25+, `wm density reset` pour revenir). Laissé allumé une nuit, son système est tombé (`DeadSystemException`, installation « Broken pipe ») : le redémarrer (`adb emu kill`) plutôt que chercher dans l'app. `local.properties` s'écrit avec des `/`.
- **Galaxy S25+ de l'auteur** (SM-S936B, Android 17, API 37, 384 dp) : Phase 1 installée et testée le 12/11/2026 ; Phase 2 installée par-dessus le 18/11/2026 (progression conservée, accueil vérifié) ; Phase 3 installée par-dessus le 18/11/2026 (migration 2 → 3 réussie, 27 réponses conservées, accueil vérifié). Si `adb devices` ne voit plus le téléphone après une déconnexion, `adb kill-server` puis `adb start-server`.
- **Non vérifié** : voir `docs/bilan-phase-3.md`.
- **Limites connues** : session ou demi-test en cours non repris après fermeture de l'app ; textes d'interface en français dans le code (pas de `strings.xml`) ; difficulté des questions estimée, non calibrée ; un seul demi-test (ne mesure qu'au premier passage).

## Façon de travailler

- Tests unitaires obligatoires sur la logique critique : révision espacée, score estimé, série hebdomadaire, sélection de la session recommandée, validation du contenu.
- Commits clairs à chaque étape. README qui explique comment builder, lancer et régénérer le contenu.
- À la fin de chaque phase : bilan (fait, reste, limites connues) et mise à jour du tableau « Phases » ci-dessus.
- Forfait Claude Pro : une session neuve par phase, pour limiter la consommation.
