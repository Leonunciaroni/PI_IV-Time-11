// Guilherme Preventi Correia
// RA: 25001694

import { afterEach, describe, expect, it, vi } from "vitest";
import { enviarCheckinQr, obterLocalizacao } from "./checkin";
import { errosCheckin, mensagemDoErro } from "./protocolo";
import type { ClienteSessao } from "./ws";

afterEach(() => vi.unstubAllGlobals());
describe("check-in com localização", () => {
  const qr = { sessaoId: "66f1c2aa0011223344556677", token: "004239" };
  const local = {
    latitude: -22.8339,
    longitude: -47.0525,
    precisaoMetros: 12.5,
    obtidaEm: Date.now(),
  };
  it("envia exatamente os campos do protocolo, sem JWT no corpo do check-in", async () => {
    const ok = { sessaoId: qr.sessaoId, registradoEm: 1791370805170 };
    const requisitar = vi.fn().mockResolvedValue(ok);
    expect(
      await enviarCheckinQr(
        { estado: "pronto", requisitar } as unknown as ClienteSessao,
        qr,
        local,
        "uuid-do-aparelho",
      ),
    ).toEqual(ok);
    expect(requisitar).toHaveBeenCalledExactlyOnceWith("checkin", {
      sessaoId: qr.sessaoId,
      token: "004239",
      canal: "QRCODE",
      dispositivoId: "uuid-do-aparelho",
      latitude: local.latitude,
      longitude: local.longitude,
      precisaoMetros: 12.5,
    });
  });
  it("não envia enquanto desconectado nem aceita resposta de outra sessão", async () => {
    const requisitar = vi
      .fn()
      .mockResolvedValue({ sessaoId: "outra", registradoEm: 1 });
    await expect(
      enviarCheckinQr(
        { estado: "reconectando", requisitar } as unknown as ClienteSessao,
        qr,
        local,
        "d",
      ),
    ).rejects.toThrow(/conexão/);
    expect(requisitar).not.toHaveBeenCalled();
    await expect(
      enviarCheckinQr(
        { estado: "pronto", requisitar } as unknown as ClienteSessao,
        qr,
        local,
        "d",
      ),
    ).rejects.toThrow(/resposta/);
  });
  it("obtém coordenadas com alta precisão, sem cache", async () => {
    vi.stubGlobal("isSecureContext", true);
    const getCurrentPosition = vi.fn<Geolocation["getCurrentPosition"]>((ok) =>
      ok({
        coords: { latitude: -22, longitude: -47, accuracy: 10 },
      } as GeolocationPosition),
    );
    vi.stubGlobal("navigator", { geolocation: { getCurrentPosition } });
    expect(await obterLocalizacao(new AbortController().signal)).toMatchObject({
      latitude: -22,
      longitude: -47,
      precisaoMetros: 10,
    });
    expect(getCurrentPosition.mock.calls[0][2]).toEqual({
      enableHighAccuracy: true,
      maximumAge: 0,
      timeout: 10000,
    });
  });
  it.each([1, 2, 3])("explica falha da localização %i", async (code) => {
    vi.stubGlobal("isSecureContext", true);
    vi.stubGlobal("navigator", {
      geolocation: {
        getCurrentPosition: (_ok: unknown, erro: (e: unknown) => void) =>
          erro({ code }),
      },
    });
    await expect(
      obterLocalizacao(new AbortController().signal),
    ).rejects.toThrow(code === 1 ? /Permita/ : code === 3 ? /demorou/ : /GPS/);
  });
  it("recusa contexto inseguro e permite cancelar a espera pelo GPS", async () => {
    vi.stubGlobal("isSecureContext", false);
    await expect(
      obterLocalizacao(new AbortController().signal),
    ).rejects.toThrow(/HTTPS/);
    vi.stubGlobal("isSecureContext", true);
    vi.stubGlobal("navigator", {
      geolocation: { getCurrentPosition: vi.fn() },
    });
    const controle = new AbortController();
    const pedido = obterLocalizacao(controle.signal);
    controle.abort();
    await expect(pedido).rejects.toMatchObject({ name: "AbortError" });
  });
  it("cada código do servidor tem orientação própria e não mostra erros técnicos desconhecidos", () => {
    expect(Object.keys(errosCheckin)).toHaveLength(17);
    for (const [codigo, mensagem] of Object.entries(errosCheckin)) {
      expect(mensagemDoErro({ codigo, mensagem: "erro técnico" })).toBe(
        mensagem,
      );
    }
    expect(
      mensagemDoErro({
        codigo: "CODIGO_FUTURO",
        mensagem: "stacktrace privado",
      }),
    ).not.toContain("stacktrace");
  });
});
