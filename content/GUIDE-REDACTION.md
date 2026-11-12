# Guide de rédaction des questions Part 5

Ce guide sert à écrire un lot de questions Part 5 (phrases à compléter) pour la banque
`content/bank/part5/`. Chaque lot est ensuite contrôlé par `content/tools/validate.py`,
puis relu de façon critique avant d'être embarqué dans l'app.

## Règles de fond

1. **Contenu 100 % original.** Aucune phrase reprise ou adaptée d'un test officiel ETS, d'un
   livre publié ou d'un site de préparation. Les entreprises et les personnes sont inventées,
   avec des noms variés et internationaux.
2. **Format du TOEIC.** Une seule phrase en anglais, de 12 à 25 mots, dans un contexte
   professionnel ou de la vie courante : bureau, RH, ventes, marketing, voyages d'affaires,
   logistique, finance, événements, maintenance, commerce, hôtellerie, production, informatique.
   Un seul trou, noté `-------` (7 tirets). Anglais américain.
3. **Une seule bonne réponse défendable.** Remets chaque distracteur dans la phrase : si un
   anglophone pouvait l'accepter, même en forçant, change la phrase ou le distracteur.
4. **Distracteurs plausibles**, de la famille attendue pour la compétence :
   - forme du mot : quatre formes réelles d'une même racine (pas de mot inventé) ;
   - vocabulaire et collocations : quatre mots de même nature grammaticale ;
   - prépositions, conjonctions, pronoms, quantifieurs : quatre mots de la même catégorie
     ou des catégories que les candidats confondent (ex. `despite` / `although`).
5. **Difficulté.** 1 = la règle s'applique directement, l'indice touche le trou.
   2 = niveau standard du TOEIC, il faut analyser la structure. 3 = piège : indice éloigné du
   trou, mots intercalés entre le sujet et le verbe, mot rare, deux règles qui se croisent.
6. **Tags.** Le premier est la compétence testée. N'en ajoute un second que si une autre
   compétence est réellement nécessaire pour répondre. Identifiants : `content/schemas/tags.json`.
7. **Variété.** Change de sujet, de structure et de position du trou (début, milieu, fin)
   d'une question à l'autre. Pas deux questions avec les mêmes quatre options.

## Explications

Chaque question porte une explication en français (`fr`) et en anglais (`en`), de même contenu :

- `rule` : la règle appliquée à cette phrase, en 1 ou 2 phrases, en citant l'indice
  (« après *was*… »). Ne cite pas la lettre de la réponse : l'app l'affiche déjà.
- `distractors` : pour chacune des 3 mauvaises lettres, une phrase qui dit ce qu'est ce mot
  et pourquoi il ne convient pas ici.
- `trap` : le piège à retenir, en une phrase courte, réutilisable sur d'autres questions.

Français naturel, tutoiement, mots anglais entre guillemets « ». Anglais simple (lecteur B1-B2).

## Format

Un fichier par lot, conforme à `content/schemas/part5.schema.json` :

```json
{
  "part": 5,
  "pool": "training",
  "questions": [
    {
      "id": "p5-9001",
      "part": 5,
      "tags": ["participle-adjectives"],
      "difficulty": 2,
      "stem": "The marketing team was ------- by the results of the latest customer survey.",
      "options": ["encourage", "encouraging", "encouraged", "encouragement"],
      "answer": "C",
      "explanation": {
        "fr": {
          "rule": "Après « was », il faut un participe. L'équipe ressent l'émotion (elle ne la provoque pas), donc le participe passé en -ed : « was encouraged by ».",
          "distractors": {
            "A": "« encourage » est le verbe à la base verbale : impossible après « was ».",
            "B": "« encouraging » décrit ce qui provoque l'émotion (« the results were encouraging »), pas celui qui la ressent.",
            "D": "« encouragement » est un nom : « the team was encouragement » n'a pas de sens, et « by » annonce un passif."
          },
          "trap": "-ing = la cause de l'émotion ; -ed = celui qui la ressent."
        },
        "en": {
          "rule": "After \"was\" you need a participle. The team feels the emotion (it does not cause it), so use the -ed past participle: \"was encouraged by\".",
          "distractors": {
            "A": "\"encourage\" is the base form of the verb, which cannot follow \"was\".",
            "B": "\"encouraging\" describes what causes the feeling (\"the results were encouraging\"), not who feels it.",
            "D": "\"encouragement\" is a noun: \"the team was encouragement\" makes no sense, and \"by\" signals a passive."
          },
          "trap": "-ing = the cause of the feeling; -ed = the one who feels it."
        }
      }
    },
    {
      "id": "p5-9002",
      "part": 5,
      "tags": ["conjunctions-connectors"],
      "difficulty": 2,
      "stem": "------- the shipment arrived two days late, the store managed to restock its shelves before the weekend sale.",
      "options": ["Despite", "Although", "However", "Because"],
      "answer": "B",
      "explanation": {
        "fr": {
          "rule": "Le trou est suivi d'une proposition complète (sujet « the shipment » + verbe « arrived ») et les deux idées s'opposent : il faut une conjonction de concession, « although ».",
          "distractors": {
            "A": "« despite » est une préposition : elle se construit avec un nom ou un -ing (« despite the late shipment »), pas avec sujet + verbe.",
            "C": "« however » est un adverbe de liaison : il ne peut pas relier deux propositions dans une même phrase de cette façon.",
            "D": "« because » se construit bien avec sujet + verbe, mais exprime la cause : un retard n'explique pas que le magasin ait réussi à se réapprovisionner."
          },
          "trap": "Sujet + verbe après le trou → conjonction (although) ; nom ou -ing → préposition (despite)."
        },
        "en": {
          "rule": "The blank is followed by a full clause (subject \"the shipment\" + verb \"arrived\") and the two ideas contrast, so you need a conjunction of concession: \"although\".",
          "distractors": {
            "A": "\"despite\" is a preposition: it takes a noun or an -ing form (\"despite the late shipment\"), not subject + verb.",
            "C": "\"however\" is a linking adverb: it cannot join two clauses inside one sentence this way.",
            "D": "\"because\" does take subject + verb, but it expresses a cause: a delay does not explain why the store managed to restock."
          },
          "trap": "Subject + verb after the blank → conjunction (although); noun or -ing → preposition (despite)."
        }
      }
    }
  ]
}
```

Les identifiants `p5-9001` et `p5-9002` ne servent qu'à ces exemples : ils ne font pas partie de la banque.

## Contrôle

```
python content/tools/validate.py content/bank/part5/lot-01.json
```

Le script doit finir par `0 erreur(s)`. Il ne remplace pas la relecture : il ne sait pas dire
si un distracteur est défendable.
