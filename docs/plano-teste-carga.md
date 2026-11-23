<!-- Autoria: Diogo Gonçalves Tonhosolo | RA: 25894007 -->
# Plano de teste de carga (#50)

Este plano prepara a execução da [issue #52](https://github.com/Leonunciaroni/PI_IV-Time-11/issues/52):
15 salas com 70 alunos simultâneos, totalizando **1.050 conexões de alunos** e
1.050 check-ins exclusivos em uma chamada. As salas físicas, turmas, matrículas,
sessões e usuários são criados por execução em um ambiente de teste.

## Cenário

O arquivo [carga.jmx](../infra/carga/carga.jmx) usa JMeter 5.6.3, Groovy e o cliente
WebSocket nativo do Java 21. Não requer plugin de WebSocket. Os scripts de
preparação e análise exigem Node.js 24+, como os apps do projeto.

1. **Preparação, fora da medição:** um professor de teste cria 15 salas e turmas,
   cadastra 70 alunos diferentes em cada turma e os matricula. Cada aluno recebe
   um JWT e um `dispositivoId` exclusivo. Só depois são abertas as 15 sessões,
   cada uma com seu próprio segredo TOTP. O preparo gera CSV, manifesto e
   propriedades do JMeter em `infra/carga/execucoes/<id>/`.
2. **Conexão e autenticação:** as 1.050 threads são iniciadas em 10 s. Cada thread
   abre seu próprio WebSocket `/ws` pelo Nginx, envia `auth` com o JWT no corpo
   e valida `v`, `ref`, `auth.ok`, `usuarioId` e o papel `ALUNO`.
3. **Barreira:** todas as threads aguardam antes do check-in. O Synchronizing
   Timer libera o grupo junto, com timeout de 30 s para evitar espera infinita.
   Threads com falha de autenticação também chegam à barreira e produzem uma
   amostra de check-in falha. Não são omitidas do relatório.
4. **Check-in:** após a barreira, cada thread gera o token TOTP com os parâmetros
   da sessão e o relógio corrigido pelo `ts` recebido em `auth.ok`. Envia uma
   única mensagem `checkin`, espera `checkin.ok` com `ref` correspondente e
   confere sessão e data do registro. Não repete automaticamente mensagens.
5. **Conferência, fora da medição:** o professor consulta `sessao.acompanhar`
   para cada sessão. A resposta `sessao.estado` é uma leitura das presenças no
   MongoDB pelo servidor. A análise compara as identidades esperadas, as
   confirmações do JTL e as identidades efetivamente persistidas.

Cada thread faz uma autenticação e um check-in na mesma conexão, depois fecha o
socket. Não há loop de check-in: uma segunda presença do mesmo aluno na mesma
sessão seria `PRESENCA_DUPLICADA`. Para repetir o teste, crie outra execução.
O intervalo padrão antes do check-in fica abaixo dos 60 s de inatividade do
protocolo; não aumente rampa e barreira acima desse limite sem adicionar keep-alive.

O canal padrão é `QRCODE`, com coordenadas sintéticas iguais às da sala, precisão
zero e raio de 30 m. Uma execução separada pode usar `SOM`, que não envia localização.
Isso exercita a validação do servidor. Leitura de câmera, captura acústica e tempo
de interação humana pertencem aos testes de campo (#13, #33 e #47).

## Métricas e critérios de aprovação

| Medida | Como obter | Aprovação por execução completa |
|---|---|---|
| Cobertura | JTL correlacionado por `sessaoId` e `alunoId` com o manifesto | 15 sessões × 70 alunos; exatamente uma amostra `auth` e uma `checkin` por aluno |
| Autenticação | Resultado do sampler `auth`, incluindo abertura do socket | 1.050 sucessos, zero erros |
| Tempo de check-in | Campo `elapsed` do sampler `checkin`; exclui preparo, auth e espera na barreira | **Máximo < 1.000 ms**, além de registrar p50, p95 e p99 |
| Erros | Falha de transporte, timeout, resposta de protocolo recusada ou confirmação inválida | Zero erros e taxa de erro 0% |
| Simultaneidade | Maior menos menor `timeStamp` dos samplers `checkin` | Dispersão ≤ 1.000 ms; fora disso, a execução não comprova o cenário simultâneo |
| Ausentes | Alunos esperados sem presença persistida | Zero; 1.050 identidades persistidas |
| Registros perdidos | Check-ins confirmados com sucesso no JTL sem presença persistida correspondente | Zero |
| Extras, duplicados e origem | Identidades e origem lidas em `sessao.estado` | Sem extras nem duplicados; origem igual ao canal da execução |

A análise considera falha uma amostra ausente, repetida ou de identidade estranha
ao manifesto. Uma presença persistida cujo cliente recebeu timeout continua
aparecendo como erro de comunicação. Uma resposta de sucesso sem registro é
contabilizada como registro perdido. Contagens iguais de pessoas diferentes não
passam na conferência.

O relatório usa **tempo de resposta completo (`elapsed`)** como latência do
check-in; os samplers JSR223 não preenchem separadamente o campo `Latency` do JTL.
O tempo inclui geração do TOTP, serialização, transporte, validação e gravação,
sem incluir a espera na barreira.

Na #52, faça uma passagem pequena de verificação e **três execuções completas
independentes**. Cada uma deve atender a todos os critérios. Um relatório com
`criteriosAtendidos: true` e `cargaCompleta: false` comprova apenas o cenário
reduzido e não aprova a meta das 15 salas.

## Executar

Use um banco de teste. O preparo cria contas, salas e turmas próprias e não apaga
dados existentes. Esses registros permanecem no banco após a execução. As sessões
permanecem abertas até serem encerradas no app ou vencerem pelo tempo limite.
O diretório local de cada execução contém JWTs e segredos TOTP: ele é ignorado pelo
Git; compartilhe apenas os resultados sem credenciais.

Instale o [JMeter 5.6.3 oficial](https://jmeter.apache.org/download_jmeter.cgi)
e use o JDK 21. Com o Docker disponível, na pasta `infra`, configure `.env` como
explicado no [README da infraestrutura](../infra/README.md) e inicie:

```sh
docker compose up -d --build
```

Utilize um professor já cadastrado. As ferramentas usam por padrão a conta de
demonstração `professor@demo.br`, senha `demo12345`, criada por `infra/usuarios-demo.sh`.
Para outra conta, configure `PROFESSOR_EMAIL` e `PROFESSOR_SENHA` no ambiente dos
comandos Node; o e-mail precisa ser permitido em `PROFESSORES_PERMITIDOS` ao cadastrar.

Exemplo em PowerShell, na raiz do repositório, ajustando o caminho do JMeter:

```powershell
$jmeterCarga = 'C:\ferramentas\apache-jmeter-5.6.3\bin\jmeter.bat'

# Verificação pequena: abra nova execução para cada tentativa.
node infra/carga/preparar.mjs --salas 1 --alunos 3

# Copie o diretório "Dados prontos" informado pelo preparo.
$execucaoCarga = 'C:\caminho\do\repositorio\infra\carga\execucoes\ID'
& $jmeterCarga -n -t infra/carga/carga.jmx -q "$execucaoCarga\carga.properties" -l "$execucaoCarga\resultados.jtl" -j "$execucaoCarga\jmeter.log"
node infra/carga/analisar.mjs $execucaoCarga
```

Para a carga completa, prepare uma nova execução com os valores padrão:

```powershell
node infra/carga/preparar.mjs
# Atualize $execucaoCarga com o NOVO diretório e rode JMeter e análise como acima.
```

Para testar o canal SOM, use `node infra/carga/preparar.mjs --canal SOM`. Para
outro host, acrescente `--url http://HOST:8088`. O destino deve ser a origem do
Nginx, sem caminho adicional, e precisa estar acessível pelo gerador de carga.
Se o prepare falhar, seu manifesto não é marcado como pronto; investigue o erro e
prepare uma nova execução. O provisionamento de 1.050 contas pode levar minutos.

No Linux/macOS, use o mesmo roteiro com `bin/jmeter`, caminhos com `/` e variáveis
do shell. `-q` recebe o arquivo de propriedades, `-l` salva o JTL e `-j` salva o log.
Mantenha `sample_variables` do arquivo gerado, necessário para correlacionar cada aluno.
Não reutilize nem acrescente resultados ao mesmo JTL em execuções diferentes.

Para o relatório HTML do JMeter, após o teste:

```powershell
& $jmeterCarga -g "$execucaoCarga\resultados.jtl" -o "$execucaoCarga\html"
```

A análise gera `persistencia.json` e `relatorio.json`; retorna código 1 quando os
critérios falham. Erros ao consultar o estado persistido impedem a emissão de uma
conclusão válida. Confirme os códigos de saída e os arquivos, pois o JMeter pode
encerrar normalmente mesmo contendo amostras falhas.

## Ambiente e evidências para a #52

Execute a carga em modo CLI, pelo Nginx direto, sem Quick Tunnel. Dimensione o
gerador para 1.050 threads; como ponto inicial, reserve heap de 2 GB para JMeter
(`HEAP=-Xms1g -Xmx2g`). Monitore memória e CPU do gerador e dos servidores. Se o
gerador saturar, não atribua o atraso automaticamente ao servidor. Prefira outra
máquina como gerador para separar esses recursos do Docker.

Registre commit, sistema, versões de Java/JMeter/Docker, CPU, RAM, rede, réplicas
da API e da sessão, canal e horários. Salve o JTL, relatório JSON, log do JMeter,
relatório HTML e métricas dos contêineres (`docker stats`). Não publique `alunos.csv`
nem credenciais. A barreira é local ao processo JMeter: este plano foi definido
para um único gerador; dividir threads entre geradores exige outro mecanismo de
sincronização e particionamento dos dados.

| Execução completa | Canal | p50 / p95 / p99 / máximo | Erros | Persistidos | Perdidos | Dispersão | Resultado |
|---|---|---|---|---|---|---|---|
| 1 | Pendente | — | — | — | — | — | Pendente (#52) |
| 2 | Pendente | — | — | — | — | — | Pendente (#52) |
| 3 | Pendente | — | — | — | — | — | Pendente (#52) |

## Verificação do plano

```sh
node --test infra/carga/metricas.test.mjs
```

Para conferir o TOTP Groovy com os vetores oficiais da RFC 6238, no PowerShell:

```powershell
$bibliotecasJmeter = 'C:\ferramentas\apache-jmeter-5.6.3\lib\*;C:\ferramentas\apache-jmeter-5.6.3\lib\ext\*'
java -cp $bibliotecasJmeter groovy.ui.GroovyMain infra/carga/totp.test.groovy
```

No Linux/macOS, separe os dois caminhos do classpath com `:`.

O JMX foi carregado e executado no JMeter 5.6.3 / Java 21 contra um backend
simulado de loopback, com três alunos por execução nos canais QRCODE e SOM.
Foram exercitados preparação REST, auth, TOTP, mensagens WebSocket fragmentadas,
check-in, barreira e conferência das identidades. Uma terceira passagem simulou
uma autenticação recusada: o relatório detectou a falha e o aluno sem presença,
sem deixar a barreira esperando indefinidamente. Isso verifica o funcionamento
das ferramentas; não constitui medição de capacidade do servidor real.

Referências: [protocolo do projeto](protocolo-v1.md),
[JSR223 Sampler e Synchronizing Timer](https://jmeter.apache.org/usermanual/component_reference.html),
[JMeter em modo CLI](https://jmeter.apache.org/usermanual/get-started.html#non_gui),
[WebSocket do Java](https://docs.oracle.com/en/java/javase/21/docs/api/java.net.http/java/net/http/WebSocket.html).
