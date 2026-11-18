package br.edu.puccampinas.fastattendance.sessao.servidor;

import br.edu.puccampinas.fastattendance.common.comunicado.Comunicado;
import br.edu.puccampinas.fastattendance.common.comunicado.Erro;
import br.edu.puccampinas.fastattendance.common.comunicado.PedidoDeAbrirSessao;
import br.edu.puccampinas.fastattendance.common.comunicado.PedidoDeAcompanharSessao;
import br.edu.puccampinas.fastattendance.common.comunicado.PedidoDeAutenticacao;
import br.edu.puccampinas.fastattendance.common.comunicado.PedidoDeCheckin;
import br.edu.puccampinas.fastattendance.common.comunicado.PedidoDeEncerrarSessao;
import br.edu.puccampinas.fastattendance.common.comunicado.PedidoDeMarcarPresenca;
import br.edu.puccampinas.fastattendance.common.comunicado.PedidoDeMarcarPresencaEmLote;
import br.edu.puccampinas.fastattendance.common.comunicado.PedidoDePing;
import br.edu.puccampinas.fastattendance.common.comunicado.PedidoParaSair;
import br.edu.puccampinas.fastattendance.common.comunicado.RespostaDeAutenticacao;
import br.edu.puccampinas.fastattendance.common.comunicado.RespostaDePong;
import br.edu.puccampinas.fastattendance.common.protocolo.CodigoErro;
import br.edu.puccampinas.fastattendance.common.seguranca.TokenInvalidoException;
import br.edu.puccampinas.fastattendance.common.seguranca.UsuarioAutenticado;
import br.edu.puccampinas.fastattendance.sessao.protocolo.ErroDeProtocolo;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.*;
import java.time.Clock;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Atende uma conexão: um thread por cliente, como no modelo.
 * <p>
 * Os pedidos da conexão são processados em ordem, um de cada vez. Para cada pedido que espera
 * resposta, a supervisora envia exatamente uma mensagem: a resposta de sucesso ou um {@link Erro}.
 * Os eventos empurrados pelo painel ao vivo não solicitados e não entram nessa conta.
 * <p>
 * As regras do antigo {@code RoteadorDeMensagens} ficam aqui, nesta ordem: limite de mensagens,
 * tipo aceito do cliente, {@code auth} antes dos demais pedidos, prazo do JWT e timeout de
 * inatividade. As regras de negócio ficam nos serviços.
 */
public class SupervisoraDeConexao extends Thread
{
    private static final System.Logger LOG = System.getLogger(SupervisoraDeConexao.class.getName());

    private Parceiro             usuario;
    private Socket               conexao;
    private ArrayList<Parceiro>  usuarios;
    private Servicos             servicos;

    private final LimitadorDeMensagens limitador = new LimitadorDeMensagens(Clock.systemUTC());
    private UsuarioAutenticado  autenticado = null;
    private Future<?>           expiracaoDoToken = null;

    public SupervisoraDeConexao (Socket conexao, ArrayList<Parceiro> usuarios, Servicos servicos)
    throws Exception
    {
        if (conexao==null)
            throw new Exception ("Conexao ausente");

        if (usuarios==null)
            throw new Exception ("Usuarios ausentes");

        if (servicos==null)
            throw new Exception ("Servicos ausentes");

        this.conexao  = conexao;
        this.usuarios = usuarios;
        this.servicos = servicos;
    }

