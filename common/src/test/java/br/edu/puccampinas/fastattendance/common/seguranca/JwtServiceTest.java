/*
 * Autores:
 * - João Pedro Panza Mainieri (RA 25006642)
 */
package br.edu.puccampinas.fastattendance.common.seguranca;

import br.edu.puccampinas.fastattendance.common.modelo.Papel;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    private static final String SEGREDO = "segredo-de-teste-com-mais-de-32-bytes-123456";
    private static final Instant AGORA = Instant.parse("2026-10-07T12:00:00Z");

    private final JwtService jwt = new JwtService(SEGREDO, Duration.ofHours(12), relogio(AGORA));

    @Test
    void tokenEmitidoEhValidadoComOsMesmosDados() throws Exception {
        TokenEmitido emitido = jwt.emitir("66f1b9ff", "Ana Souza", Papel.ALUNO);

        UsuarioAutenticado usuario = jwt.validar(emitido.token());

        assertEquals("66f1b9ff", usuario.id());
        assertEquals("Ana Souza", usuario.nome());
        assertEquals(Papel.ALUNO, usuario.papel());
        assertEquals(AGORA.plus(Duration.ofHours(12)), usuario.expiraEm());
        assertEquals(usuario.expiraEm(), emitido.expiraEm());
    }

    @Test
    void rejeitaTokenExpirado() {
        TokenEmitido emitido = jwt.emitir("1", "Prof. Matheus", Papel.PROFESSOR);
        JwtService depois = new JwtService(SEGREDO, Duration.ofHours(12),
                relogio(AGORA.plus(Duration.ofHours(13))));

        assertThrows(TokenInvalidoException.class, () -> depois.validar(emitido.token()));
    }

    @Test
    void rejeitaTokenAssinadoComOutroSegredo() {
        JwtService outro = new JwtService("outro-segredo-tambem-com-mais-de-32-bytes!!", Duration.ofHours(1),
                relogio(AGORA));
        String token = outro.emitir("1", "Intruso", Papel.PROFESSOR).token();

        assertThrows(TokenInvalidoException.class, () -> jwt.validar(token));
    }

    @Test
    void rejeitaTokenAdulterado() {
        String token = jwt.emitir("1", "Ana", Papel.ALUNO).token();
        String[] partes = token.split("\\.");
        // Troca o payload por outro que promove o usuário a professor
        String payloadFalso = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
                "{\"iss\":\"fast-attendance\",\"sub\":\"1\",\"papel\":\"PROFESSOR\"}".getBytes());
        String adulterado = partes[0] + "." + payloadFalso + "." + partes[2];

        assertThrows(TokenInvalidoException.class, () -> jwt.validar(adulterado));
    }

    @Test
    void rejeitaTokenAusenteOuMalformado() {
        assertThrows(TokenInvalidoException.class, () -> jwt.validar(null));
        assertThrows(TokenInvalidoException.class, () -> jwt.validar(""));
        assertThrows(TokenInvalidoException.class, () -> jwt.validar("nao.e.jwt"));
    }

    @Test
    void exigeSegredoDePeloMenos32Bytes() {
        assertThrows(IllegalArgumentException.class, () -> new JwtService("curto", Duration.ofHours(1)));
    }

    private static Clock relogio(Instant instante) {
        return Clock.fixed(instante, ZoneOffset.UTC);
    }
}
