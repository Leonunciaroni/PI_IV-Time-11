package br.edu.puccampinas.fastattendance.api.turma;

import br.edu.puccampinas.fastattendance.api.erro.RequisicaoInvalidaException;
import br.edu.puccampinas.fastattendance.api.turma.LeitorCsvAlunos.LinhaAluno;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LeitorCsvAlunosTest {

    final LeitorCsvAlunos leitor = new LeitorCsvAlunos();

    @Test
    void leArquivoComVirgula() {
        List<LinhaAluno> linhas = ler("nome,email,matricula\nAna Souza,ana@puc.br,24001234\nBia Lima,bia@puc.br,24005678\n");

        assertEquals(2, linhas.size());
        assertEquals(new LinhaAluno(2, "Ana Souza", "ana@puc.br", "24001234"), linhas.get(0));
        assertEquals(3, linhas.get(1).numero());
    }

    @Test
    void leArquivoDoExcelComPontoEVirgulaEBom() {
        List<LinhaAluno> linhas = ler("﻿Nome;E-mail;Matrícula\r\nAna Souza;ana@puc.br;24001234\r\n");

        assertEquals(List.of(new LinhaAluno(2, "Ana Souza", "ana@puc.br", "24001234")), linhas);
    }

    @Test
    void aceitaColunasEmOutraOrdemEIgnoraLinhasEmBranco() {
        List<LinhaAluno> linhas = ler("matricula,nome,email\n\n24001234, Ana Souza ,ana@puc.br\n");

        assertEquals("Ana Souza", linhas.get(0).nome());
        assertEquals("24001234", linhas.get(0).matricula());
    }

    @Test
    void campoEntreAspasPodeTerVirgula() {
        List<LinhaAluno> linhas = ler("nome,email,matricula\n\"Souza, Ana\",ana@puc.br,24001234\n");

        assertEquals("Souza, Ana", linhas.get(0).nome());
    }

    @Test
    void colunaAusenteNaLinhaViraTextoVazio() {
        List<LinhaAluno> linhas = ler("nome,email,matricula\nAna Souza,ana@puc.br\n");

        assertEquals("", linhas.get(0).matricula());
    }

    @Test
    void rejeitaArquivoVazio() {
        assertThrows(RequisicaoInvalidaException.class, () -> ler(""));
        assertThrows(RequisicaoInvalidaException.class, () -> ler("  \n\n"));
    }

    @Test
    void rejeitaCabecalhoSemAsColunasEsperadas() {
        RequisicaoInvalidaException erro = assertThrows(RequisicaoInvalidaException.class,
                () -> ler("nome,email\nAna,ana@puc.br\n"));

        assertEquals("O cabeçalho precisa ter as colunas nome, email e matricula.", erro.getMessage());
    }

    @Test
    void aspaSoltaNoMeioDoCampoViraErroDeRequisicao() {
        RequisicaoInvalidaException erro = assertThrows(RequisicaoInvalidaException.class,
                () -> ler("nome,email,matricula\nAna \"Aninha\" Souza,ana@demo.br,24001234\n\"Bia\" Lima,bia@puc.br,1\n"));

        assertEquals(LeitorCsvAlunos.ARQUIVO_ILEGIVEL, erro.getMessage());
    }

    @Test
    void rejeitaArquivoComLinhasDemais() {
        StringBuilder texto = new StringBuilder("nome,email,matricula\n");
        for (int i = 0; i <= LeitorCsvAlunos.MAXIMO_LINHAS; i++) {
            texto.append("Aluno,a").append(i).append("@puc.br,").append(i).append('\n');
        }

        assertThrows(RequisicaoInvalidaException.class, () -> ler(texto.toString()));
    }

    private List<LinhaAluno> ler(String texto) {
        return leitor.ler(new ByteArrayInputStream(texto.getBytes(StandardCharsets.UTF_8)));
    }
}
