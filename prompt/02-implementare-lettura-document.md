Implementa il seguente codice.

NOTA: non considerare gli altri documenti successivi, considerali solo in modo incrementale perché devo dimostrare ogni punto a livello didattico. Preoccupati solo dei punti da implementare se ti chiedo di implementare questo file, ignora quelli successivi o non menzionati. Ti dirò io come procedere o quale prompt ingaggiare nel prossimo lavoro. 

**17\) Estrarre il testo con PagePdfDocumentReader**

Adesso arriviamo alla prima vera classe del RAG.

“**PagePdfDocumentReader**” è un componente di Spring AI che utilizza **Apache PDFBox** per leggere un PDF e trasformarne il contenuto in oggetti “**Document**”.

La cosa interessante è che possiamo ottenere un documento per ogni pagina del PDF.

Per esempio, se carichiamo un PDF di 10 pagine:

| manuale.pdf        \|        vPagePdfDocumentReader        \|        vDocument 1 → Pagina 1Document 2 → Pagina 2Document 3 → Pagina 3...Document 10 → Pagina 10 |
| :---- |

Questo ci permette di conservare le informazioni relative alle pagine e successivamente associarle ai singoli chunk.

La documentazione ufficiale descrive proprio questo comportamento come standard.

**18\) Implementare il service layer “PdfProcessingService”**

Quindi implementiamo l’intero layer di astrazione per processare i file PDF dentro:

***service/PdfProcessingService.java***

| package com.example.chatbot.service;import org.springframework.ai.document.Document;import org.springframework.ai.reader.pdf.PagePdfDocumentReader;import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;import org.springframework.core.io.FileSystemResource;import org.springframework.stereotype.Service;import java.nio.file.Path;import java.util.ArrayList;import java.util.HashMap;import java.util.List;import java.util.Map;@Servicepublic class PdfProcessingService {    public List\<Document\> extractText(            Path pdfPath,            String storedFilename,            String originalFilename    ) {        PdfDocumentReaderConfig config \=                PdfDocumentReaderConfig.builder()                        .withPagesPerDocument(1)                        .build();        PagePdfDocumentReader reader \=                new PagePdfDocumentReader(                        new FileSystemResource(pdfPath),                        config                );        List\<Document\> pages \= reader.read();        List\<Document\> result \= new ArrayList\<\>();        for (Document page : pages) {            Map\<String, Object\> metadata \=                    new HashMap\<\>(page.getMetadata());            metadata.put("source", storedFilename);            metadata.put("original\_filename", originalFilename);            result.add(                new Document(page.getText(), metadata)            );        }        return result;    }} |
| :---- |

Vediamo ogni elemento step by step per comprendere questa implementazione.

La classe “**PdfDocumentReaderConfig**” permette di configurare il comportamento del lettore PDF. Infatti, se vediamo il metodo usato, ovvero il seguente:

| .withPagesPerDocument(1) |
| :---- |

Indica che vogliamo un oggetto “**Document**“ per ogni pagina di un PDF.

“**FileSystemResource**” rappresenta il file PDF salvato sul filesystem.

Utilizziamo questa classe perché il PDF si trova nella cartella “**documents/pdf**”, non dentro “**src/main/resources**”.

In seguito, il metodo:

| reader.read() |
| :---- |

Avvia la lettura del documento e restituisce il seguente tipo di dato:

| List\<Document\> |
| :---- |

Quindi ogni elemento contiene:

* Testo estratto.  
* Metadati.  
* Informazioni relative alla pagina.

**page.getText()** recupera il contenuto testuale della pagina.

**page.getMetadata()** recupera i metadati generati dal PDF Reader.

Aggiungiamo anche:

* **source**: nome univoco del PDF salvato.  
* **original\_filename**: nome originale del documento.

Questi metadati saranno fondamentali quando dovremo capire da quale PDF e da quale pagina proviene una risposta.

**19\) Stampare il testo estratto dai documenti nella console**

Per verificare che tutto funzioni, aggiungiamo temporaneamente questo codice alla fine del metodo “extractText” (prima del **return**) della classe PdfProcessingService:

| for (int i \= 0; i \< pages.size(); i++) {    Document page \= pages.get(i);    System.out.println("\\n==============================");    System.out.println("PAGINA: " \+ (i \+ 1));    System.out.println("==============================");    System.out.println(page.getText());    System.out.println("\\nMETADATI:");    System.out.println(page.getMetadata());} |
| :---- |

Otterrai un output simile a questo:

| \==============================PAGINA: 1\==============================Introduzione a Spring Boot.Spring Boot è un framework Javautilizzato per sviluppare applicazioni...METADATI:{  start\_page\_number=1,  end\_page\_number=1}\==============================PAGINA: 2\==============================Configurazione delle dipendenze...METADATI:{  start\_page\_number=2,  end\_page\_number=2} |
| :---- |