    public void run ()
    {
        ObjectOutputStream transmissor;
        try
        {
            // Primeiro o transmissor, como no modelo: o cabeçalho do fluxo sai antes de esperar o do cliente
            transmissor =
            new ObjectOutputStream(
            this.conexao.getOutputStream());
        }
        catch (Exception erro)
        {
            fecharSocket();
            return;
        }

        ObjectInputStream receptor=null;
        try
        {
            // O prazo de autenticação vale já para o cabeçalho: um cliente que não fala não prende o thread
            this.conexao.setSoTimeout(prazoAutenticacaoMs());
            receptor=
            new ObjectInputStream(
            this.conexao.getInputStream());
            receptor.setObjectInputFilter(FiltroDeComunicados.FILTRO);
        }
        catch (Exception err0)
        {
            fecharTudo(transmissor, receptor);
            return;
        }

        try
        {
            this.usuario =
            new Parceiro (this.conexao,
                          receptor,
                          transmissor);
        }
        catch (Exception erro)
        {
            fecharTudo(transmissor, receptor);
            return;
        }

        synchronized (this.usuarios)
        {
            this.usuarios.add (this.usuario);
        }

        try
        {
            for(;;)
            {
                Comunicado comunicado = this.usuario.envie ();

                // PedidoParaSair não tem resposta: sai do laço e a conexão é encerrada no finally
                if (comunicado==null || comunicado instanceof PedidoParaSair)
                    return;

                if (!processar (comunicado))
                    return;
            }
        }
        catch (Exception erro)
        {
            // Queda da conexão, prazo de autenticação, inatividade ou token expirado: termina sem mensagem
        }
        finally
        {
            encerrar ();
        }
    }

    /**
     * Trata um pedido. Devolve {@code false} quando a conexão deve ser encerrada logo depois da
     * resposta enviada (autenticação recusada ou limite de mensagens excedido repetidamente).
     */
    private boolean processar (Comunicado pedido) throws Exception
    {
        // Antes de tudo, inclusive da validação do tipo: a conta é de mensagens recebidas
        if (!this.limitador.registrar())
        {
            responder (new Erro (CodigoErro.LIMITE_EXCEDIDO, "Mensagens demais em pouco tempo"));
            return !this.limitador.excessoRepetido();
        }

        if (pedido instanceof PedidoDeAutenticacao)
            return autenticar ((PedidoDeAutenticacao) pedido);

        if (!ehPedidoDoCliente (pedido))
        {
            responder (new Erro (CodigoErro.MENSAGEM_INVALIDA,
                    "Tipo de comunicado que o cliente não pode enviar: " + pedido.getClass().getSimpleName()));
            return true;
        }

        if (pedido instanceof PedidoDePing)
        {
            responder (new RespostaDePong ());
            return true;
        }

        if (this.autenticado==null)
        {
            responder (new Erro (CodigoErro.AUTH_REQUERIDA,
                    "Envie PedidoDeAutenticacao antes de " + pedido.getClass().getSimpleName()));
            return true;
        }

        responder (executar (pedido));
        return true;
    }

    private boolean autenticar (PedidoDeAutenticacao pedido) throws Exception
    {
        if (this.autenticado!=null)
        {
            responder (new Erro (CodigoErro.MENSAGEM_INVALIDA, "Conexão já autenticada"));
            return true;
        }

        UsuarioAutenticado usuarioAutenticado;
        try
        {
            usuarioAutenticado = this.servicos.jwt().validar (pedido.getToken());
        }
        catch (TokenInvalidoException | RuntimeException erro)
        {
            responder (new Erro (CodigoErro.AUTH_INVALIDA, "Token inválido ou expirado"));
            return false;
        }

        this.autenticado = usuarioAutenticado;
        responder (new RespostaDeAutenticacao (usuarioAutenticado.id(), usuarioAutenticado.nome(),
                usuarioAutenticado.papel(), usuarioAutenticado.expiraEm().toEpochMilli()));

        // Quando o token vence, a conexão é fechada, mesmo que esteja ativa
        long restante = Math.max(0, Duration.between(this.servicos.relogio().instant(),
                usuarioAutenticado.expiraEm()).toMillis());
        this.expiracaoDoToken = this.servicos.agendador().schedule(this::expirarToken,
                restante, TimeUnit.MILLISECONDS);

        // Depois de autenticada, a conexão sem tráfego por tempo demais é encerrada
        this.conexao.setSoTimeout(inatividadeMs());
        return true;
    }

