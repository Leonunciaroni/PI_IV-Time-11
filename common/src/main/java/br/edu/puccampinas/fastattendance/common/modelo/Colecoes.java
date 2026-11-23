/*
 * Autores:
 * - João Pedro Panza Mainieri (RA 25006642)
 */
package br.edu.puccampinas.fastattendance.common.modelo;

/**
 * Nomes das coleções do MongoDB, compartilhados entre a API (Spring Data) e o servidor de sessão
 * (driver nativo). O esquema está descrito em docs/modelo-de-dados.md.
 */
public final class Colecoes {

    public static final String USUARIOS = "usuarios";
    public static final String TURMAS = "turmas";
    public static final String MATRICULAS = "matriculas";
    public static final String SALAS = "salas";
    public static final String SESSOES = "sessoes";
    public static final String PRESENCAS = "presencas";

    private Colecoes() {
    }
}
