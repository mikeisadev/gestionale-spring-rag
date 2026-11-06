package com.gestionale.industriatech;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

// Avvia l'intera applicazione: servono MariaDB, PostgreSQL e Ollama (docker compose up -d)
@Tag("integration")
@SpringBootTest
class IndustriatechApplicationTests {

	@Test
	void contextLoads() {
	}

}
