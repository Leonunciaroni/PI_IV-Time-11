// Guilherme Preventi Correia
// RA: 25001694

package br.edu.puccampinas.fastattendance.api.auth;

import br.edu.puccampinas.fastattendance.api.dominio.Usuario;
import br.edu.puccampinas.fastattendance.api.erro.EmailJaCadastradoException;
import br.edu.puccampinas.fastattendance.api.erro.RecursoNaoEncontradoException;
import br.edu.puccampinas.fastattendance.api.erro.RequisicaoInvalidaException;
import br.edu.puccampinas.fastattendance.api.repositorio.UsuarioRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.nio.charset.StandardCharsets;

@Service
public class PerfilService {
    private final UsuarioRepository usuarios;
    private final PasswordEncoder encoder;
    private final MongoTemplate mongo;

    public PerfilService(UsuarioRepository usuarios, PasswordEncoder encoder, MongoTemplate mongo) {
        this.usuarios = usuarios;
        this.encoder = encoder;
        this.mongo = mongo;
    }

    public UsuarioResponse atualizar(String usuarioId, AtualizarPerfilRequest dados) {
        Usuario usuario = usuarios.findById(usuarioId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado"));
        if (dados.nome() == null && dados.email() == null && dados.novaSenha() == null) {
            throw new RequisicaoInvalidaException("Informe os dados que deseja atualizar.");
        }
        Update mudancas = new Update();
        if (dados.nome() != null) {
            if (dados.nome().isBlank()) throw new RequisicaoInvalidaException("O nome não pode ficar vazio.");
            mudancas.set("nome", dados.nome().strip());
        }
        String email = dados.email() == null ? usuario.email() : dados.email().strip().toLowerCase(Locale.ROOT);
        if (dados.email() != null) {
            if (email.isBlank()) throw new RequisicaoInvalidaException("O e-mail não pode ficar vazio.");
            usuarios.findByEmail(email).filter(outro -> !outro.id().equals(usuarioId)).ifPresent(outro -> {
                throw new EmailJaCadastradoException(email);
            });
            mudancas.set("email", email);
        }
        Criteria criterio = Criteria.where("_id").is(usuarioId);
        if (dados.novaSenha() != null) {
            if (dados.novaSenha().isBlank()) throw new RequisicaoInvalidaException("A nova senha não pode conter apenas espaços.");
            if (dados.novaSenha().getBytes(StandardCharsets.UTF_8).length > 72) {
                throw new RequisicaoInvalidaException("A nova senha excede o limite de 72 bytes. Use uma senha mais curta.");
            }
            if (dados.senhaAtual() == null
                    || dados.senhaAtual().getBytes(StandardCharsets.UTF_8).length > 72
                    || !encoder.matches(dados.senhaAtual(), usuario.senhaHash())) {
                throw new RequisicaoInvalidaException("A senha atual está incorreta.");
            }
            criterio.and("senhaHash").is(usuario.senhaHash());
            mudancas.set("senhaHash", encoder.encode(dados.novaSenha()));
        } else if (dados.senhaAtual() != null) {
            throw new RequisicaoInvalidaException("Informe a nova senha para alterar sua senha atual.");
        }
        try {
            // Atualiza só os campos enviados, sem sobrescrever mudanças concorrentes em outros campos.
            Usuario atualizado = mongo.findAndModify(Query.query(criterio), mudancas,
                    FindAndModifyOptions.options().returnNew(true), Usuario.class);
            if (atualizado == null) throw new RequisicaoInvalidaException("O perfil mudou durante a atualização. Tente novamente.");
            return UsuarioResponse.de(atualizado);
        } catch (DuplicateKeyException e) {
            throw new EmailJaCadastradoException(email);
        }
    }
}
