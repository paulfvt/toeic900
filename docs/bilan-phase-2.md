# Bilan de la Phase 2

Phase lancée le 13/11/2026, terminée le 16/11/2026. Elle remplace `docs/reprise-phase-2.md`, supprimé.

## Ce qui est fait

**L'app** couvre tout le Reading du TOEIC :

- Parts 6 et 7 jouables : un texte entier par exercice, panneau de question repliable en Part 7,
  passages justificatifs (`evidence`) surlignés à la correction ;
- 24 fiches de cours (une par compétence), ouvrables depuis la correction ; le chronomètre se met en
  pause pendant la lecture ;
- un chemin de leçons de 7 unités et 31 étapes, avec une barre de navigation Accueil / Chemin / Exercices ;
- la base utilisateur passe en version 2 sans rien perdre (migration testée sur appareil, puis sur une
  vraie base de la Phase 1 installée par-dessus).

**Le contenu** atteint les volumes visés, tout relu de façon critique avant d'entrer en banque :

| | Phase 1 | Phase 2 | Total |
|---|---|---|---|
| Part 5 | 120 questions | 180 questions | 300 questions (75 par lettre) |
| Part 6 | | 25 textes | 100 questions |
| Part 7 | | 50 jeux (34 seuls, 16 multiples) | 186 questions |
| Fiches | | 24 | |

Chaque compétence de grammaire apparaît dans au moins 4 textes Part 6. En Part 7, les compétences
rares en début de phase ont été renforcées : 9 questions `writer-intention`, 16 `synonym-in-context`,
9 `sentence-insertion`, 19 `not-true`.

**Vérifié** : 103 tests Kotlin, 97 tests Python, 11 tests sur appareil ; parcours à l'écran sur
l'émulateur à 411 et 384 dp, en clair et en sombre (une leçon du chemin depuis l'accueil et depuis
l'onglet Chemin, une fiche ouverte depuis une correction, un texte Part 6 et un jeu Part 7 joués de bout
en bout) ; réimportation du contenu complet sur une base existante.

## Ce que la relecture a corrigé

- Une seule correction d'interface : « Question suivante » passait sur deux lignes à 384 dp, le
  bouton dit maintenant « Suivante ». Les six autres points jugés fragiles tenaient.
- Contenu existant : trois questions à deux réponses possibles, un numéro de ligne dans une question de
  synonyme (interdit désormais : sur un téléphone, les lignes ne veulent rien dire), des exemples de
  fiches ambigus, une phrase maladroite.
- Nouveaux lots : environ une retouche par lot. Les défauts typiques des rédacteurs étaient un
  distracteur acceptable à l'oral (« the season where »), une phrase d'insertion qui tenait à deux
  endroits, le même mot testé quatre fois en synonyme (« cover »), deux calculs au centime dans un même
  jeu, et surtout des noms de personnes ou d'entreprises repris d'un document à l'autre. Pour ces
  derniers, `python content/tools/names.py` repère les collisions sur toute la banque.

## Ce qui reste

- **Installer la Phase 2 sur le Galaxy S25+** (par-dessus, sans désinstaller) et refaire un parcours rapide.
- Phase 3 : statistiques, score estimé, XP, série, badges, objectifs, notifications, animations,
  demi-test Reading.

## Limites connues

- Difficulté des questions estimée par les rédacteurs, pas calibrée sur de vrais candidats.
- Les relectures ont été faites par Claude à partir des énoncés, des options et des explications des
  questions douteuses, pas par un anglophone natif. Les explications des questions sans doute signalé
  n'ont pas toutes été relues mot à mot.
- Session en cours non reprise après fermeture de l'app ; interface en français uniquement.
- Session menée jusqu'à l'expiration du chronomètre : jamais testée.
