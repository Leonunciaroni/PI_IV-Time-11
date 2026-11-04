package br.edu.puccampinas.fastattendance.sessao.chamada;

import br.edu.puccampinas.fastattendance.common.modelo.Colecoes;
import br.edu.puccampinas.fastattendance.common.modelo.ModoChamada;
import br.edu.puccampinas.fastattendance.common.modelo.OrigemPresenca;
import br.edu.puccampinas.fastattendance.common.modelo.Papel;
import br.edu.puccampinas.fastattendance.common.protocolo.CodigoErro;
import br.edu.puccampinas.fastattendance.common.protocolo.dados.Checkin;
import br.edu.puccampinas.fastattendance.common.protocolo.dados.CheckinOk;
import br.edu.puccampinas.fastattendance.common.seguranca.UsuarioAutenticado;
import br.edu.puccampinas.fastattendance.sessao.protocolo.ErroDeProtocolo;
import br.edu.puccampinas.fastattendance.sessao.totp.ValidadorTotp;
import com.mongodb.ErrorCategory;
import com.mongodb.MongoWriteException;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.bson.types.ObjectId;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;

/**
 * Check-in do aluno pelos canais QR Code e som (docs/protocolo-v1.md, seções 4.8 e 7).
 * <p>
 * As validações seguem a ordem da seção 7: token, raio, matrícula, duplicidade de presença e
 * duplicidade de dispositivo. Antes delas, o pedido precisa ser de um aluno autenticado, em uma
 * sessão aberta e em modo compatível com o canal.
 * <p>
 * Só o canal {@code QRCODE} usa geolocalização: exige coordenadas e valida o raio até a sala. No
 * canal {@code SOM} as coordenadas do pedido são ignoradas (não validadas e não gravadas). Os
 * demais passos são iguais nos dois canais. A gravação usa o índice único
 * {@code aluno_sessao_unico} como última barreira, e o índice {@code sessao_dispositivo_unico} para
 * o dispositivo.
 */
public final class ServicoDeCheckin {

    private static final String INDICE_ALUNO_SESSAO = "aluno_sessao_unico";
    private static final String INDICE_DISPOSITIVO = "sessao_dispositivo_unico";

    private final ServicoDeSessoes sessoes;
    private final MongoCollection<Document> matriculas;
    private final MongoCollection<Document> salas;
    private final MongoCollection<Document> presencas;
    private final Clock relogio;

    public ServicoDeCheckin(MongoDatabase banco, ServicoDeSessoes sessoes, Clock relogio) {
        this.sessoes = sessoes;
        this.matriculas = banco.getCollection(Colecoes.MATRICULAS);
        this.salas = banco.getCollection(Colecoes.SALAS);
        this.presencas = banco.getCollection(Colecoes.PRESENCAS);
        this.relogio = relogio;
    }

    public CheckinOk registrar(UsuarioAutenticado aluno, Checkin pedido) {
        if (aluno.papel() != Papel.ALUNO) {
            throw new ErroDeProtocolo(CodigoErro.PERMISSAO_NEGADA, "Apenas alunos fazem check-in");
        }
        String sessaoId = exigirTexto(pedido.sessaoId(), "sessaoId");
        // Sessão encerrada responde antes de qualquer outra validação (docs/protocolo-v1.md, seção 4.4)
        Document sessao = sessoes.aberta(sessaoId.strip());
        String sessaoHex = sessao.getObjectId("_id").toHexString();

        String token = exigirTexto(pedido.token(), "token");
        String dispositivoId = exigirTexto(pedido.dispositivoId(), "dispositivoId");
        if (pedido.canal() == null) {
            throw new ErroDeProtocolo(CodigoErro.MENSAGEM_INVALIDA, "Campo canal ausente");
        }
        exigirModoDoCanal(pedido.canal(), sessao);
        boolean comGeolocalizacao = pedido.canal() == OrigemPresenca.QRCODE;
        if (comGeolocalizacao) {
            exigirCoordenadas(pedido);
        }

        // 1. Token TOTP da janela atual ou da anterior
        validarToken(sessao, token);

        // 2. Distância até a sala (somente QRCODE; no SOM as coordenadas são ignoradas)
        if (comGeolocalizacao) {
            validarRaio(sessao, pedido);
        }

        // 3. Matrícula na turma
        String turmaId = sessao.getString("turmaId");
        if (matriculas.countDocuments(and(eq("turmaId", turmaId), eq("alunoId", aluno.id()))) == 0) {
            throw new ErroDeProtocolo(CodigoErro.ALUNO_NAO_MATRICULADO, "Aluno não matriculado na turma");
        }

        // 4 e 5. Duplicidades. As consultas dão uma resposta determinística; os índices únicos,
        // a seguir, cobrem as corridas entre réplicas.
        if (presencas.countDocuments(and(eq("sessaoId", sessaoHex), eq("alunoId", aluno.id()))) > 0) {
            throw new ErroDeProtocolo(CodigoErro.PRESENCA_DUPLICADA, "Presença já registrada nesta sessão");
        }
        if (presencas.countDocuments(and(eq("sessaoId", sessaoHex), eq("dispositivoId", dispositivoId))) > 0) {
            throw new ErroDeProtocolo(CodigoErro.DISPOSITIVO_JA_UTILIZADO,
                    "Este dispositivo já registrou presença de outro aluno nesta sessão");
        }

        Instant agora = relogio.instant().truncatedTo(ChronoUnit.MILLIS);
        Document presenca = new Document("sessaoId", sessaoHex)
                .append("turmaId", turmaId)
                .append("alunoId", aluno.id())
                .append("presente", true)
                .append("origem", pedido.canal().name())
                .append("dispositivoId", dispositivoId)
                .append("registradoEm", Date.from(agora));
        if (comGeolocalizacao) {
            presenca.append("latitude", pedido.latitude()).append("longitude", pedido.longitude());
        }
        gravar(presenca);
        return new CheckinOk(sessaoHex, agora.toEpochMilli());
    }

