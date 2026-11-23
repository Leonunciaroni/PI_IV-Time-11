/*
 * Autores:
 * - Lucas Leonel (RA 25015188)
 */
package br.edu.puccampinas.fastattendance.api.seguranca;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/** Responde 403 em formato Problem Details (RFC 9457) quando o papel do usuário não permite a rota. */
@Component
public class RespostaAcessoNegado implements AccessDeniedHandler {

    @Override
    public void handle(HttpServletRequest requisicao, HttpServletResponse resposta,
                       AccessDeniedException excecao) throws IOException {
        resposta.setStatus(HttpStatus.FORBIDDEN.value());
        resposta.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        resposta.setCharacterEncoding("UTF-8");
        resposta.getWriter().write("""
                {"type":"about:blank","title":"Forbidden","status":403,\
                "detail":"Seu perfil não tem acesso a este recurso."}""");
    }
}
