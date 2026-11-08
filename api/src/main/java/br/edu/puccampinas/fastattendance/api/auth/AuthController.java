package br.edu.puccampinas.fastattendance.api.auth;

import br.edu.puccampinas.fastattendance.common.seguranca.UsuarioAutenticado;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService auth;
    private final PerfilService perfil;

    public AuthController(AuthService auth, PerfilService perfil) {
        this.auth = auth;
        this.perfil = perfil;
    }

    /** Autentica com e-mail e senha e devolve o JWT usado na API e no WebSocket. */
    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest requisicao) {
        return auth.login(requisicao);
    }

    /** Cria uma conta de aluno, ou de professor se o e-mail estiver em {@link CadastroProperties}. */
    @PostMapping("/registro")
    @ResponseStatus(HttpStatus.CREATED)
    public UsuarioResponse registrar(@Valid @RequestBody RegistroRequest requisicao) {
        return auth.registrar(requisicao);
    }

    /** Dados do usuário dono do JWT. */
    @GetMapping("/me")
    public UsuarioResponse eu(@AuthenticationPrincipal UsuarioAutenticado usuario) {
        return auth.buscar(usuario.id());
    }

    @PatchMapping("/me")
    public UsuarioResponse atualizar(@AuthenticationPrincipal UsuarioAutenticado usuario,
                                      @Valid @RequestBody AtualizarPerfilRequest dados) {
        return perfil.atualizar(usuario.id(), dados);
    }
}
