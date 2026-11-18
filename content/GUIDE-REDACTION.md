# Guide de rédaction du contenu

Ce guide sert à écrire le contenu de TOEIC 900 : questions Part 5, textes Part 6, documents
Part 7 et fiches de cours. Chaque lot est contrôlé par `content/tools/validate.py`, puis relu
de façon critique avant d'être embarqué dans l'app.

Deux règles valent partout. **Contenu 100 % original** : rien n'est repris ni adapté d'un test
officiel ETS, d'un livre ou d'un site de préparation ; entreprises, personnes, adresses et
produits sont inventés. **Une seule bonne réponse défendable** : remets chaque distracteur à sa
place ; si un anglophone pouvait l'accepter, même en forçant, change le texte ou le distracteur.

# Part 5 : phrases à compléter

Les lots vont dans `content/bank/part5/`.

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
      "id": "p5-7991",
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
      "id": "p5-7992",
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

Les identifiants `p5-7991` et `p5-7992` ne servent qu'à ces exemples : ils ne font pas partie de la banque. Plages par pool : entraînement de 0001 à 7999 (7990 à 7999 réservés aux exemples), diagnostic de 8000 à 8999, tests blancs de 9000 à 9999.

## Contrôle

```
python content/tools/validate.py content/bank/part5/lot-01.json
```

Le script doit finir par `0 erreur(s)`. Il ne remplace pas la relecture : il ne sait pas dire
si un distracteur est défendable.

# Part 6 : textes à trous

Les lots vont dans `content/bank/part6/`. Modèle complet et valide : `content/examples/part6-exemple.json`.
Schéma : `content/schemas/part6.schema.json`.

## Le texte

1. Un document professionnel court en anglais américain, de **80 à 140 mots** : e-mail, note de
   service, avis, annonce, article, lettre, publicité, instructions… Champ `genre` : une valeur
   de la liste du schéma. Varie les genres et les secteurs d'un texte à l'autre.
2. Exactement **4 trous**, notés `[[1]]`, `[[2]]`, `[[3]]`, `[[4]]`, dans cet ordre, répartis dans
   le texte (pas deux trous dans la même phrase). Paragraphes séparés par une ligne vide ;
   en-têtes d'e-mail sur des lignes séparées.
3. Guillemets et apostrophes **droits** (`"` et `'`), jamais courbes.

## Les 4 questions

Une question par trou, dans l'ordre ; identifiants `<id du texte>-1` à `-4`. Pas de champ `stem` :
la question est le trou.

- **Une seule insertion de phrase** par texte (premier tag `sentence-insertion`) : le trou
  remplace une phrase entière, les 4 options sont des phrases complètes. La bonne phrase
  s'accroche à ce qui précède ET à ce qui suit ; les distracteurs parlent du même sujet général
  mais cassent la logique (hors sujet, contredit le texte, arrive trop tôt ou trop tard).
- **Trois questions de grammaire ou de vocabulaire**, avec les tags de la Part 5. Au moins une
  doit dépendre du **contexte** : temps imposé par une date donnée ailleurs dans le texte,
  connecteur choisi d'après la phrase précédente, pronom dont le référent est dans une autre
  phrase, mot de vocabulaire que seul le reste du texte départage.
- Mêmes exigences que la Part 5 pour les options (4 formes d'une même racine, 4 mots de même
  nature…), la difficulté et les explications (`rule`, `distractors`, `trap`, en `fr` et `en`).
- `evidence` (facultatif, 0 à 3 citations) : les passages du texte qui justifient la réponse,
  **recopiés mot pour mot**, sans trou. À fournir dès que la réponse dépend du contexte, et
  toujours pour l'insertion de phrase. L'app les surligne dans le texte après la correction.

# Part 7 : documents

Les lots vont dans `content/bank/part7/`. Modèle complet et valide (un document seul, puis un
jeu de deux documents) : `content/examples/part7-exemple.json`. Schéma : `content/schemas/part7.schema.json`.

## Les documents

1. Un jeu = **1 document** (60 à 300 mots) ou **2 à 3 documents liés** (350 à 600 mots au total),
   par exemple une annonce et l'e-mail d'un client, ou un planning, un avis et une réponse.
2. Genres variés (`genre` : liste du schéma) : e-mail, avis, article, publicité, page web,
   échange de messages (`chat`), formulaire, planning, facture, offre d'emploi…
3. **Texte simple**, lisible sur un téléphone : paragraphes séparés par une ligne vide, listes
   sur des lignes séparées (« October 4 - Bookkeeping Basics - $60 »). **Pas de tableau** à
   colonnes alignées. Pour un `chat` : une ligne par message, « Nom (10:02 A.M.): message ».
