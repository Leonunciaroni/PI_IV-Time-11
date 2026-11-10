# Testes das regras de validação — issue #51

## Executar e gerar cobertura

Com Docker Desktop aberto e JDK 21 ou superior, na raiz:

```sh
./mvnw verify
```

Para executar somente o módulo de sessão e sua dependência:

```sh
./mvnw -pl sessao -am verify
```

No Windows, usar `mvnw.cmd`. O Testcontainers cria um MongoDB descartável em replica
set; os testes não usam o banco `fast_attendance` do Compose. O JaCoCo 0.8.16 gera
`sessao/target/site/jacoco/index.html`, `jacoco.xml` e `jacoco.csv` na fase `verify`.
Os arquivos gerados ficam em `target`, fora do versionamento. Não usar `-DskipTests`
para produzir evidência de cobertura.

O relatório considera todo o módulo de sessão, inclusive o console e os serviços
auxiliares. A cobertura das regras não deve ser confundida com a cobertura global.

Medição local em 10/11/2026: **103 testes de sessão aprovados**, além dos 9 do `common`.

| Classe | Linhas cobertas / total | Ramos cobertos / total |
|---|---|---|
| `ValidadorTotp` | 34 / 36 (94,4%) | 21 / 22 (95,5%) |
| `Haversine` | 8 / 8 (100%) | Sem ramos |
| `ServicoDeCheckin` | 93 / 98 (94,9%) | 57 / 75 (76%) |

Os números são a fotografia dessa execução; o HTML/XML/CSV gerado pelo comando
acima é a referência para medir mudanças futuras.

## Cenários cobertos

| Regra | Evidência automatizada |
|---|---|
| TOTP | `ValidadorTotpTest`: vetores da RFC 6238 com HMAC-SHA256, janela atual/anterior, futuro, token inválido, outra chave, expiração e fronteiras exatas de janela. |
| Geolocalização | `HaversineTest`: mesmo ponto, meridiano, equador, polos, antípodas, simetria e distância curta. `CheckinTest`: dentro, fora, igualdade exata ao raio e distância imediatamente acima do limite. Precisão alta não amplia o raio. |
| Duplicidade do aluno | `CheckinTest`: segundo registro recusado e duas conexões enviando simultaneamente pelo QR, com uma única presença no banco. |
| Dispositivo | `CheckinTest`: recusa do segundo aluno no mesmo dispositivo, reutilização em outra sessão e disputa simultânea de dois alunos pelo canal SOM. |
| Outras recusas | Sessão encerrada/inexistente, matrícula ausente, papel incompatível, canal incompatível, coordenadas ausentes ou inválidas. |
| Persistência | Banco real com os índices únicos usados pela aplicação. Os testes conferem os documentos gravados, o código de erro e a ausência de gravação nas recusas. |

Os testes concorrentes sincronizam a partida de duas conexões autenticadas e
conferem exatamente um sucesso, uma recusa e um registro. O escalonamento pode
fazer a consulta antecipada detectar a duplicidade ou levar a disputa até o índice;
não se afirma que uma execução isolada percorre necessariamente os dois caminhos.
Os testes não alteram os tempos de expiração ou os limites de produção.

## Limites

Esses testes validam regras de servidor e o transporte TCP. Não medem alcance de
áudio nem a precisão física do GPS. O check-in pela ponte e a interface do aluno
têm verificações próprias, descritas na documentação do QR Code. A entrega do
modem e os testes físicos em sala continuam separados.

Referências: [plugin Maven do JaCoCo](https://www.jacoco.org/jacoco/trunk/doc/maven.html),
[histórico de versões](https://www.jacoco.org/jacoco/trunk/doc/changes.html) e
[protocolo do projeto](protocolo-v1.md).
