package br.edu.puccampinas.fastattendance.api.erro;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

/** Converte exceções em respostas Problem Details (RFC 9457) com mensagens em português. */
@RestControllerAdvice
public class TratadorDeErros {

    @ExceptionHandler(CredenciaisInvalidasException.class)
    ProblemDetail credenciaisInvalidas(CredenciaisInvalidasException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, e.getMessage());
    }

    @ExceptionHandler(EmailJaCadastradoException.class)
    ProblemDetail emailJaCadastrado(EmailJaCadastradoException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(RecursoNaoEncontradoException.class)
    ProblemDetail naoEncontrado(RecursoNaoEncontradoException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(RequisicaoInvalidaException.class)
    ProblemDetail requisicaoInvalida(RequisicaoInvalidaException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail validacao(MethodArgumentNotValidException e) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "Há campos inválidos na requisição.");
        Map<String, String> campos = new LinkedHashMap<>();
        for (FieldError erro : e.getBindingResult().getFieldErrors()) {
            campos.putIfAbsent(erro.getField(), erro.getDefaultMessage());
        }
        problema.setProperty("campos", campos);
        return problema;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail corpoIlegivel(HttpMessageNotReadableException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Corpo da requisição inválido.");
    }
}
