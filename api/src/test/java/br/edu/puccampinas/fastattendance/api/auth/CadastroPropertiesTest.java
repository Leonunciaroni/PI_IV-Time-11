/*
 * Autores:
 * - João Pedro Panza Mainieri (RA 25006642)
 */
package br.edu.puccampinas.fastattendance.api.auth;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CadastroPropertiesTest {

    @Test
    void listaVaziaNaoPermiteNenhumProfessor() {
        assertFalse(new CadastroProperties(List.of()).permiteProfessor("matheus@puc.br"));
        assertFalse(new CadastroProperties(List.of("", "  ")).permiteProfessor("matheus@puc.br"));
    }

    @Test
    void emailCompletoPrecisaSerIgual() {
        CadastroProperties cadastro = new CadastroProperties(List.of(" Matheus@PUC.br "));

        assertTrue(cadastro.permiteProfessor("matheus@puc.br"));
        assertFalse(cadastro.permiteProfessor("outro.matheus@puc.br"));
        assertFalse(cadastro.permiteProfessor("matheus@puc.br.com"));
    }

    @Test
    void dominioAceitaQualquerEmailDoDominioExato() {
        CadastroProperties cadastro = new CadastroProperties(List.of("@puc-campinas.edu.br"));

        assertTrue(cadastro.permiteProfessor("clara@puc-campinas.edu.br"));
        assertFalse(cadastro.permiteProfessor("clara@falsapuc-campinas.edu.br"));
        assertFalse(cadastro.permiteProfessor("clara@puc-campinas.edu.br.evil.com"));
    }
}
