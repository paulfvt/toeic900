> Proposition rédigée par Claude le 12/11/2026. L'auteur a lancé la Phase 1 sur cette base ; les décisions qui tranchent les questions restées ouvertes sont dans `CLAUDE.md`.

# Proposition pour « TOEIC 900 »

Rien n'est codé ni créé pour l'instant : voici la proposition à valider, comme demandé.

## Ton PC

Je l'ai inspecté moi-même :

- **Matériel** : PC portable, carte graphique intégrée (pas de carte dédiée), 55 Go libres.
- **Android** : Android Studio récent, SDK (API 37), un émulateur « Medium_Phone » déjà créé et l'accélération matérielle fonctionnelle. Il n'y a donc presque rien à installer.
- **Outils** : Git, Python 3.13 et ffmpeg sont présents. Ton Java 11 est trop ancien pour Android, mais Android Studio embarque le sien, que j'utiliserai.
- **Téléphone** : aucun n'est branché actuellement.

Deux conséquences :

- **Audio** : la synthèse vocale tournera sur le processeur, ce qui suffit pour Piper et Kokoro. Je les comparerai à l'écoute en Phase 4. Les accents américain et britannique sont acquis ; l'australien et le canadien restent à vérifier.
- **Images** : générer des photos réalistes en local n'est pas viable sans carte dédiée. Je propose des photos libres de droits (question 4).

## Stack : Kotlin + Jetpack Compose

| Option | Pour | Contre |
|---|---|---|
| **Kotlin + Compose (recommandé)** | Natif Android, déjà installé, base locale, audio et notifications fournis par Google, une seule chaîne d'outils | Kotlin est nouveau pour toi |
| Flutter | Animations, rechargement à chaud | SDK supplémentaire (~3 Go), multi-plateforme inutile ici, plugins tiers pour audio et notifications |
| React Native | JavaScript | Chaîne de build la plus fragile sous Windows |

Comme l'app est Android seul et que tout est déjà installé, le natif réduit au minimum ce qui peut casser. Les briques :

- **Base locale** : Room (SQLite).
- **Audio** : Media3, avec des fichiers Opus.
- **Réglages** : DataStore.
- **Rappels** : AlarmManager et notifications.
- **Interface** : Compose et Material 3.
- **Pipeline de contenu** : Python.

Je n'ajoute ni framework d'injection de dépendances ni découpage en plusieurs modules, pour que le projet reste lisible.

## Architecture

```
toeic900/
├─ app/src/main/.../
│  ├─ domain/   # Kotlin pur : révision espacée, score, streak, XP, recommandation
│  ├─ data/     # Room, import du contenu, accès aux données
│  └─ ui/       # écrans Compose
├─ app/src/test/        # tests unitaires (sans émulateur)
├─ content/
│  ├─ schemas/          # un schéma JSON par part
│  ├─ bank/ mock/ diagnostic/ lessons/   # sources JSON
│  ├─ tools/            # validate.py, build.py, tts.py, images.py + tests
│  └─ media/            # audio et images générés
└─ README.md
```

Toute la logique critique vit dans `domain/`, sans dépendance Android, donc testable en quelques secondes.

## Modèle de données

Il y a deux bases, reliées par des identifiants stables comme `p5-0142` :

- **Contenu**, reconstruit à chaque nouvelle version des JSON :
  - `Question` : part, énoncé, options, réponse, difficulté, explications FR/EN, note par distracteur, piège, et pool (entraînement, test blanc ou diagnostic).
  - `Stimulus` : texte Part 6, documents Part 7, audio et transcription, photo.
  - `Tag`, `Lesson`, `VocabWord`, `MockTest`.
- **Utilisateur**, jamais détruite :
  - `Attempt`, `Session`, `MockResult`, `LessonProgress`, `DailyActivity`, `Badge`.
  - `SkillState` : boîte et échéance par compétence.
  - `QuestionState` : questions vues et ratées.
  - `VocabState`.

