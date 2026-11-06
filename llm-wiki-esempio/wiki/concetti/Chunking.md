---
type: concept
tags: [rag, chunking]
sources: [lezioni/02-lettura-e-chunking-pdf.md, lezioni/10-ingestione-documenti-e-formati.md]
updated: 2026-11-06
---

# Chunking

Il **chunking** divide un documento in pezzi (chunk) abbastanza piccoli da essere precisi nella
ricerca, ma abbastanza grandi da avere un senso compiuto.

## I valori del gestionale

| Parametro | Valore | Significato |
|---|---|---|
| chunkSize | 500 token | dimensione target |
| minChunkSizeChars | 200 | minimo di caratteri |
| minChunkLengthToEmbed | 20 | sotto i 20 caratteri il chunk viene scartato |

## Overlap

Con l'**overlap** ogni chunk ripete la fine del precedente, così una frase spezzata al confine
non perde il significato. `TokenTextSplitter` lo supporta da Spring AI 2.1.

## Pagine della wiki

Le pagine Markdown vengono divise prima per **titolo** (MarkdownDocumentReader) e poi, se una sezione
è lunga, per token. Ogni chunk sa da quale pagina e sezione proviene. Vedi [[RAG]].
