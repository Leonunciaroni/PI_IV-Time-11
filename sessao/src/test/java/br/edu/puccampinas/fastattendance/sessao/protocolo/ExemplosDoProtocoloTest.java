/*
 * Autores:
 * - João Pedro Panza Mainieri (RA 25006642)
 */
package br.edu.puccampinas.fastattendance.sessao.protocolo;

import br.edu.puccampinas.fastattendance.common.protocolo.Protocolo;
import br.edu.puccampinas.fastattendance.common.protocolo.TipoMensagem;
import br.edu.puccampinas.fastattendance.common.protocolo.Direcao;
import br.edu.puccampinas.fastattendance.common.protocolo.dados.ConfiguracaoTotp;
import br.edu.puccampinas.fastattendance.common.protocolo.dados.PresenteResumo;
import br.edu.puccampinas.fastattendance.common.protocolo.dados.SessaoEstado;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Confere os exemplos JSON de docs/protocolo-v1.md contra os DTOs que o servidor usa.
 * <p>
 * Cada bloco {@code json} do documento é lido com um mapper estrito (campo desconhecido é erro) e
 * convertido para a classe de {@code dados} do tipo da seção em que está. Os blocos com
 * {@code tipo} (envelope completo) são conferidos também no envelope. Exige que o bloco tenha
 * exatamente os campos do DTO, então um exemplo com campo a mais, a menos ou com valor de enum
 * inexistente quebra o teste. A conferência contra o servidor real (MongoDB) fica nos testes de cada
 * fluxo, como {@code CheckinTest}.
 * <p>
 * O documento é lido a partir do diretório do módulo {@code sessao/}, que é o diretório de trabalho
 * do Maven.
 */
class ExemplosDoProtocoloTest {

    private static final Path DOCUMENTO = Path.of("..", "docs", "protocolo-v1.md");

    /** Títulos de seção de tipos, como {@code ### 4.3 `sessao.abrir`}. */
    private static final Pattern TITULO_COM_TIPO = Pattern.compile("^### [^`]*`([a-z_.]+)`");

    /** Quantidade mínima de exemplos JSON no documento; abaixo disso, o leitor de blocos quebrou. */
    private static final int EXEMPLOS_ESPERADOS = 16;

    private static final JsonMapper ESTRITO = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    @Test
    void exemplosDoDocumentoBatemComOsDtos() throws IOException {
        List<String> linhas = Files.readAllLines(DOCUMENTO);
        Class<?> classeDaSecao = null;
        String tituloDaSecao = "";
        int exemplos = 0;

        for (int i = 0; i < linhas.size(); i++) {
            String linha = linhas.get(i);
            if (linha.startsWith("## ") && !linha.startsWith("### ")) {
                classeDaSecao = null;
                tituloDaSecao = linha;
            } else if (linha.startsWith("### ")) {
                classeDaSecao = classeDoTitulo(linha);
                tituloDaSecao = linha;
            } else if (linha.equals("```json")) {
                StringBuilder bloco = new StringBuilder();
                for (i++; i < linhas.size() && !linhas.get(i).equals("```"); i++) {
                    // Comentários // são usados nos exemplos da seção 9 e não fazem parte do JSON
                    if (!linhas.get(i).strip().startsWith("//")) {
                        bloco.append(linhas.get(i)).append('\n');
                    }
                }
                conferirExemplo(bloco.toString(), classeDaSecao, tituloDaSecao);
                exemplos++;
            }
        }

        assertTrue(exemplos >= EXEMPLOS_ESPERADOS,
                "Exemplos JSON encontrados: " + exemplos + ", esperados ao menos " + EXEMPLOS_ESPERADOS);
    }

    /** Um bloco pode trazer mais de um objeto, como os três exemplos da seção 9. */
    private static void conferirExemplo(String texto, Class<?> classeDaSecao, String secao) {
        Iterator<JsonNode> objetos = ESTRITO.readerFor(JsonNode.class).readValues(texto);
        while (objetos.hasNext()) {
            conferirObjeto(objetos.next(), classeDaSecao, secao);
        }
    }

