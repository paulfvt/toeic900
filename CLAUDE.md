# TOEIC 900 — contexte du projet

Application Android personnelle de préparation au TOEIC pour son auteur, **débutant en développement mobile** : chaque étape d'installation, de build et de lancement s'explique comme à un débutant. Objectif : 900. Examen prévu début 2027 (« dans 2 à 3 mois », dit le 12/11/2026 ; date exacte non fixée). Point faible : le Reading.

- Brief complet de l'auteur, **il fait foi** : `docs/brief.md` (non versionné)
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
- **Visuel** : direction « Élan » choisie par l'auteur sur maquettes. Traduire les tokens en thème Compose (couleurs, typographie, formes, boutons « à tranche ») sans réinventer de palette. Polices Nunito (interface) et Nunito Sans (textes d'examen) embarquées. Le mode sombre est vérifié en contraste par calcul, mais n'a jamais été relu visuellement.
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
| 1 | Squelette, base locale, entraînement Part 5 avec corrections, révision espacée, premier lot de 120 questions Part 5 | lancée le 12/11/2026 |
| 2 | Parts 6 et 7, fiches de cours, chemin de leçons | à faire |
| 3 | Stats, score estimé, XP, série, badges, objectifs, notifications, animations, demi-test Reading | à faire |
| 4 | Pipeline audio et images, Parts 1 à 4 | à faire |
| 5 | Diagnostic complet, tests blancs, session recommandée | à faire |

Le demi-test Reading est avancé en Phase 3 : sans lui, le score estimé n'aurait aucun test blanc sur lequel s'appuyer.

## Façon de travailler

- Tests unitaires obligatoires sur la logique critique : révision espacée, score estimé, série hebdomadaire, sélection de la session recommandée, validation du contenu.
- Commits clairs à chaque étape. README qui explique comment builder, lancer et régénérer le contenu.
- À la fin de chaque phase : bilan (fait, reste, limites connues) et mise à jour du tableau « Phases » ci-dessus.
- Forfait Claude Pro : une session neuve par phase, pour limiter la consommation.
