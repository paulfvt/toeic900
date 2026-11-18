# Score estimé : méthode et limites

Code : `app/src/main/java/fr/toeic900/domain/ScoreEstimator.kt`, tests :
`app/src/test/java/fr/toeic900/domain/ScoreEstimatorTest.kt`.

**Ce score est une estimation.** L'app l'affiche toujours avec une fourchette et l'étiquette
« estimé ». Pour recaler l'estimation, rien ne remplace un test officiel ETS avant l'examen.

## 1. Table de conversion (maison)

ETS ne publie pas de table fixe : la conversion du nombre de bonnes réponses en score varie d'un
test à l'autre. L'app utilise une table lissée qui va du **taux** de bonnes réponses au score de
la section (5 à 495), avec une interpolation linéaire entre les points :

| Taux de bonnes réponses | 0-10 % | 20 % | 30 % | 40 % | 50 % | 60 % | 70 % | 80 % | 90 % | 95 % | 97 % | 100 % |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| Score Reading | 5 | 55 | 105 | 155 | 210 | 265 | 320 | 380 | 440 | 470 | 485 | 495 |

Le résultat est arrondi au multiple de 5, comme un vrai score. Repère principal de la table :
90 % de bonnes réponses donne 440, le Reading qu'il faut pour un total de 900 avec un Listening à 460 ;
70 % donne 320. Le
demi-test compte 50 questions au lieu de 100 : son taux est utilisé tel quel.

## 2. Pondération

- **Tests blancs : 80 %.** Moyenne des taux, le plus récent avec un poids de 1, le précédent 0,6,
  puis 0,36, et ainsi de suite : la progression récente compte davantage.
- **Entraînement des 30 derniers jours : 20 %**, s'il compte au moins 30 réponses. Les réponses
  des tests blancs n'en font pas partie.
- **Sans test blanc**, l'entraînement seul donne une estimation marquée **« provisoire »**.
  Moins de 30 réponses d'entraînement et aucun test : pas d'estimation.

L'entraînement pèse peu parce qu'il est plus facile qu'un test : pas de chronomètre serré, la
révision espacée ramène les compétences déjà vues, et la correction suit chaque question.

## 3. Fourchette

1. Incertitude statistique d'un taux mesuré sur *n* questions : écart-type √(p(1−p)/n), au moins
   2 points de pourcentage, puis ± 1,645 écart-type (environ 90 %), convertis par la table. Une
   question d'entraînement compte pour un quart de question de test blanc dans *n*, et les tests
   anciens comptent selon leur poids.
2. Plus une **marge fixe de 20 points** de chaque côté, parce que la table et la difficulté des
   questions ne sont pas calibrées sur de vrais candidats.
3. Plus **30 points** de chaque côté quand l'estimation est provisoire.

La fourchette se resserre donc avec le nombre de tests blancs, sans jamais descendre sous
± 20 points environ.

## Limites connues

- Questions rédigées par Claude, difficulté estimée et non mesurée : un demi-test maison peut être
  plus facile ou plus difficile qu'un vrai TOEIC.
- Un seul demi-test Reading en Phase 3 : le refaire ne mesure plus rien, puisque les réponses sont
  connues. Les tests blancs complets arrivent en Phase 5.
- Le Listening n'est pas estimé avant la Phase 4 ; le score total, et les badges 750 à 900 qui en
  dépendent, attendent ce moment.