    private static void conferirObjeto(JsonNode no, Class<?> classeDaSecao, String secao) {
        if (no.get("tipo") == null) {
            // Só os dados: a classe vem do título da seção
            if (classeDaSecao == null) {
                fail("Exemplo sem tipo nem seção conhecida: " + secao);
            }
            conferirCampos(no, classeDaSecao, secao);
            return;
        }

        String nomeTipo = no.get("tipo").stringValue();
        TipoMensagem tipo = TipoMensagem.de(nomeTipo)
                .orElseThrow(() -> new AssertionError("Tipo desconhecido no exemplo de '" + secao + "': " + nomeTipo));
        assertEquals(Protocolo.VERSAO, no.get("v").intValue(), secao);
        assertNotNull(no.get("ts"), secao);
        assertNotNull(no.get("dados"), secao);
        if (tipo.direcao() == Direcao.CLIENTE_PARA_SERVIDOR) {
            assertNotNull(no.get("id"), "Requisição sem id em: " + secao);
            // "ref": null é aceito: o exemplo genérico da seção 2 mostra o campo vazio
            assertTrue(no.get("ref") == null || no.get("ref").isNull(), "Requisição com ref em: " + secao);
        } else {
            conferirCorrelacaoDoServidor(no, secao);
        }
        conferirCampos(no.get("dados"), tipo.classeDados(), secao);
    }

    /**
     * Na seção 5.1 (respostas), o servidor manda {@code ref} e nenhum {@code id}. Na seção 5.2
     * (eventos), não manda nem {@code ref} nem {@code id}. Nas demais seções não há essa distinção.
     */
    private static void conferirCorrelacaoDoServidor(JsonNode no, String secao) {
        boolean temRef = no.get("ref") != null && !no.get("ref").isNull();
        boolean temId = no.get("id") != null && !no.get("id").isNull();
        assertTrue(!temId, "Mensagem do servidor com id em: " + secao);
        if (secao.startsWith("### 5.1 ")) {
            assertTrue(temRef, "Resposta sem ref em: " + secao);
        } else if (secao.startsWith("### 5.2 ")) {
            assertTrue(!temRef, "Evento com ref em: " + secao);
        }
    }

    /** Converte o JSON para a classe e confere que os campos são exatamente os componentes do record. */
    private static void conferirCampos(JsonNode no, Class<?> classe, String secao) {
        // Vazio e demais records sem componentes não têm exemplos; a conferência não se aplica a eles
        RecordComponent[] componentes = classe.getRecordComponents();
        assertNotNull(componentes, "Classe sem componentes: " + classe.getSimpleName());
        ESTRITO.treeToValue(no, classe);
        assertEquals(componentes.length, no.size(), "Campos do exemplo diferentes de " + classe.getSimpleName() + " em: " + secao);
        for (RecordComponent componente : componentes) {
            assertNotNull(no.get(componente.getName()),
                    "Campo " + componente.getName() + " ausente no exemplo de: " + secao);
        }
        if (classe == SessaoEstado.class) {
            // Cada item de presentes tem exatamente os campos de PresenteResumo
            for (JsonNode presente : no.get("presentes")) {
                conferirCampos(presente, PresenteResumo.class, secao);
            }
        }
    }

    /**
     * Classe de {@code dados} do tipo indicado no título da seção, ou {@code null} se o título não traz
     * um tipo do protocolo (ex.: "5.2 Eventos (push, sem `ref`)").
     */
    private static Class<?> classeDoTitulo(String titulo) {
        Matcher casamento = TITULO_COM_TIPO.matcher(titulo);
        if (casamento.find()) {
            Optional<TipoMensagem> tipo = TipoMensagem.de(casamento.group(1));
            if (tipo.isPresent()) {
                return tipo.get().classeDados();
            }
        }
        if (titulo.startsWith("### 5.3 ")) {
            return ConfiguracaoTotp.class;
        }
        return null;
    }
}
