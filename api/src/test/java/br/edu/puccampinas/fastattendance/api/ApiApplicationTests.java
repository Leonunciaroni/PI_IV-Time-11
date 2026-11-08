package br.edu.puccampinas.fastattendance.api;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(MongoTestcontainers.class)
class ApiApplicationTests {

    @Test
    void contextoSobe() {
    }
}
