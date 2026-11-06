package com.gestionale.industriatech.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;

import com.gestionale.industriatech.entity.DocumentoIndicizzato;
import com.gestionale.industriatech.repository.DocumentoIndicizzatoRepository;
import com.gestionale.industriatech.repository.LogIndicizzazioneRepository;

/**
 * LIVELLO 1 (lezione 10): ingestione dei documenti.
 * Nessun servizio esterno: i reader lavorano su file temporanei, il database è un mock.
 */
class DocumentIngestionTest {

    @TempDir
    Path folder;

    // ===== L'impronta SHA-256 =====

    @Test
    void lHashDiUnContenutoNotoEQuelloStandard() {
        // Valore di riferimento ufficiale di SHA-256 per il testo "abc"
        assertThat(DocumentCatalogService.sha256(new ByteArrayInputStream("abc".getBytes(StandardCharsets.UTF_8))))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void stessoContenutoStessoHashAncheConNomiDiversi() throws Exception {
        Path a = Files.writeString(folder.resolve("regolamento.txt"), "Ferie: 26 giorni");
        Path b = Files.writeString(folder.resolve("copia di regolamento.txt"), "Ferie: 26 giorni");
        Path c = Files.writeString(folder.resolve("regolamento-2027.txt"), "Ferie: 27 giorni");

        assertThat(DocumentCatalogService.sha256(a)).isEqualTo(DocumentCatalogService.sha256(b));
        assertThat(DocumentCatalogService.sha256(a)).isNotEqualTo(DocumentCatalogService.sha256(c));
    }

    // ===== I formati accettati =====

    @Test
    void siAccettanoSoloLeEstensioniDellaLista() {
        assertThat(DocumentReaderService.isSupported("Regolamento Ferie.DOCX")).isTrue();
        assertThat(DocumentReaderService.isSupported("note.md")).isTrue();
        assertThat(DocumentReaderService.isSupported("virus.exe")).isFalse();
        assertThat(DocumentReaderService.isSupported("manuale.pdf.exe")).isFalse(); // conta l'ULTIMA estensione
        assertThat(DocumentReaderService.isSupported("senza-estensione")).isFalse();
        assertThat(DocumentReaderService.isSupported(null)).isFalse();
    }

    // ===== I reader di Spring AI =====

    private final DocumentReaderService readers = new DocumentReaderService();

    @Test
    void ilReaderGiustoPerOgniFormato() throws Exception {
        Path txt = Files.writeString(folder.resolve("a.txt"), "testo");
        Path md = Files.writeString(folder.resolve("a.md"), "# Titolo");
        Path docx = folder.resolve("a.docx");

        assertThat(readers.readerName(txt)).isEqualTo("TextReader");
        assertThat(readers.readerName(md)).isEqualTo("MarkdownDocumentReader");
        assertThat(readers.readerName(docx)).isEqualTo("TikaDocumentReader");
    }

    @Test
    void unFileWordVieneLettoDaTika() throws Exception {
        Path docx = folder.resolve("regolamento.docx");
        try (XWPFDocument word = new XWPFDocument(); OutputStream out = Files.newOutputStream(docx)) {
            word.createParagraph().createRun().setText("Ogni dipendente ha diritto a 26 giorni di ferie.");
            word.createParagraph().createRun().setText("Le ferie vanno richieste con 15 giorni di anticipo.");
            word.write(out);
        }

        List<Document> documents = readers.read(docx);

        assertThat(documents).hasSize(1); // Tika restituisce un unico Document con tutto il testo
        assertThat(documents.getFirst().getText())
                .contains("26 giorni di ferie")
                .contains("15 giorni di anticipo");
    }

    @Test
    void unFileMarkdownVieneDivisoPerTitoli() throws Exception {
        Path md = Files.writeString(folder.resolve("faq.md"), """
                # Ferie
                Ogni dipendente ha diritto a 26 giorni di ferie.

                # Trasferte
                Il rimborso chilometrico è di 0,42 euro al km.
                """);

        List<Document> documents = readers.read(md);

        assertThat(documents).hasSize(2);
        assertThat(documents.get(1).getText()).contains("0,42 euro");
        assertThat(documents.get(1).getMetadata()).containsEntry("title", "Trasferte");
    }

    @Test
    void ogniSezioneRiceveDocumentIdEVersione() throws Exception {
        Path txt = Files.writeString(folder.resolve("x.txt"), "Il rimborso chilometrico è di 0,42 euro al km.");
        var processing = new PdfProcessingService(null, readers);

        List<Document> sections = processing.extractText(txt, "x.txt", "rimborsi.txt", "doc-123", 2);

        assertThat(sections.getFirst().getMetadata())
                .containsEntry("document_id", "doc-123")
                .containsEntry("version", 2)
                .containsEntry("source", "x.txt")
                .containsEntry("original_filename", "rimborsi.txt");
    }

    @Test
    void senzaPaginaIlTagDocumentoNonHaLAttributoPagina() {
        var ragService = new RagService(null);
        var doc = new Document("Ferie: 26 giorni.", Map.of("original_filename", "regolamento.docx"));

        assertThat(ragService.formatAsContext(List.of(doc)))
                .startsWith("<documento fonte=\"regolamento.docx\">")
                .doesNotContain("pagina=");
    }

    // ===== Il catalogo: doppioni e versioni =====

    private final DocumentoIndicizzatoRepository repository = mock(DocumentoIndicizzatoRepository.class);
    private final DocumentCatalogService catalog = new DocumentCatalogService(
            repository, mock(LogIndicizzazioneRepository.class), mock(VectorStore.class), "documents/pdf");

    private static DocumentoIndicizzato existing(String name, String status, int version) {
        var d = new DocumentoIndicizzato();
        d.setId("upload-" + version);
        d.setDocumentoId("doc-1");
        d.setNomeOriginale(name);
        d.setStato(status);
        d.setVersione(version);
        d.setCaricatoIl(LocalDateTime.of(2026, 11, 6, 10, 30));
        return d;
    }

    @Test
    void unFileGiaCaricatoVieneRifiutato() {
        when(repository.findByHashSha256AndStatoIn(anyString(), any()))
                .thenReturn(List.of(existing("regolamento.pdf", "COMPLETATO", 1)));

        // Anche con un nome diverso: conta il contenuto
        assertThatThrownBy(() -> catalog.plan("hash", "copia di regolamento.pdf"))
                .isInstanceOf(DocumentConflictException.class)
                .hasMessageContaining("già nella base di conoscenza")
                .hasMessageContaining("regolamento.pdf");
    }

    @Test
    void stessoNomeContenutoDiversoENuovaVersione() {
        when(repository.findByHashSha256AndStatoIn(anyString(), any())).thenReturn(List.of());
        when(repository.findByNomeOriginaleIgnoreCaseAndStatoIn(anyString(), any()))
                .thenReturn(List.of(existing("Regolamento.pdf", "COMPLETATO", 2)));

        var plan = catalog.plan("nuovo-hash", "regolamento.pdf");

        assertThat(plan.isNewVersion()).isTrue();
        assertThat(plan.documentId()).isEqualTo("doc-1"); // stesso ID stabile
        assertThat(plan.version()).isEqualTo(3);
    }

    @Test
    void unNuovoNomeENuovoDocumento() {
        when(repository.findByHashSha256AndStatoIn(anyString(), any())).thenReturn(List.of());
        when(repository.findByNomeOriginaleIgnoreCaseAndStatoIn(anyString(), any())).thenReturn(List.of());

        var plan = catalog.plan("hash", "nuovo.pdf");

        assertThat(plan.isNewVersion()).isFalse();
        assertThat(plan.version()).isEqualTo(1);
        assertThat(plan.documentId()).hasSize(36); // un nuovo UUID
    }

    @Test
    void nonSiCaricaUnaVersioneMentreLaPrecedenteEInElaborazione() {
        when(repository.findByHashSha256AndStatoIn(anyString(), any())).thenReturn(List.of());
        when(repository.findByNomeOriginaleIgnoreCaseAndStatoIn(anyString(), any()))
                .thenReturn(List.of(existing("regolamento.pdf", "IN_ELABORAZIONE", 1)));

        assertThatThrownBy(() -> catalog.plan("hash", "regolamento.pdf"))
                .isInstanceOf(DocumentConflictException.class)
                .hasMessageContaining("caricamento in corso");
    }
}
