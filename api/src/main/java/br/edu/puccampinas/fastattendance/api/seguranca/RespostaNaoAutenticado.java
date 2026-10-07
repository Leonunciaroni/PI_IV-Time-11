package br.edu.puccampinas.fastattendance.api.seguranca;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/** Responde 401 em formato Problem Details (RFC 9457) quando falta um JWT válido. */
@Component
public class RespostaNaoAutenticado implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest requisicao, HttpServletResponse resposta,
                         AuthenticationException excecao) throws IOException {
        resposta.setStatus(HttpStatus.UNAUTHORIZED.value());
        resposta.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        resposta.setCharacterEncoding("UTF-8");
        resposta.getWriter().write("""
                {"type":"about:blank","title":"Unauthorized","status":401,\
                "detail":"Autenticação necessária: envie um JWT válido no header Authorization."}""");
    }
}
