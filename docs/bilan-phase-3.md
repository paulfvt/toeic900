# Bilan de la Phase 3

Phase lancée et terminée le 18/11/2026.

## Ce qui est fait

**Suivi de progression**

- **Score Reading estimé**, toujours avec une fourchette et étiqueté comme une estimation : table de
  conversion maison, demi-tests 80 % (les plus récents pèsent plus), entraînement des 30 derniers
  jours 20 %, estimation « provisoire » sans demi-test. Méthode et limites : `docs/score-estime.md`.
- **Onglet Progrès** (4ᵉ onglet) : score estimé, niveau et XP, série hebdomadaire, activité jour par
  jour sur 28 jours, réussite par part et par compétence (de la plus fragile à la plus solide, un
  appui ouvre la fiche), historique et courbe des demi-tests.

**Demi-test Reading n° 1**

- 50 questions originales du pool `mock`, jamais servies à l'entraînement, aux proportions d'une vraie
  section Reading divisées par deux : 15 questions Part 5, 2 textes Part 6, 8 jeux Part 7 (6 seuls,
  2 multiples), 38 minutes.
- Comme à l'examen : navigation libre, réponses modifiables jusqu'à la remise, aucune correction avant
  la fin, copie rendue d'office quand le temps est écoulé. Indicateur de rythme pondéré par part, avec
  une alerte qui chiffre le temps restant pour la Part 7.
- Résultats : score de ce test avec sa fourchette, détail par part et par compétence, revue de chaque
  erreur (réponse donnée, bonne réponse, règle, piège, texte d'origine avec le passage justificatif).

**Motivation**

- XP (10 par bonne réponse, 2 par erreur, bonus d'étape, de demi-test et d'objectif) et niveaux.
- Objectif quotidien en questions (10 à 50) ou en minutes (10 à 30) ; série hebdomadaire validée à
  5 jours sur 7 par défaut, seuil réglable de 3 à 7.
- 25 badges : score (Reading 375 à 450 dès maintenant, total 750 à 900 quand le Listening sera
  estimé), régularité, maîtrise d'une compétence (boîte 5), chemin, volume, demi-tests.
- Correction animée (coche qui rebondit, croix qui tremble, « +10 XP », bandeau « objectif atteint »
  ou « nouveau badge »), sons synthétisés par `content/tools/sounds.py`, vibrations ; sons et
  vibrations se coupent dans les Réglages.
- Rappel quotidien à l'heure choisie, envoyé seulement si l'objectif du jour n'est pas atteint.

**Données**

- `user.db` passe en version 3 (`mock_result`, `mock_answer`, `badge_unlock`, `goal_day`), sans rien
  modifier aux tables existantes. `content.db` passe en version 3 (`mock_test`), contenu embarqué au
  format 3.
- Validateur : plages d'identifiants par pool (une question de test blanc ne peut pas glisser dans
  l'entraînement), contrôle des définitions de tests blancs.

## Vérifications

- 147 tests Kotlin (dont 49 nouveaux : score estimé, XP, série, objectif, badges, statistiques,
  rythme, heure du rappel, format 3, migrations), 105 tests Python, 13 tests sur appareil (dont les
  deux de la migration 2 → 3).
- Migration sur une vraie base : l'app de la Phase 2, avec sa progression, mise à jour par-dessus sur
  l'émulateur : base en version 3, aucune réponse perdue.
- Parcours à l'écran sur l'émulateur à 384 dp : accueil, onglet Progrès, introduction du demi-test,
  questions des Parts 5, 6 et 7, confirmation de remise, résultats et revue d'une erreur avec son texte.
- Relecture critique des 50 questions du demi-test : une seule retouche (difficulté de p5-9007).
  Aucun nom propre repris de la banque d'entraînement.

## Ce que la vérification à l'écran a corrigé

- En Parts 6 et 7, le panneau de réponse du demi-test cachait presque tout le texte à 384 dp : il est
  limité à la moitié de l'écran, et la part (« Part 6 ») est passée dans la barre du haut.

## Ce qui reste

- Phase 4 : pipeline audio et images, Parts 1 à 4, estimation du Listening (et donc du score total).
- Phase 5 : diagnostic, tests blancs complets, session recommandée.

## Limites connues

- Score estimé fondé sur une table maison et des questions non calibrées sur de vrais candidats.
- Un seul demi-test : il ne mesure vraiment qu'au premier passage.
- Un demi-test interrompu par la fermeture de l'app est perdu (le chronomètre, lui, ne s'arrête pas).
- Le rappel peut arriver avec quelques minutes de retard (alarme inexacte, choisie pour ne demander
  aucune permission spéciale).
