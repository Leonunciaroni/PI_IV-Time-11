package br.edu.puccampinas.fastattendance.common.comunicado;

import br.edu.puccampinas.fastattendance.common.modelo.OrigemPresenca;

/** Aluno -> servidor: confirma presença pelo QR Code ou pelo som (tipo checkin). Resposta: RespostaDeCheckin ou Erro. */
public class PedidoDeCheckin extends Comunicado
{
    private String sessaoId;
    private String token;
    private OrigemPresenca canal;
    private String dispositivoId;
    private Double latitude;
    private Double longitude;
    private Double precisaoMetros;

    public PedidoDeCheckin (String sessaoId, String token, OrigemPresenca canal, String dispositivoId, Double latitude, Double longitude, Double precisaoMetros)
    {
        this.sessaoId = sessaoId;
        this.token = token;
        this.canal = canal;
        this.dispositivoId = dispositivoId;
        this.latitude = latitude;
        this.longitude = longitude;
        this.precisaoMetros = precisaoMetros;
    }

    public String getSessaoId ()
    {
        return this.sessaoId;
    }

    public String getToken ()
    {
        return this.token;
    }

    public OrigemPresenca getCanal ()
    {
        return this.canal;
    }

    public String getDispositivoId ()
    {
        return this.dispositivoId;
    }

    public Double getLatitude ()
    {
        return this.latitude;
    }

    public Double getLongitude ()
    {
        return this.longitude;
    }

    public Double getPrecisaoMetros ()
    {
        return this.precisaoMetros;
    }

    public String toString ()
    {
        return "PedidoDeCheckin[" + "sessaoId=" + this.sessaoId + ", " + "token=" + "***" + ", " + "canal=" + this.canal + ", " + "dispositivoId=" + this.dispositivoId + ", " + "latitude=" + this.latitude + ", " + "longitude=" + this.longitude + ", " + "precisaoMetros=" + this.precisaoMetros + "]";
    }
}
