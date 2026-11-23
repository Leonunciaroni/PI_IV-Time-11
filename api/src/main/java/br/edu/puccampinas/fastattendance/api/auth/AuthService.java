/*
 * Autores:
 * - João Pedro Panza Mainieri (RA 25006642)
 */
package br.edu.puccampinas.fastattendance.api.auth;

import br.edu.puccampinas.fastattendance.api.dominio.Usuario;
import br.edu.puccampinas.fastattendance.api.erro.CadastroNaoPermitidoException;
import br.edu.puccampinas.fastattendance.api.erro.CredenciaisInvalidasException;
import br.edu.puccampinas.fastattendance.api.erro.EmailJaCadastradoException;
import br.edu.puccampinas.fastattendance.api.erro.RecursoNaoEncontradoException;
import br.edu.puccampinas.fastattendance.api.repositorio.UsuarioRepository;
import br.edu.puccampinas.fastattendance.common.modelo.Papel;
import br.edu.puccampinas.fastattendance.common.seguranca.JwtService;
import br.edu.puccampinas.fastattendance.common.seguranca.TokenEmitido;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Optional;

@Service
public class AuthService {

    private final UsuarioRepository usuarios;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final CadastroProperties cadastro;
    /**
     * Hash usado quando o e-mail não existe, para que o tempo de resposta seja o mesmo de uma
     * senha errada e não revele quais e-mails estão cadastrados.
     */
    private final String hashFicticio;

    public AuthService(UsuarioRepository usuarios, PasswordEncoder encoder, JwtService jwt,
                       CadastroProperties cadastro) {
        this.usuarios = usuarios;
        this.encoder = encoder;
        this.jwt = jwt;
        this.cadastro = cadastro;
        this.hashFicticio = encoder.encode("senha-ficticia-para-equalizar-tempo");
    }

    public TokenResponse login(LoginRequest requisicao) {
        Optional<Usuario> encontrado = usuarios.findByEmail(normalizar(requisicao.email()));
        String hash = encontrado.map(Usuario::senhaHash).orElse(hashFicticio);
        boolean senhaConfere = encoder.matches(requisicao.senha(), hash);
        if (encontrado.isEmpty() || !senhaConfere) {
            throw new CredenciaisInvalidasException();
        }
        Usuario usuario = encontrado.get();
        TokenEmitido emitido = jwt.emitir(usuario.id(), usuario.nome(), usuario.papel());
        return new TokenResponse(emitido.token(), emitido.expiraEm(), UsuarioResponse.de(usuario));
    }

    public UsuarioResponse registrar(RegistroRequest requisicao) {
        String email = normalizar(requisicao.email());
        // Verificado antes do e-mail duplicado, para a recusa não revelar quais e-mails existem
        if (requisicao.papel() == Papel.PROFESSOR && !cadastro.permiteProfessor(email)) {
            throw new CadastroNaoPermitidoException();
        }
        if (usuarios.findByEmail(email).isPresent()) {
            throw new EmailJaCadastradoException(email);
        }
        Usuario novo = new Usuario(null, requisicao.nome().strip(), email, encoder.encode(requisicao.senha()),
                requisicao.papel(), requisicao.matricula().strip(), null);
        try {
            return UsuarioResponse.de(usuarios.insert(novo));
        } catch (DuplicateKeyException e) {
            // Dois cadastros simultâneos com o mesmo e-mail: o índice único decide
            throw new EmailJaCadastradoException(email);
        }
    }

    public UsuarioResponse buscar(String usuarioId) {
        return usuarios.findById(usuarioId)
                .map(UsuarioResponse::de)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado"));
    }

    private static String normalizar(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }
}
