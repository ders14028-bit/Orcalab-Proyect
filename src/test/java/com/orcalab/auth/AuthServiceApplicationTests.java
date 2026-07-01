package com.orcalab.auth;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class AuthServiceApplicationTests {

    @Test
    void contextLoads() {
        // Verifica que el contexto de Spring levanta correctamente.
        // Requiere una base de datos disponible (ver application-test.yml
        // o configurar H2 en memoria si prefieres no depender de Postgres para tests).
    }
}
