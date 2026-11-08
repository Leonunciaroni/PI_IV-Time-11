package br.edu.puccampinas.fastattendance.sessao.chamada;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HaversineTest {

    /** Metade da circunferência de um círculo máximo: pi * R, com R = 6 371 000 m. */
    private static final double MEIA_CIRCUNFERENCIA = Math.PI * Haversine.RAIO_TERRA_METROS;

    @Test
    void mesmoPontoTemDistanciaZero() {
        assertEquals(0.0, Haversine.metros(-22.8339, -47.0525, -22.8339, -47.0525), 1e-9);
    }

    @Test
    void umGrauDeLatitudeNoEquadorMede111195Metros() {
        // 1 grau sobre um meridiano = pi/180 * R
        assertEquals(111_194.93, Haversine.metros(0, 0, 1, 0), 0.01);
    }

    @Test
    void umGrauDeLongitudeNoEquadorMede111195Metros() {
        assertEquals(111_194.93, Haversine.metros(0, 0, 0, 1), 0.01);
    }

    @Test
    void poloAoEquadorMedeUmQuartoDaCircunferencia() {
        assertEquals(MEIA_CIRCUNFERENCIA / 2, Haversine.metros(0, 0, 90, 0), 0.01);
    }

    @Test
    void pontosAntipodasMedemMeiaCircunferencia() {
        // Perto do antípoda, a raiz de (1 - a) perde precisão em ponto flutuante: tolerância de 1 m
        assertEquals(MEIA_CIRCUNFERENCIA, Haversine.metros(0, 0, 0, 180), 1.0);
        assertEquals(MEIA_CIRCUNFERENCIA, Haversine.metros(10, 20, -10, -160), 1.0);
    }

    @Test
    void ladoDaDistanciaNaoImporta() {
        double ida = Haversine.metros(-22.8339, -47.0525, -22.8300, -47.0500);
        double volta = Haversine.metros(-22.8300, -47.0500, -22.8339, -47.0525);
        assertEquals(ida, volta, 1e-6);
    }

    @Test
    void deslocamentoDeUmDecimoMilesimoDeGrauEstaNaFaixaDeUmaSala() {
        // 0,0001 grau de latitude: aproximadamente 11,12 m
        assertEquals(11.12, Haversine.metros(-22.8339, -47.0525, -22.8338, -47.0525), 0.01);
    }
}
