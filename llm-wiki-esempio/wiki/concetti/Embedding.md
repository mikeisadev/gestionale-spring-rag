---
type: concept
aliases: [vettore semantico]
tags: [rag, embedding, ollama]
sources: [lezioni/03-embedding-e-ricerca-semantica.md]
updated: 2026-11-06
---

# Embedding

Un **embedding** è un vettore di numeri che rappresenta il *significato* di un testo.
Testi con significato simile hanno vettori vicini, anche se usano parole diverse.

## Quanto sono vicini due testi?

pgvector usa la **similarità del coseno**: il coseno dell'angolo tra i due vettori $A$ e $B$.

$$
\cos(\theta) = \frac{A \cdot B}{\lVert A \rVert \, \lVert B \rVert}
$$

Vale $1$ per significati identici e si avvicina a $0$ per testi che non c'entrano nulla.
Nel gestionale la soglia minima è $0{,}5$.

## Il modello del gestionale

- modello: **bge-m3**, eseguito da Ollama in Docker;
- dimensioni: **1024** valori per vettore (la colonna `vector(1024)` di pgvector deve coincidere);
- su un Mac senza GPU in Docker serve circa 1–20 secondi per chunk: è la fase più lenta
  dell'ingestione, come mostrano le tracce di Zipkin (lezione 9).

## Errore tipico

Cambiare modello di embedding senza reindicizzare: i vettori vecchi e nuovi non sono confrontabili.
Prima di cambiare modello bisogna rigenerare tutti gli embedding.

Vedi anche: [[RAG]], [[Chunking]].