I nomi esatti dei metadati dipendono dall'implementazione del reader.

È proprio per questo che nel nostro codice li stampiamo invece di presumere che siano presenti in un formato particolare.

**20\) Eseguire un test**

A questo punto carica un PDF tramite l’interfaccia web sviluppata in React e verifica che il testo venga stampato nella console di Spring Boot.

**Attenzione**: “**PagePdfDocumentReader”** estrae testo dai PDF che contengono testo leggibile. Se il PDF è una scansione composta esclusivamente da immagini, avremo bisogno di una libreria che esegue OCR, per esempio con Tesseract o un servizio OCR esterno.

**21\) Implementare il Chunking del testo**

Ora che abbiamo ottenuto il testo del PDF dobbiamo suddividerlo in blocchi più piccoli.

Questa operazione si chiama **chunking**.

**Ma perché non utilizzare direttamente tutto il PDF?**

Immaginiamo di avere un manuale di 300 pagine. Non avrebbe senso inviare tutto il suo contenuto al modello linguistico ogni volta che un utente pone una domanda.

Avremmo una serie di problemi, tra cui:

* Consumo elevato di token.  
* Maggiore latenza.  
* Costi superiori.  
* Possibile superamento della finestra di contesto del modello.  
* Minore precisione nella selezione delle informazioni.

Quindi suddividiamo il testo e ogni chunk diventerà un elemento indipendente all’interno del Vector Store.

**22\) Implementare il TokenTextSplitter per eseguire il chunking del testo**

Spring AI mette a disposizione la classe utility **TokenTextSplitter**.

Questa classe suddivide i documenti in base al numero di token, cercando quando possibile di rispettare i confini delle frasi.

Nella fattispecie, possiamo configurare i seguenti parametri:

| Parametro | Significato |
| :---- | :---- |
| chunkSize | Dimensione target in token |
| minChunkSizeChars | Numero minimo di caratteri per un blocco |
| minChunkLengthToEmbed | Lunghezza minima per includere il blocco |
| maxNumChunks | Lunghezza minima per includere il blocco |
| keepSeparator | Mantiene i separatori |

Per il nostro esempio utilizziamo:

* 500 token per chunk.  
* 200 caratteri minimi per un blocco.  
* 20 caratteri minimi per l'indicizzazione.  
* 10.000 chunk massimi.

**Sono valori iniziali didattici**, non valori universali: andranno ottimizzati in base ai documenti e ai risultati della ricerca.

**23\) Implementare il metodo di chunking effettivo nella classe PdfProcessingService**

All’interno del file PdfProcessingService.java aggiungiamo questo import:

| import org.springframework.ai.transformer.splitter.TokenTextSplitter; |
| :---- |

E il seguente metodo:

| public List\<Document\> splitDocuments(        List\<Document\> documents) {    TokenTextSplitter splitter \=            TokenTextSplitter.builder()                    .withChunkSize(500)                    .withMinChunkSizeChars(200)                    .withMinChunkLengthToEmbed(20)                    .withMaxNumChunks(10000)                    .withKeepSeparator(true)                    .build();    return splitter.apply(documents);} |
| :---- |

Il metodo **apply()** prende la lista dei documenti originali e restituisce una nuova lista contenente i chunk.

**24\) Visualizzare tutti i chunk generati**

Temporaneamente, aggiungiamo questo frammento di codice:

| List\<Document\> chunks \= splitter.apply(documents);for (int i \= 0; i \< chunks.size(); i++) {    Document chunk \= chunks.get(i);    System.out.println("\\n==============================");    System.out.println("CHUNK: " \+ (i \+ 1));    System.out.println("==============================");    System.out.println(chunk.getText());    System.out.println("\\nMETADATI:");    System.out.println(chunk.getMetadata());}return chunks; |
| :---- |

Ad esempio, otterremo come output:

| \==============================CHUNK: 1\==============================Spring Boot è un framework Java.Permette di creare applicazioni...METADATI:{  source=550e8400-e29b-41d4-a716-446655440000.pdf,  original\_filename=manuale.pdf,  start\_page\_number=1,  end\_page\_number=1}\==============================CHUNK: 2\==============================Utilizza un sistema di configurazioneautomatica...METADATI:{  source=550e8400-e29b-41d4-a716-446655440000.pdf,  original\_filename=manuale.pdf,  start\_page\_number=1,  end\_page\_number=1} |
| :---- |

Nota bene come i metadati del documento originale vengono mantenuti nei chunk.

**Questo è importantissimo**: quando recupereremo un chunk, sapremo da quale PDF e da quale pagina proviene.

**Nota sulla versione:** in Spring AI 2.0.2 TokenTextSplitter non offre ancora la configurazione dell'overlap tra chunk. Questa funzionalità è stata introdotta nella release 2.1.0-RC1. Non utilizziamo quindi withChunkOverlap() negli esempi destinati alla versione 2.0.2.