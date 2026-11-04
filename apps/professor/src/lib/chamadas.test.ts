import { describe, expect, it } from "vitest";
import {
  chamadaDaTurma,
  esquecerChamada,
  guardarChamada,
  guardarUltimaSala,
  lerChamada,
  ultimaSala,
  type ChamadaGuardada,
} from "./chamadas";

/** Armazenamento em memória com a interface do localStorage. */
function memoria(): Storage {
  const dados = new Map<string, string>();
  return {
    get length() {
      return dados.size;
    },
    clear: () => dados.clear(),
    getItem: (chave) => dados.get(chave) ?? null,
    key: (i) => [...dados.keys()][i] ?? null,
    removeItem: (chave) => void dados.delete(chave),
    setItem: (chave, valor) => void dados.set(chave, valor),
  };
}

/** Armazenamento bloqueado (janela privada, cookies desligados): toda operação lança. */
const bloqueado = {
  getItem: () => {
    throw new Error("bloqueado");
  },
  setItem: () => {
    throw new Error("bloqueado");
  },
  removeItem: () => {
    throw new Error("bloqueado");
  },
} as unknown as Storage;

const chamada: ChamadaGuardada = {
  sessaoId: "s1",
  turmaId: "t1",
  modo: "QRCODE",
  salaId: "sala-203",
  abertaEm: 1_793_740_000_000,
  totp: { segredo: "JBSWY3DPEHPK3PXP", algoritmo: "HmacSHA256", digitos: 6, periodoSegundos: 15, t0: 0 },
};

describe("chamadas guardadas no navegador", () => {
  it("guarda e lê a chamada pelo id da sessão", () => {
    const armazenamento = memoria();
    guardarChamada(chamada, armazenamento);
    expect(lerChamada("s1", armazenamento)).toEqual(chamada);
    expect(lerChamada("s2", armazenamento)).toBeNull();
  });

  it("encontra a última chamada aberta da turma", () => {
    const armazenamento = memoria();
    guardarChamada(chamada, armazenamento);
    guardarChamada({ ...chamada, sessaoId: "s2", modo: "CLASSICA" }, armazenamento);
    expect(chamadaDaTurma("t1", armazenamento)?.sessaoId).toBe("s2");
    expect(chamadaDaTurma("t9", armazenamento)).toBeNull();
  });

  it("esquece a chamada encerrada, inclusive como última da turma", () => {
    const armazenamento = memoria();
    guardarChamada(chamada, armazenamento);
    esquecerChamada("s1", armazenamento);
    expect(lerChamada("s1", armazenamento)).toBeNull();
    expect(chamadaDaTurma("t1", armazenamento)).toBeNull();
  });

  it("ignora conteúdo corrompido", () => {
    const armazenamento = memoria();
    armazenamento.setItem("fa.professor.chamada.s1", "{não é json");
    expect(lerChamada("s1", armazenamento)).toBeNull();
  });

  it("lembra a última sala usada no modo QR Code", () => {
    const armazenamento = memoria();
    expect(ultimaSala(armazenamento)).toBeNull();
    guardarUltimaSala("sala-203", armazenamento);
    expect(ultimaSala(armazenamento)).toBe("sala-203");
  });

  it("segue funcionando, sem guardar nada, quando o armazenamento está bloqueado", () => {
    expect(() => guardarChamada(chamada, bloqueado)).not.toThrow();
    expect(lerChamada("s1", bloqueado)).toBeNull();
    expect(chamadaDaTurma("t1", bloqueado)).toBeNull();
    expect(() => esquecerChamada("s1", bloqueado)).not.toThrow();
    expect(() => guardarUltimaSala("x", bloqueado)).not.toThrow();
    expect(ultimaSala(bloqueado)).toBeNull();
  });
});
