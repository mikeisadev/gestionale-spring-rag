Implementa il seguente codice ripreso dal mio documento di lezione.

NOTA: non considerare gli altri documenti successivi, considerali solo in modo incrementale perché devo dimostrare ogni punto a livello didattico. Preoccupati solo dei punti da implementare se ti chiedo di implementare questo file, ignora quelli successivi o non menzionati. Ti dirò io come procedere o quale prompt ingaggiare nel prossimo lavoro. 

**13\) Caricamento di un PDF dal frontend**

Implementiamo un sistema di upload file per ottenere i documenti PDF da leggere dal frontend. 

Il flusso sarà il seguente:

| React  \|  \| POST multipart/form-data  vDocumentController  \|  vDocumentService  \|  vPdfProcessingService  \|  vPostgreSQL \+ pgvector |
| :---- |

Il frontend invierà il PDF al backend. Il backend lo salverà nella cartella documents/pdf, lo elaborerà e inserirà i chunk nel database.

**14\) Creazione del DTO di risposta**

Adesso aggiungiamo il DTO di risposta dentro ***dto/DocumentUploadResponse.java***:

| package com.example.chatbot.dto;public record DocumentUploadResponse(    String filename,    int pages,    int chunks,    String message) {} |
| :---- |

Questo DTO ci servirà per comunicare al frontend l’esito dell’indicizzazione del documento appena caricato.

**15\) Creazione del controller “DocumentController” per processare l’upload del file**

***PERCORSO: controller/DocumentController.java***

***NOTA: nel metodo “uploadPdf” usiamo “FilePart” di WebFlux e non “MultipartFile”.***

| package com.example.chatbot.controller;import com.example.chatbot.dto.DocumentUploadResponse;import com.example.chatbot.service.DocumentService;import org.springframework.http.MediaType;import org.springframework.http.HttpStatus;import org.springframework.http.codec.multipart.FilePart;import org.springframework.web.bind.annotation.\*;import reactor.core.publisher.Mono;import reactor.core.scheduler.Schedulers;import java.nio.file.Files;import java.nio.file.Path;import java.util.UUID;@RestController@RequestMapping("/api/documents")public class DocumentController {    private final DocumentService documentService;    private final Path uploadDirectory \=            Path.of("documents", "pdf");    public DocumentController(DocumentService documentService) {        this.documentService \= documentService;    }    @PostMapping(consumes \= MediaType.MULTIPART\_FORM\_DATA\_VALUE)    @ResponseStatus(HttpStatus.OK)    public Mono\<DocumentUploadResponse\> uploadPdf(            @RequestPart("file") FilePart file    ) {        String originalFilename \= file.filename();        if (originalFilename \== null \|\|                \!originalFilename.toLowerCase().endsWith(".pdf")) {            return Mono.error(                new IllegalArgumentException("Caricare un file PDF")            );        }        // Non utilizziamo il nome originale come percorso fisico.        String storedFilename \= UUID.randomUUID() \+ ".pdf";        Path destination \= uploadDirectory.resolve(storedFilename);        return Mono.fromCallable(() \-\>                    Files.createDirectories(uploadDirectory)                )                .subscribeOn(Schedulers.boundedElastic())                .then(file.transferTo(destination))                .then(                    Mono.fromCallable(() \-\>                        documentService.indexPdf(                            destination,                            storedFilename,                            originalFilename                        )                    ).subscribeOn(Schedulers.boundedElastic())                );    }} |
| :---- |

Vediamo cosa succede step by step:

1. @RequestPart("file") recupera il file inviato dal frontend.  
2. Verifichiamo che il nome termini con .pdf.  
3. Generiamo un nome univoco con UUID.  
4. Creiamo la cartella di destinazione.  
5. Salviamo il PDF.  
6. Avviamo il processo di indicizzazione.

**Curiosità tecnica: Perché utilizziamo Schedulers.boundedElastic() nel metodo uploadPdf?**

La lettura dei PDF, la generazione degli embeddings e le operazioni JDBC sono operazioni potenzialmente bloccanti.

Con WebFlux non dobbiamo eseguirle sul thread che gestisce le richieste HTTP. “**boundedElastic**” permette di spostare questo lavoro su un pool di thread adatto alle operazioni bloccanti.

In produzione aggiungeremo anche limiti di dimensione, verifica del MIME type, gestione degli errori e protezione degli endpoint tramite autenticazione.

**16\) Chiamata da React per l’upload del file**

Adesso, con l’assistenza del tuo LLM crea un nuovo “tab” all’interno del gestionale chiamato **“Upload file per sistema RAG”.**

Se vuoi portarti avanti con le funzionalità potresti rendere accessibile questa sezione solo gli Admin. Infatti, solo gli admin dovrebbero gestire questa sezione.

È fondamentale che questa sezione gestisca l’upload del file usando l’endpoint del controller appena creato.

Qui sotto ti riporto l’esempio di una semplice funzione fetch per eseguire l’upload di un ipotetico file, MA NON l’intero componente:

| async function uploadPdf(file) {    const formData \= new FormData();    formData.append("file", file);    const response \= await fetch(        "http\://localhost:8080/api/documents",        {            method: "POST",            body: formData        }    );    if (\!response.ok) {        throw new Error("Errore durante il caricamento");    }    const data \= await response.json();    console.log(data);    return data;} |
| :---- |

L’esempio di utilizzo di questa funzione potrebbe essere su un tag “input” di tipo “file”:

| \<input    type="file"    accept="application/pdf"    onChange={e \=\> {        const file \= e.target.files?.\[0\];        if (file) {            uploadPdf(file);        }    }}/\> |
| :---- |

Ma questi sono soltanto esempi di frammenti di codice. Te li ho riportati per farti degli esempi. Potrai farti assistere dal tuo modello di LLM preferito per implementare il tutto. 

**Esercitazione**: potresti chiedere al tuo LLM di implementare nella UI del tuo gestionale il resoconto di tutto l’upload. 