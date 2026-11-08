package br.edu.puccampinas.fastattendance.api.seguranca;

import br.edu.puccampinas.fastattendance.common.seguranca.JwtService;
import br.edu.puccampinas.fastattendance.common.seguranca.TokenInvalidoException;
import br.edu.puccampinas.fastattendance.common.seguranca.UsuarioAutenticado;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Autentica a requisição a partir de {@code Authorization: Bearer <jwt>}. O principal é um
 * {@link UsuarioAutenticado}, com autoridade {@code ROLE_PROFESSOR} ou {@code ROLE_ALUNO}.
 * Tokens inválidos não autenticam e a requisição cai no 401 das rotas protegidas.
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String PREFIXO = "Bearer ";

    private final JwtService jwt;

    public JwtAuthFilter(JwtService jwt) {
        this.jwt = jwt;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest requisicao, HttpServletResponse resposta, FilterChain cadeia)
            throws ServletException, IOException {
        String cabecalho = requisicao.getHeader(HttpHeaders.AUTHORIZATION);
        if (cabecalho != null && cabecalho.startsWith(PREFIXO)) {
            try {
                UsuarioAutenticado usuario = jwt.validar(cabecalho.substring(PREFIXO.length()));
                var autenticacao = new UsernamePasswordAuthenticationToken(usuario, null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + usuario.papel().name())));
                SecurityContextHolder.getContext().setAuthentication(autenticacao);
            } catch (TokenInvalidoException e) {
                SecurityContextHolder.clearContext();
            }
        }
        cadeia.doFilter(requisicao, resposta);
    }
}
