/*
 * Autores:
 * - João Pedro Panza Mainieri (RA 25006642)
 */
package br.edu.puccampinas.fastattendance.sessao.servidor;

import java.io.IOException;
import java.net.*;
import java.util.*;

public class AceitadoraDeConexao extends Thread
{
    private ServerSocket        pedido;
    private ArrayList<Parceiro> usuarios;
    private Servicos            servicos;

    public AceitadoraDeConexao
    (String porta, ArrayList<Parceiro> usuarios, Servicos servicos)
    throws Exception
    {
        if (porta==null)
            throw new Exception ("Porta ausente");

        if (usuarios==null)
            throw new Exception ("Usuarios ausentes");

        if (servicos==null)
            throw new Exception ("Servicos ausentes");

        try
        {
            this.pedido =
            new ServerSocket (Integer.parseInt(porta));
        }
        catch (Exception  erro)
        {
            throw new Exception ("Porta invalida");
        }

        this.usuarios = usuarios;
        this.servicos = servicos;
    }

    public void run ()
    {
        for(;;)
        {
            Socket conexao=null;
            try
            {
                conexao = this.pedido.accept();
            }
            catch (Exception erro)
            {
                // Com o ServerSocket fechado, accept() falha sem parar: nesse caso a aceitação termina
                if (this.pedido.isClosed())
                    return;
                continue;
            }

            try
            {
                new SupervisoraDeConexao (conexao, usuarios, servicos).start();
            }
            catch (Exception erro)
            {
                try
                {
                    conexao.close();
                }
                catch (IOException falha)
                {}
            }
        }
    }

    /** Porta em uso (útil quando foi pedida a porta 0, nos testes). */
    public int porta ()
    {
        return this.pedido.getLocalPort();
    }

    /** Para de aceitar conexões. As já aceitas continuam até terminarem. */
    public void encerrar () throws IOException
    {
        this.pedido.close();
    }
}
