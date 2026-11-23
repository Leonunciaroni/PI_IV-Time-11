/*
 * Autores:
 * - João Pedro Panza Mainieri (RA 25006642)
 */
package br.edu.puccampinas.fastattendance.ponte;

import br.edu.puccampinas.fastattendance.common.comunicado.Comunicado;
import br.edu.puccampinas.fastattendance.common.modelo.ModoChamada;
import br.edu.puccampinas.fastattendance.common.modelo.OrigemPresenca;
import br.edu.puccampinas.fastattendance.common.modelo.Papel;
import br.edu.puccampinas.fastattendance.common.protocolo.CodigoErro;
import br.edu.puccampinas.fastattendance.common.protocolo.dados.MotivoEncerramento;

import java.io.ObjectInputFilter;
import java.util.Set;

/**
 * Allowlist da desserialização dos comunicados que o servidor de sessão envia à ponte.
 * <p>
 * Mesma regra do {@code sessao/servidor/FiltroDeComunicados}: a serialização Java executa código das
 * classes do classpath, e o fluxo vem de outro processo. Só são aceitos comunicados e valores do
 * pacote {@code common.comunicado}, os enums do protocolo e as classes básicas dos campos.
 */
final class FiltroDeComunicados {

    static final String PACOTE_COMUNICADOS = Comunicado.class.getPackageName();

    static final long PROFUNDIDADE_MAXIMA = 8;
    static final long REFERENCIAS_MAXIMAS = 10_000;
    static final long TAMANHO_MAXIMO_ARRAY = 10_000;

    /** O fluxo também traz as superclasses ({@code Number}, {@code Enum}), por isso estão na lista. */
    private static final Set<Class<?>> CLASSES_PERMITIDAS = Set.of(
            String.class, Boolean.class, Integer.class, Long.class, Double.class, Number.class, Enum.class,
            java.util.ArrayList.class, Object[].class,
            ModoChamada.class, Papel.class, OrigemPresenca.class, CodigoErro.class, MotivoEncerramento.class);

    static final ObjectInputFilter FILTRO = info -> {
        if (info.depth() > PROFUNDIDADE_MAXIMA || info.references() > REFERENCIAS_MAXIMAS
                || info.arrayLength() > TAMANHO_MAXIMO_ARRAY) {
            return ObjectInputFilter.Status.REJECTED;
        }
        Class<?> classe = info.serialClass();
        if (classe == null) {
            return ObjectInputFilter.Status.UNDECIDED;
        }
        return permitida(classe) ? ObjectInputFilter.Status.ALLOWED : ObjectInputFilter.Status.REJECTED;
    };

    private FiltroDeComunicados() {
    }

    static boolean permitida(Class<?> classe) {
        if (classe.isPrimitive() || CLASSES_PERMITIDAS.contains(classe)) {
            return true;
        }
        return classe.getPackageName().equals(PACOTE_COMUNICADOS);
    }
}
