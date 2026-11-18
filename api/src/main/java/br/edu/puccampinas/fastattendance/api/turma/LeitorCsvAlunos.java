package br.edu.puccampinas.fastattendance.api.turma;

import br.edu.puccampinas.fastattendance.api.erro.RequisicaoInvalidaException;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Lê a lista de alunos de um CSV com as colunas nome, e-mail e matrícula (em qualquer ordem, com
 * cabeçalho na primeira linha). Aceita vírgula ou ponto e vírgula, que é o que o Excel em português grava.
 */
public class LeitorCsvAlunos {

    /** Limite de linhas por arquivo, para uma importação não prender a API. */
    public static final int MAXIMO_LINHAS = 2000;

    static final String ARQUIVO_ILEGIVEL =
            "Não foi possível ler o arquivo como CSV. Confira as aspas e o separador.";

    /** Uma linha lida do arquivo; {@code numero} é a posição no arquivo, contando o cabeçalho como 1. */
    public record LinhaAluno(int numero, String nome, String email, String matricula) {
    }

    public List<LinhaAluno> ler(InputStream entrada) {
        String conteudo = lerTexto(entrada);
        if (conteudo.isBlank()) {
            throw new RequisicaoInvalidaException("O arquivo está vazio.");
        }

        char separador = detectarSeparador(conteudo);
        CSVFormat formato = CSVFormat.DEFAULT.builder()
                .setDelimiter(separador)
                .setIgnoreEmptyLines(true)
                .setTrim(true)
                .build();

        try (CSVParser leitor = formato.parse(new StringReader(conteudo))) {
            List<CSVRecord> registros = leitor.getRecords();
            if (registros.isEmpty()) {
                throw new RequisicaoInvalidaException("O arquivo está vazio.");
            }
            int colunaNome = coluna(registros.get(0), "nome");
            int colunaEmail = coluna(registros.get(0), "email", "e-mail");
            int colunaMatricula = coluna(registros.get(0), "matricula", "ra");
            if (colunaNome < 0 || colunaEmail < 0 || colunaMatricula < 0) {
                throw new RequisicaoInvalidaException(
                        "O cabeçalho precisa ter as colunas nome, email e matricula.");
            }
            if (registros.size() - 1 > MAXIMO_LINHAS) {
                throw new RequisicaoInvalidaException("O arquivo passa de " + MAXIMO_LINHAS + " linhas.");
            }

            List<LinhaAluno> linhas = new ArrayList<>();
            for (CSVRecord registro : registros.subList(1, registros.size())) {
                linhas.add(new LinhaAluno((int) registro.getRecordNumber(),
                        valor(registro, colunaNome), valor(registro, colunaEmail),
                        valor(registro, colunaMatricula)));
            }
            return linhas;
        } catch (IOException | UncheckedIOException | IllegalArgumentException e) {
            // Aspas fora do lugar fazem o parser lançar UncheckedIOException ao ler os registros
            throw new RequisicaoInvalidaException(ARQUIVO_ILEGIVEL);
        }
    }

    private static String lerTexto(InputStream entrada) {
        try (BufferedReader leitor = new BufferedReader(new InputStreamReader(entrada, StandardCharsets.UTF_8))) {
            StringBuilder texto = new StringBuilder();
            char[] bloco = new char[4096];
            int lidos;
            while ((lidos = leitor.read(bloco)) != -1) {
                texto.append(bloco, 0, lidos);
            }
            // O Excel grava um marcador (BOM) no começo do arquivo UTF-8
            if (texto.length() > 0 && texto.charAt(0) == '﻿') {
                texto.deleteCharAt(0);
            }
            return texto.toString();
        } catch (IOException e) {
            throw new RequisicaoInvalidaException("Não foi possível ler o arquivo.");
        }
    }

    private static char detectarSeparador(String conteudo) {
        String cabecalho = conteudo.lines().filter(l -> !l.isBlank()).findFirst().orElse("");
        long pontoEVirgula = cabecalho.chars().filter(c -> c == ';').count();
        long virgulas = cabecalho.chars().filter(c -> c == ',').count();
        return pontoEVirgula > virgulas ? ';' : ',';
    }

    private static int coluna(CSVRecord cabecalho, String... nomes) {
        for (int i = 0; i < cabecalho.size(); i++) {
            String atual = semAcento(cabecalho.get(i)).strip().toLowerCase(Locale.ROOT);
            for (String nome : nomes) {
                if (atual.equals(nome)) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static String valor(CSVRecord registro, int coluna) {
        return coluna < registro.size() ? registro.get(coluna).strip() : "";
    }

    private static String semAcento(String texto) {
        return Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }
}