Cette séparation me permet de corriger ou d'ajouter des questions sans effacer ta progression.

## Deux choix de fond

- **Révision espacée** : Leitner à 5 boîtes (1, 2, 4, 8, 16 jours), appliqué à la compétence et non à la question.
  - Quand une compétence est due, l'app tire une question que tu n'as pas encore vue sur ce tag.
  - Une question ratée ne revient qu'une fois, au moins 7 jours plus tard, pour contrôle.
  - Le vocabulaire suit le même système, mot par mot.
  - J'écarte SM-2 parce qu'il demande une auto-évaluation de 0 à 5 qui n'a pas de sens sur un QCM, et que ses longs intervalles ne servent pas sur 2-3 mois.
- **Score estimé** : une table de conversion maison, documentée, qui va du nombre de bonnes réponses à un score de 5 à 495 par section.
  - Les tests blancs pèsent environ 80 %, les plus récents comptant davantage ; l'entraînement pèse environ 20 %.
  - La fourchette s'élargit quand il y a peu de données.
  - Mes questions ne sont pas calibrées sur de vrais candidats. Je te conseille donc de faire un test officiel ETS avant l'examen pour recaler l'estimation.

## Volumes de contenu

Environ 1 300 questions au total :

| Pool | Contenu |
|---|---|
| Entraînement Reading | 300 questions Part 5, 25 textes Part 6 (100 questions), 50 documents Part 7 (~160 questions) |
| Entraînement Listening | 30 photos, 100 questions Part 2, 30 conversations, 25 monologues |
| Tests blancs | 2 × 200 questions, distinctes de l'entraînement |
| Diagnostic | ~50 questions dédiées |

C'est le plus gros du travail. Je le produirai par lots sur plusieurs sessions, chaque lot passant par le script de validation puis une relecture critique. L'app pèsera environ 60 à 70 Mo, dont 3 à 4 heures d'audio.

## Phases

Je reprends tes cinq phases avec un seul ajustement, en Phase 3.

| Phase | Livré sur ton téléphone |
|---|---|
| 1 | Squelette, bases, entraînement Part 5 (premier lot de ~120 questions), corrections, révision espacée |
| 2 | Parts 6 et 7, fiches de cours, chemin de leçons, Part 5 complétée à 300 |
| 3 | Stats, score estimé, XP, streak, badges, objectifs, notifications, animations, **plus le demi-test blanc Reading** |
| 4 | Pipeline audio et images, Parts 1 à 4 |
| 5 | Diagnostic, tests blancs complets, session recommandée |

Chaque phase se termine par des tests unitaires, des commits, une mise à jour du README et un bilan.

## Mes questions

Chacune a un choix par défaut, que j'appliquerai si tu valides en bloc :

1. **Emplacement du projet.** Le dossier actuel est dans OneDrive, avec un espace dans son nom. Un build Android génère des milliers de fichiers que OneDrive synchronise, ce qui provoque lenteurs et fichiers verrouillés. Par défaut : je crée le projet dans `C:\dev\toeic900`.
2. **Ton téléphone.** Quel modèle et quelle version d'Android ? Par défaut : compatible à partir d'Android 8.
3. **Demi-test Reading avancé en Phase 3.** Sinon, le score estimé de la Phase 3 ne reposerait sur aucun test blanc. Par défaut : oui.
4. **Images Part 1.** Photos Pexels ou Unsplash, en choisissant la photo d'abord puis en écrivant la question dessus, pour que la bonne réponse corresponde forcément. Je te demanderai l'autorisation de télécharger en Phase 4. Par défaut : oui.
5. **Semaine validée.** Par défaut : objectif quotidien atteint au moins 5 jours sur 7, seuil réglable.
6. **Date d'examen.** Si tu la connais, j'ajoute un compte à rebours et je cale le rythme des révisions dessus.
