/*
 * Autores:
 * - João Pedro Panza Mainieri (RA 25006642)
 */
package br.edu.puccampinas.fastattendance.common.seguranca;

import br.edu.puccampinas.fastattendance.common.modelo.Papel;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Objects;

/**
 * Emite e valida os JWTs usados pela API (header {@code Authorization}) e pelo servidor de sessão
 * (mensagem {@code auth}). Os dois módulos compartilham o mesmo segredo HMAC-SHA256.
 */
public final class JwtService {

    public static final String EMISSOR = "fast-attendance";
    /** HS256 exige chave de pelo menos 256 bits. */
    public static final int TAMANHO_MINIMO_SEGREDO = 32;

    private static final String CLAIM_NOME = "nome";
    private static final String CLAIM_PAPEL = "papel";

    private final SecretKey chave;
    private final Duration validade;
    private final Clock relogio;
    private final JwtParser parser;

    public JwtService(String segredo, Duration validade) {
        this(segredo, validade, Clock.systemUTC());
    }

    public JwtService(String segredo, Duration validade, Clock relogio) {
        Objects.requireNonNull(segredo, "segredo do JWT não configurado");
        byte[] bytes = segredo.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < TAMANHO_MINIMO_SEGREDO) {
            throw new IllegalArgumentException(
                    "O segredo do JWT deve ter pelo menos " + TAMANHO_MINIMO_SEGREDO + " bytes");
        }
        if (validade.isZero() || validade.isNegative()) {
            throw new IllegalArgumentException("A validade do JWT deve ser positiva");
        }
        this.chave = Keys.hmacShaKeyFor(bytes);
        this.validade = validade;
        this.relogio = relogio;
        this.parser = Jwts.parser()
                .verifyWith(chave)
                .requireIssuer(EMISSOR)
                .clock(() -> Date.from(relogio.instant()))
                .build();
    }

    public TokenEmitido emitir(String usuarioId, String nome, Papel papel) {
        Instant agora = relogio.instant();
        Instant expiraEm = agora.plus(validade);
        String token = Jwts.builder()
                .issuer(EMISSOR)
                .subject(usuarioId)
                .claim(CLAIM_NOME, nome)
                .claim(CLAIM_PAPEL, papel.name())
                .issuedAt(Date.from(agora))
                .expiration(Date.from(expiraEm))
                .signWith(chave, Jwts.SIG.HS256)
                .compact();
        return new TokenEmitido(token, expiraEm);
    }

    public UsuarioAutenticado validar(String token) throws TokenInvalidoException {
        if (token == null || token.isBlank()) {
            throw new TokenInvalidoException("Token ausente", null);
        }
        try {
            Claims claims = parser.parseSignedClaims(token).getPayload();
            String id = claims.getSubject();
            if (id == null || id.isBlank()) {
                throw new TokenInvalidoException("Token sem subject", null);
            }
            Papel papel = Papel.valueOf(claims.get(CLAIM_PAPEL, String.class));
            return new UsuarioAutenticado(id, claims.get(CLAIM_NOME, String.class), papel,
                    claims.getExpiration().toInstant());
        } catch (JwtException | IllegalArgumentException | NullPointerException e) {
            throw new TokenInvalidoException("Token inválido: " + e.getMessage(), e);
        }
    }
}
