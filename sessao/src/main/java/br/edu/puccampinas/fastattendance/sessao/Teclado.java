package br.edu.puccampinas.fastattendance.sessao;

import java.io.*;

/** Leitura da linha de comando do servidor (só o necessário: o console aceita apenas texto). */
public class Teclado
{
    private static BufferedReader teclado =
                   new BufferedReader (
                   new InputStreamReader (
                   System.in));

    /** Próxima linha da entrada padrão, ou {@code null} quando a entrada está fechada. */
    public static String getUmString ()
    {
        String ret=null;

        try
        {
            ret = teclado.readLine ();
        }
        catch (IOException erro)
        {} // sei que nao vai dar erro

        return ret;
    }
}
