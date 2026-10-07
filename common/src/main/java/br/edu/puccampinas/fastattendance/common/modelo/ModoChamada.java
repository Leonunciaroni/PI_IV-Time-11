package br.edu.puccampinas.fastattendance.common.modelo;

/** Modo de confirmação de presença escolhido pelo professor ao abrir a aula. */
public enum ModoChamada {
    /** Marcação manual por lista, com marcação em lote. */
    CLASSICA,
    /** Token TOTP exibido em QR Code rotativo, com validação de geolocalização. */
    QRCODE,
    /** Token TOTP transmitido em onda sonora na faixa ultrassônica. */
    SOM
}
