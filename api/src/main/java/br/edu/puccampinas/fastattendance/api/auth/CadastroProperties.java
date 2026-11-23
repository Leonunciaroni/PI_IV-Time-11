/*
 * Autores:
 * - João Pedro Panza Mainieri (RA 25006642)
 */
package br.edu.puccampinas.fastattendance.api.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;
import java.util.Locale;

/**
 * Quem pode criar conta de professor. O cadastro de alunos é aberto.
 *
 * @param professoresPermitidos variável de ambiente {@code PROFESSORES_PERMITIDOS}, separada por
 *                              vírgulas: e-mails completos ({@code matheus@puc.br}) ou domínios
 *                              iniciados por {@code @} ({@code @puc-campinas.edu.br}). Vazia, nenhum
 *                              professor pode se cadastrar pela API.
 */
@ConfigurationProperties("fastattendance.cadastro")
public record CadastroProperties(@DefaultValue List<String> professoresPermitidos) {

    /** {@code email} já deve vir normalizado em minúsculas. */
    public boolean permiteProfessor(String email) {
        for (String entrada : professoresPermitidos) {
            String permitido = entrada.strip().toLowerCase(Locale.ROOT);
            if (permitido.isEmpty()) {
                continue;
            }
            boolean confere = permitido.startsWith("@") ? email.endsWith(permitido) : email.equals(permitido);
            if (confere) {
                return true;
            }
        }
        return false;
    }
}
