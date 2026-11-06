# LLM Wiki di esempio (lezione 11)

Una wiki **minima** per provare il collegamento tra LLM Wiki e RAG senza avere ancora
un vault tuo. Ha la stessa struttura di una LLM Wiki vera:

```
llm-wiki-esempio/
├── raw/    fonti originali (qui: un indice delle lezioni del corso)
└── wiki/   pagine scritte dall'LLM durante l'ingest
```

Le pagine riassumono alcune lezioni di questo progetto. La pagina `wiki/riservato/Politica stipendi.md`
è **inventata** e ha `department: HR` nel frontmatter: serve a provare i permessi per reparto.

## Come usarla

- **Docker**: è la wiki predefinita (se `WIKI_VAULT_PATH` non è impostata in `docker/.env`).
- **VS Code**: crea il file `.env` nella cartella del progetto (copia `.env.example`) con
  `WIKI_VAULT_PATH=<percorso assoluto di questa cartella>`.

Poi, dalla pagina "Upload file per sistema RAG", premi **Aggiorna indice della wiki**.

## La tua wiki

Questa cartella è solo un esempio. Per costruire la tua LLM Wiki, su qualsiasi argomento,
studia la lezione **L19 – LLM Wiki** del corso. Un'idea: usa le lezioni in `lezioni/` come
materiale `raw/` e falle "ingerire" a un agente per generare una wiki di studio.
