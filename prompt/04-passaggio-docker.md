Analizza l'intera codebase e converti tutta l'infrastruttura Docker in un'infrastruttura autonoma. 

Molto probabilmente Docker per ora conterrà solo:
- pgvector -> per il database vettoriale
- pgamdin4 -> per visualizzare l'area admin di PostgreSQL
- ollama -> che contiene il modello di embedding.

Però questa è solo una parte della configurazione, voglio che converti l'intero progetto in Docker. Quindi:
- implementare il database principale mariadb in un contenitore dedicato con tutte le credenziali
- se trovi driver mysql nel pom.xml, installa quello per mariadb e configura in modo appropriato l'application.properties
- MOTIVO: mysql è troppo arretrato, voglio accedere a un database più performante e versatile
- includi phpmyadmin in un contenitore dedicato per permettermi di visualizzare non solo i database postgres, ma anche quelli di mariadb
- infine, containerizza spring boot in un contenitore dedicato e il frontend

Regole:
- revisiona sempre tutte le configurazioni del backend nell'application.properties e nel backend
- esegui un avvio di test di Docker per intercettare eventuali errori

Documentazione:
- aggiorna sempre la documentazione nel file Readme.md per consentirmi di studiare le tue implemtazioni