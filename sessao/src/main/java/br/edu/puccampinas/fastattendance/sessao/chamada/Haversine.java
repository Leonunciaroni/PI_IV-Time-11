/*
 * Autores:
 * - João Pedro Panza Mainieri (RA 25006642)
 */
package br.edu.puccampinas.fastattendance.sessao.chamada;

/**
 * Distância entre dois pontos da superfície terrestre pela fórmula de Haversine, usada na
 * validação de raio do modo QR Code (docs/protocolo-v1.md, seção 7, {@code FORA_DO_RAIO}).
 * <p>
 * Considera a Terra como esfera de raio médio; o erro frente ao elipsoide é de até 0,5%, o que é
 * irrelevante para raios de sala da ordem de dezenas de metros.
 */
public final class Haversine {

    /** Raio médio da Terra, em metros. */
    static final double RAIO_TERRA_METROS = 6_371_000.0;

    private Haversine() {
    }

    /**
     * Distância em metros entre dois pontos informados em graus decimais.
     */
    public static double metros(double latitude1, double longitude1, double latitude2, double longitude2) {
        double phi1 = Math.toRadians(latitude1);
        double phi2 = Math.toRadians(latitude2);
        double deltaPhi = Math.toRadians(latitude2 - latitude1);
        double deltaLambda = Math.toRadians(longitude2 - longitude1);

        double a = Math.sin(deltaPhi / 2) * Math.sin(deltaPhi / 2)
                + Math.cos(phi1) * Math.cos(phi2) * Math.sin(deltaLambda / 2) * Math.sin(deltaLambda / 2);
        // Em ponto flutuante, a pode ultrapassar 1 por uma fração ínfima (pontos antípodas)
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(Math.max(0, 1 - a)));
        return RAIO_TERRA_METROS * c;
    }
}
