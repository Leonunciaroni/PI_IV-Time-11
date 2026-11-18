import org.apache.jmeter.samplers.SampleResult
import org.apache.jmeter.threads.JMeterVariables

// Executar na raiz usando as bibliotecas do JMeter; não conecta a nenhum servidor.
def contexto = new Binding([args: ['fechar'] as String[], vars: new JMeterVariables(), SampleResult: new SampleResult()])
def shell = new GroovyShell(contexto)
shell.evaluate(new File('infra/carga/ws.groovy'))
def gerador = shell.classLoader.loadClass('CanalCarga')
// RFC 6238, Apêndice B, HMAC-SHA256: segredo ASCII de 32 bytes em Base32.
String segredo = 'GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQGEZA'
def vetores = [59L: '46119246', 1111111109L: '68084774', 1111111111L: '67062674',
    1234567890L: '91819424', 2000000000L: '90698825', 20000000000L: '77737706']
vetores.each { segundos, esperado ->
    assert gerador.totp(segredo, 'HmacSHA256', 8, 30L, 0L, segundos * 1000L) == esperado
    assert gerador.totp(segredo, 'HmacSHA256', 6, 30L, 0L, segundos * 1000L) == esperado[-6..-1]
    assert gerador.totp(segredo, 'HmacSHA256', 8, 30L, 123L, (segundos + 123L) * 1000L) == esperado
}
println 'OK: seis vetores RFC 6238, truncamento para seis digitos e deslocamento t0.'