    /** Executa um pedido de negócio de um usuário autenticado. Falhas viram {@link Erro}. */
    private Comunicado executar (Comunicado pedido)
    {
        try
        {
            return despachar (pedido);
        }
        catch (ErroDeProtocolo erro)
        {
            return new Erro (erro.codigo(), erro.getMessage());
        }
        catch (RuntimeException erro)
        {
            // A conexão continua: o pedido recebe sua resposta, e o erro fica no log do servidor
            LOG.log(System.Logger.Level.ERROR, "Falha ao processar " + pedido.getClass().getSimpleName(), erro);
            return new Erro (CodigoErro.ERRO_INTERNO, "Erro inesperado");
        }
    }

    private Comunicado despachar (Comunicado pedido)
    {
        UsuarioAutenticado u = this.autenticado;
        if (pedido instanceof PedidoDeAbrirSessao p)
            return this.servicos.sessoes().abrir (u, p);
        if (pedido instanceof PedidoDeEncerrarSessao p)
            return this.servicos.sessoes().encerrar (u, p.getSessaoId());
        if (pedido instanceof PedidoDeAcompanharSessao p)
            return this.servicos.painel().acompanhar (this.usuario, u, p.getSessaoId());
        if (pedido instanceof PedidoDeMarcarPresenca p)
            return this.servicos.chamadaClassica().marcar (u, p);
        if (pedido instanceof PedidoDeMarcarPresencaEmLote p)
            return this.servicos.chamadaClassica().marcarLote (u, p);
        if (pedido instanceof PedidoDeCheckin p)
            return this.servicos.checkin().registrar (u, p);
        throw new IllegalStateException ("Pedido sem tratamento: " + pedido.getClass().getName());
    }

    private static boolean ehPedidoDoCliente (Comunicado comunicado)
    {
        return comunicado instanceof PedidoDePing
            || comunicado instanceof PedidoDeAutenticacao
            || comunicado instanceof PedidoDeAbrirSessao
            || comunicado instanceof PedidoDeEncerrarSessao
            || comunicado instanceof PedidoDeAcompanharSessao
            || comunicado instanceof PedidoDeMarcarPresenca
            || comunicado instanceof PedidoDeMarcarPresencaEmLote
            || comunicado instanceof PedidoDeCheckin;
    }

    private void expirarToken ()
    {
        LOG.log(System.Logger.Level.DEBUG, "Token expirado; conexão encerrada");
        fecharSocket();
    }

    private void responder (Comunicado resposta) throws Exception
    {
        this.usuario.receba (resposta);
    }

    /** Fim da conexão, por qualquer motivo: tira o parceiro da lista e do painel. */
    private void encerrar ()
    {
        if (this.expiracaoDoToken!=null)
            this.expiracaoDoToken.cancel (false);

        synchronized (this.usuarios)
        {
            this.usuarios.remove (this.usuario);
        }
        this.servicos.painel().desconectar (this.usuario);

        try
        {
            this.usuario.adeus ();
        }
        catch (Exception erro)
        {} // já fechada pela queda ou pelo desligamento
    }

    private void fecharTudo (ObjectOutputStream transmissor, ObjectInputStream receptor)
    {
        try
        {
            if (transmissor!=null) transmissor.close();
            if (receptor!=null)    receptor.close();
        }
        catch (Exception falha)
        {} // só tentando fechar antes de acabar a thread
        fecharSocket();
    }

    private void fecharSocket ()
    {
        try
        {
            this.conexao.close();
        }
        catch (IOException falha)
        {} // só tentando fechar antes de acabar a thread
    }

    private int prazoAutenticacaoMs ()
    {
        return (int) this.servicos.configuracao().prazoAutenticacao().toMillis();
    }

    private int inatividadeMs ()
    {
        return (int) this.servicos.configuracao().timeoutInatividade().toMillis();
    }
}