4. `title` (facultatif) : l'en-tête du document, s'il en a un.
5. Guillemets et apostrophes droits. Dates, prix et noms cohérents d'un document à l'autre.

## Les questions

- 1 document : **2 à 4 questions**. 2 ou 3 documents : **exactement 5 questions**.
- Identifiants `<id du jeu>-1`, `-2`… Les questions suivent l'ordre du texte.
- Chaque question a un `stem` en anglais et 4 options de même forme (4 groupes verbaux, 4 noms,
  4 phrases…) et de longueur comparable.
- Le premier tag dit ce que la question teste :

| Tag | Forme typique de la question |
|---|---|
| `main-idea` | What is the purpose of the e-mail? / What is the article mainly about? / Why did Ms. Ito write to Mr. Bell? |
| `detail` | According to the notice, what…? / When will…? / What is indicated about…? (réponse écrite dans le texte, reformulée dans l'option) |
| `inference` | What is suggested / implied about…? / What will most likely happen…? / Who most likely is…? (réponse non écrite, déduite d'une phrase précise) |
| `not-true` | What is NOT mentioned as…? / What is NOT true about…? (trois options sont dans le texte, une n'y est pas) |
| `synonym-in-context` | The word "secure" in paragraph 2 is closest in meaning to (un mot à plusieurs sens ; les distracteurs sont ses autres sens). **Jamais de numéro de ligne** : sur un téléphone, les lignes ne correspondent à rien. Si le mot apparaît deux fois dans le paragraphe, change le texte. |
| `writer-intention` | At 10:15 A.M., what does Mr. Diaz most likely mean when he writes, "Count me in"? (dans un `chat` : le sens d'une courte réplique d'après le contexte) |
| `sentence-insertion` | In which of the positions marked [1], [2], [3], and [4] does the following sentence best belong? "…" (options : `[1]`, `[2]`, `[3]`, `[4]` ; les 4 repères sont dans un seul document) |
| `cross-reference` | Toute question dont la réponse exige de combiner deux documents (jeux de 2 ou 3 documents seulement, au moins une par jeu) |

- **La bonne réponse reformule le texte** ; les distracteurs reprennent ses mots dans un autre
  sens, ou sont plausibles mais absents du texte. Jamais une réponse trouvable sans lire.
- `evidence` (obligatoire, 1 à 3 citations) : les passages qui justifient la réponse, **recopiés
  mot pour mot** depuis les documents. Une question `cross-reference` cite au moins deux
  documents ; une question `not-true` cite les passages qui prouvent les trois options vraies.
- Explications : `rule` cite le passage et explique le lien avec la bonne réponse (reformulation,
  déduction) ; `distractors` dit pour chaque mauvaise option ce que le texte dit vraiment ou
  pourquoi elle n'y figure pas ; `trap` donne une méthode réutilisable.

# Fiches de cours

Une fiche par compétence, dans `content/lessons/fiches/<tag>.json`. Modèle :
`content/lessons/fiches/participle-adjectives.json`. Schéma : `content/schemas/lesson.schema.json`.

- Deux versions de **même structure**, `fr` et `en` : mêmes points clés, mêmes règles, mêmes
  exemples (la phrase d'exemple `en` est identique dans les deux versions ; seule la `note` est
  traduite), mêmes pièges.
- `summary` : l'idée en 2 ou 3 phrases. `keyPoints` : 2 à 4 points à retenir, c'est la
  mini-fiche montrée avant les exercices d'une leçon.
- `rules` : 2 à 5 règles, de la plus fréquente au TOEIC à la plus fine. Chacune a un titre, un
  texte court et 1 à 4 exemples dans un contexte professionnel, avec une note qui dit ce que
  l'exemple montre.
- `traps` : 2 à 4 pièges classiques du TOEIC sur cette compétence : ce que les concepteurs du
  test mettent dans les distracteurs, et comment ne pas tomber dedans.
- Seul balisage : `**mot**` pour mettre en avant le mot clé d'un exemple ou d'une règle.
- Français naturel, tutoiement, mots anglais entre guillemets « ». Anglais simple (lecteur B1-B2).
  Viser l'utile pour un candidat autour de 750 qui vise 900 : la règle, l'indice à repérer dans la
  phrase, l'exception qui tombe à l'examen. Pas de cours exhaustif.
- Pour les compétences de lecture (Part 7), les « règles » sont des méthodes : comment reconnaître
  le type de question, où chercher la réponse, comment éliminer.

# Contrôle des Parts 6 et 7 et des fiches

```
python content/tools/validate.py chemin/vers/le-lot-ou-la-fiche.json
```

Même commande que pour la Part 5 : le script reconnaît seul un lot Part 5, 6 ou 7 et une fiche.
Sans argument, il valide tout `content/`, y compris le chemin de leçons (`content/lessons/path.json`).
