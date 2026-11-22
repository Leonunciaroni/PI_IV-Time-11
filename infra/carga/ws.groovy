import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import java.net.http.HttpClient
import java.net.http.WebSocket
import java.nio.ByteBuffer
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

// Um cliente HTTP compartilhado; cada thread JMeter mantém seu próprio WebSocket.
class CanalCarga implements WebSocket.Listener {
    static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()
    final LinkedBlockingQueue<Object> mensagens = new LinkedBlockingQueue<>()
    final StringBuilder parcial = new StringBuilder()
    WebSocket socket

    void onOpen(WebSocket ws) { ws.request(1) }
    CompletionStage<?> onText(WebSocket ws, CharSequence texto, boolean ultimo) {
        parcial.append(texto)
        if (ultimo) { mensagens.offer(parcial.toString()); parcial.setLength(0) }
        ws.request(1)
        return CompletableFuture.completedFuture(null)
    }
    CompletionStage<?> onClose(WebSocket ws, int codigo, String motivo) {
        mensagens.offer(new IOException('WebSocket fechado: ' + codigo))
        return CompletableFuture.completedFuture(null)
    }
    void onError(WebSocket ws, Throwable erro) { mensagens.offer(erro) }

    Map pedir(String tipo, Map dados, String esperado) {
        String id = UUID.randomUUID().toString()
        socket.sendText(JsonOutput.toJson([v: 1, tipo: tipo, id: id, ts: System.currentTimeMillis(), dados: dados]), true)
            .get(5, TimeUnit.SECONDS)
        long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (true) {
            long restante = limite - System.nanoTime()
            Object texto = restante > 0 ? mensagens.poll(restante, TimeUnit.NANOSECONDS) : null
            if (texto == null) throw new IOException('Timeout em ' + tipo)
            if (texto instanceof Throwable) throw new IOException('Falha WebSocket', (Throwable) texto)
            Map mensagem = (Map) new JsonSlurper().parseText((String) texto)
            if (mensagem.v != 1) throw new IOException('Versao inesperada')
            if (mensagem.ref != id) {
                if (mensagem.tipo == 'erro') throw new IOException('Erro sem correlacao: ' + mensagem.dados?.codigo)
                continue
            }
            if (mensagem.tipo != esperado) throw new IOException('Esperado ' + esperado + '; recebido ' + mensagem.tipo + ' (' + mensagem.dados?.codigo + ')')
            return mensagem
        }
    }

    static String totp(String segredo, String algoritmo, int digitos, long periodo, long t0, long agoraMs) {
        ByteArrayOutputStream chave = new ByteArrayOutputStream()
        int acumulado = 0, bits = 0
        for (char c : segredo.toUpperCase(Locale.ROOT).replace('=', '').toCharArray()) {
            int valor = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ234567'.indexOf((int) c)
            if (valor < 0) throw new IllegalArgumentException('Segredo Base32 invalido')
            acumulado = (acumulado << 5) | valor
            bits += 5
            if (bits >= 8) { bits -= 8; chave.write((acumulado >> bits) & 255) }
        }
        long contador = Math.floorDiv(Math.floorDiv(agoraMs, 1000L) - t0, periodo)
        Mac mac = Mac.getInstance(algoritmo)
        mac.init(new SecretKeySpec(chave.toByteArray(), algoritmo))
        byte[] hmac = mac.doFinal(ByteBuffer.allocate(8).putLong(contador).array())
        int offset = hmac[hmac.length - 1] & 15
        long binario = ((hmac[offset] & 127L) << 24) | ((hmac[offset + 1] & 255L) << 16) |
            ((hmac[offset + 2] & 255L) << 8) | (hmac[offset + 3] & 255L)
        return Long.toString(binario % (long) Math.pow(10, digitos)).padLeft(digitos, '0')
    }
}

String operacao = args[0]
CanalCarga canal = (CanalCarga) vars.getObject('canalWs')
try {
    if (operacao == 'fechar') {
        try { if (canal?.socket != null) canal.socket.sendClose(1000, 'fim da amostra').get(2, TimeUnit.SECONDS) }
        finally { canal?.socket?.abort(); vars.remove('canalWs'); SampleResult.setIgnore() }
        return
    }
    Map resposta
    if (operacao == 'auth') {
        vars.put('autenticado', 'false')
        canal = new CanalCarga()
        vars.putObject('canalWs', canal)
        URI origem = URI.create(props.getProperty('base_url', 'http://localhost:8088'))
        URI destino = new URI(origem.scheme == 'https' ? 'wss' : 'ws', null, origem.host, origem.port, '/ws', null, null)
        canal.socket = CanalCarga.HTTP.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(5))
            .buildAsync(destino, canal).get(6, TimeUnit.SECONDS)
        resposta = canal.pedir('auth', [token: vars.get('jwt')], 'auth.ok')
        if (resposta.dados.usuarioId != vars.get('alunoId') || resposta.dados.papel != 'ALUNO') {
            throw new IOException('Identidade autenticada difere da linha do CSV')
        }
        vars.putObject('diferencaMs', ((Number) resposta.ts).longValue() - System.currentTimeMillis())
        vars.put('autenticado', 'true')
    } else if (operacao == 'checkin') {
        if (vars.get('autenticado') != 'true') throw new IOException('Auth anterior falhou')
        long agora = System.currentTimeMillis() + (long) vars.getObject('diferencaMs')
        String token = CanalCarga.totp(vars.get('segredo'), vars.get('algoritmo'), vars.get('digitos').toInteger(),
            vars.get('periodoSegundos').toLong(), vars.get('t0').toLong(), agora)
        Map dados = [sessaoId: vars.get('sessaoId'), token: token,
            canal: vars.get('canal'), dispositivoId: vars.get('dispositivoId')]
        if (dados.canal == 'QRCODE') dados.putAll([latitude: vars.get('latitude').toDouble(),
            longitude: vars.get('longitude').toDouble(), precisaoMetros: vars.get('precisaoMetros').toDouble()])
        resposta = canal.pedir('checkin', dados, 'checkin.ok')
        if (resposta.dados.sessaoId != vars.get('sessaoId') || !(resposta.dados.registradoEm instanceof Number)) {
            throw new IOException('Confirmacao de check-in invalida')
        }
    } else throw new IllegalArgumentException('Operacao desconhecida')
    SampleResult.setSuccessful(true)
    SampleResult.setResponseCode('200')
    SampleResult.setResponseMessage('OK')
    // Não armazena JWT, segredo TOTP nem mensagem de auth nos resultados.
    SampleResult.setResponseData(JsonOutput.toJson([tipo: resposta.tipo, ref: resposta.ref]), 'UTF-8')
} catch (Exception erro) {
    canal?.socket?.abort()
    SampleResult.setSuccessful(false)
    SampleResult.setResponseCode('FALHA')
    SampleResult.setResponseMessage(erro.class.simpleName + ': ' + erro.message)
}
