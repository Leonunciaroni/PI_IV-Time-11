package br.edu.puccampinas.fastattendance.sessao;

import br.edu.puccampinas.fastattendance.common.comunicado.ComunicadoDeDesligamento;
import br.edu.puccampinas.fastattendance.sessao.servidor.AceitadoraDeConexao;
import br.edu.puccampinas.fastattendance.sessao.servidor.Parceiro;
import br.edu.puccampinas.fastattendance.sessao.servidor.Servicos;

import java.time.Duration;
import java.util.ArrayList;

/**
 * Servidor de sessão no modelo cliente-servidor do material "Fazedor de Continhas" (pasta
 * {@code Servidor/}). Ver docs/servidor-modelo-socket.md.
 * <p>
 * Uso: {@code java -jar sessao.jar [PORTA]}. Comando do console: {@code desativar}.
 */
public class Servidor
{
    public static String PORTA_PADRAO = "3000";

    public static void main (String[] args)
    {
        if (args.length>1)
        {
            System.err.println ("Uso esperado: java -jar sessao.jar [PORTA]\n");
            return;
        }

        String porta=Servidor.PORTA_PADRAO;

        if (args.length==1)
            porta = args[0];

        Servicos servicos;
        try
        {
            servicos = Servicos.montar (Configuracao.doAmbiente (System.getenv()));
        }
        catch (Exception erro)
        {
            System.err.println ("Configuração inválida: " + erro.getMessage() + "\n");
            return;
        }

        ArrayList<Parceiro> usuarios =
        new ArrayList<Parceiro> ();

        AceitadoraDeConexao aceitadoraDeConexao=null;
        try
        {
            aceitadoraDeConexao =
            new AceitadoraDeConexao (porta, usuarios, servicos);
            aceitadoraDeConexao.start();
        }
        catch (Exception erro)
        {
            System.err.println ("Escolha uma porta apropriada e liberada para uso!\n");
            servicos.close();
            return;
        }

        for(;;)
        {
            System.out.println ("O servidor esta ativo! Para desativa-lo,");
            System.out.println ("use o comando \"desativar\"\n");
            System.out.print   ("> ");

            String comando=null;
            try
            {
                comando = Teclado.getUmString();
            }
            catch (Exception erro)
            {}

            if (comando==null)
            {
                // Desvio do modelo: stdin fechado (ex.: contêiner sem -i) devolve null, e o modelo daria
                // NullPointerException aqui. Sem console, o servidor continua atendendo a porta.
                esperarParaSempre();
            }

            if (comando.toLowerCase().equals("desativar"))
            {
                desligar (usuarios);

                System.out.println ("O servidor foi desativado!\n");
                System.exit(0);
            }
            else
                System.err.println ("Comando invalido!\n");
        }
    }

    /** Envia {@link ComunicadoDeDesligamento} a todos os parceiros e fecha cada conexão. */
    public static void desligar (ArrayList<Parceiro> usuarios)
    {
        synchronized (usuarios)
        {
            ComunicadoDeDesligamento comunicadoDeDesligamento =
            new ComunicadoDeDesligamento ();

            for (Parceiro usuario:usuarios)
            {
                try
                {
                    usuario.receba (comunicadoDeDesligamento);
                    usuario.adeus  ();
                }
                catch (Exception erro)
                {}
            }
        }
    }

    private static void esperarParaSempre ()
    {
        for(;;)
        {
            try
            {
                Thread.sleep (Duration.ofHours(1));
            }
            catch (InterruptedException erro)
            {} // sinal sem uso aqui: volta a dormir
        }
    }
}