    /** O canal precisa corresponder ao modo da sessão: QRCODE com QRCODE, SOM com SOM. */
    private static void exigirModoDoCanal(OrigemPresenca canal, Document sessao) {
        ModoChamada modoDoCanal = switch (canal) {
            case QRCODE -> ModoChamada.QRCODE;
            case SOM -> ModoChamada.SOM;
            default -> null;
        };
        if (modoDoCanal == null) {
            throw new ErroDeProtocolo(CodigoErro.MODO_INCOMPATIVEL, "Canal %s não aceita check-in".formatted(canal));
        }
        if (!modoDoCanal.name().equals(sessao.getString("modo"))) {
            throw new ErroDeProtocolo(CodigoErro.MODO_INCOMPATIVEL,
                    "O check-in pelo canal %s exige sessão em modo %s".formatted(canal, modoDoCanal));
        }
    }

    private void validarToken(Document sessao, String token) {
        long instante = relogio.instant().getEpochSecond();
        ValidadorTotp.Resultado resultado = ValidadorTotp.validar(sessao.getString("segredoTotp"), token, instante);
        if (resultado == ValidadorTotp.Resultado.TOKEN_INVALIDO) {
            throw new ErroDeProtocolo(CodigoErro.TOKEN_INVALIDO, "Token não confere com a sessão");
        }
        if (resultado == ValidadorTotp.Resultado.TOKEN_EXPIRADO) {
            throw new ErroDeProtocolo(CodigoErro.TOKEN_EXPIRADO, "Token de uma janela já expirada");
        }
    }

    private void validarRaio(Document sessao, Checkin pedido) {
        Document sala = sala(sessao.getString("salaId"));
        double distancia = Haversine.metros(sala.getDouble("latitude"), sala.getDouble("longitude"),
                pedido.latitude(), pedido.longitude());
        double raio = sala.getDouble("raioMetros");
        if (distancia > raio) {
            throw new ErroDeProtocolo(CodigoErro.FORA_DO_RAIO,
                    "Aluno a %.0f m da sala; o raio aceito é de %.0f m".formatted(distancia, raio));
        }
    }

    /** Grava a presença; o índice violado (nome na mensagem) define o código de erro na corrida entre réplicas. */
    private void gravar(Document presenca) {
        try {
            presencas.insertOne(presenca);
        } catch (MongoWriteException e) {
            if (e.getError().getCategory() != ErrorCategory.DUPLICATE_KEY) {
                throw e;
            }
            String mensagem = e.getError().getMessage();
            if (mensagem != null && mensagem.contains(INDICE_DISPOSITIVO)) {
                throw new ErroDeProtocolo(CodigoErro.DISPOSITIVO_JA_UTILIZADO,
                        "Este dispositivo já registrou presença de outro aluno nesta sessão");
            }
            if (mensagem != null && mensagem.contains(INDICE_ALUNO_SESSAO)) {
                throw new ErroDeProtocolo(CodigoErro.PRESENCA_DUPLICADA, "Presença já registrada nesta sessão");
            }
            throw e;
        }
    }

    private Document sala(String salaId) {
        if (salaId == null || !ObjectId.isValid(salaId)) {
            // Toda sessão QRCODE recebe salaId válido na abertura; sem ele o estado é inconsistente
            throw new IllegalStateException("Sessão QRCODE sem sala válida");
        }
        Document sala = salas.find(eq("_id", new ObjectId(salaId))).first();
        if (sala == null) {
            throw new IllegalStateException("Sala da sessão não encontrada: " + salaId);
        }
        return sala;
    }

    private static String exigirTexto(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new ErroDeProtocolo(CodigoErro.MENSAGEM_INVALIDA, "Informe " + campo);
        }
        return valor;
    }

    /** Coordenadas e precisão são obrigatórias no canal QRCODE (seção 4.8). */
    private static void exigirCoordenadas(Checkin pedido) {
        Double latitude = pedido.latitude();
        Double longitude = pedido.longitude();
        Double precisao = pedido.precisaoMetros();
        if (latitude == null || longitude == null || precisao == null) {
            throw new ErroDeProtocolo(CodigoErro.MENSAGEM_INVALIDA,
                    "Latitude, longitude e precisaoMetros são obrigatórios no canal QRCODE");
        }
        boolean coordenadasValidas = Double.isFinite(latitude) && Double.isFinite(longitude)
                && latitude >= -90 && latitude <= 90 && longitude >= -180 && longitude <= 180;
        if (!coordenadasValidas || !Double.isFinite(precisao) || precisao < 0) {
            throw new ErroDeProtocolo(CodigoErro.MENSAGEM_INVALIDA, "Coordenadas ou precisão inválidas");
        }
    }
}
