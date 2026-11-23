/*
 * Autores:
 * - João Pedro Panza Mainieri (RA 25006642)
 */
package br.edu.puccampinas.fastattendance.sessao.servidor;

import br.edu.puccampinas.fastattendance.common.comunicado.Comunicado;
import br.edu.puccampinas.fastattendance.common.modelo.ModoChamada;
import br.edu.puccampinas.fastattendance.common.modelo.OrigemPresenca;
import br.edu.puccampinas.fastattendance.common.modelo.Papel;
import br.edu.puccampinas.fastattendance.common.protocolo.CodigoErro;
import br.edu.puccampinas.fastattendance.common.protocolo.dados.MotivoEncerramento;

import java.io.ObjectInputFilter;
import java.util.Set;

/**
 * Allowlist da desserialização dos comunicados recebidos.
 * <p>
 * A desserialização Java lê dados de quem está do outro lado do socket. Sem filtro, um cliente
 * poderia enviar objetos de qualquer classe do classpath, e a serialização executa código dessas
 * classes. Por isso só são aceitos: os comunicados e valores do pacote {@code common.comunicado}, os
 * enums do protocolo, as classes básicas usadas nos campos ({@code String}, boxed numéricos e
 * booleano, {@code ArrayList} e o {@code Object[]} que ele usa) e primitivos. Qualquer outra classe
 * fecha a conexão. Os limites de profundidade, referências e tamanho de array impedem objetos
 * construídos para consumir memória.
 */
public final class FiltroDeComunicados {

    static final String PACOTE_COMUNICADOS = Comunicado.class.getPackageName();

    static final long PROFUNDIDADE_MAXIMA = 8;
    static final long REFERENCIAS_MAXIMAS = 10_000;
    static final long TAMANHO_MAXIMO_ARRAY = 10_000;

    /**
     * O fluxo serializado também traz as superclasses: {@code Double} e {@code Integer} trazem
     * {@code Number}, e os enums trazem {@code Enum}. Por isso elas precisam estar na lista.
     */
    private static final Set<Class<?>> CLASSES_PERMITIDAS = Set.of(
            String.class, Boolean.class, Integer.class, Long.class, Double.class, Number.class, Enum.class,
            java.util.ArrayList.class, Object[].class,
            ModoChamada.class, Papel.class, OrigemPresenca.class, CodigoErro.class, MotivoEncerramento.class);

    public static final ObjectInputFilter FILTRO = info -> {
        if (info.depth() > PROFUNDIDADE_MAXIMA || info.references() > REFERENCIAS_MAXIMAS
                || info.arrayLength() > TAMANHO_MAXIMO_ARRAY) {
            return ObjectInputFilter.Status.REJECTED;
        }
        Class<?> classe = info.serialClass();
        if (classe == null) {
            // Ainda não há classe a julgar (ex.: limites do fluxo): deixa a decisão para as demais checagens
            return ObjectInputFilter.Status.UNDECIDED;
        }
        return permitida(classe) ? ObjectInputFilter.Status.ALLOWED : ObjectInputFilter.Status.REJECTED;
    };

    private FiltroDeComunicados() {
    }

    static boolean permitida(Class<?> classe) {
        if (classe.isPrimitive()) {
            return true;
        }
        if (CLASSES_PERMITIDAS.contains(classe)) {
            return true;
        }
        return classe.getPackageName().equals(PACOTE_COMUNICADOS);
    }
}
