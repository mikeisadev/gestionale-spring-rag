---
type: concept
aliases: [Retrieval-Augmented Generation]
tags: [rag, spring-ai]
sources: [lezioni/03-embedding-e-ricerca-semantica.md, lezioni/05-memoria-e-persistenza-conversazioni.md]
updated: 2026-11-06
---

# RAG

**Retrieval-Augmented Generation**: prima di rispondere, il sistema *recupera* dal database
vettoriale i pezzi di documento più vicini alla domanda e li passa al modello insieme alla domanda.

## Le due fasi

![[pipeline-rag.svg]]

1. **Ingestione**: i documenti vengono letti, divisi in [[Chunking|chunk]] e trasformati in vettori
   con un modello di [[Embedding]]. I vettori finiscono in pgvector.
2. **Interrogazione**: anche la domanda diventa un vettore; pgvector restituisce i chunk più simili
   (distanza del coseno) e il modello scrive la risposta citando le fonti.

## Nel gestionale

- ricerca: `RagService.search`, con `topK = 5` e soglia di similarità `0,5`;
- i documenti arrivano al modello dentro tag `<documento>` (lezione 5), aggiunti dal `DocumentContextAdvisor`;
- il database vettoriale **non genera** risposte: recupera soltanto.

## Limiti

Il RAG trova *frammenti*: se la risposta richiede di confrontare dieci documenti, i 5 chunk
recuperati possono non bastare. Una LLM Wiki aiuta perché contiene sintesi già pronte.
Anche i documenti recuperati possono contenere [[Prompt injection|istruzioni malevole]].
