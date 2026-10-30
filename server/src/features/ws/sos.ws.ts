import type { ServerWebSocket } from "bun";
import type { WsData } from "./ws.types";
import { sosController as defaultSosController } from "../sos/sos.module";
import type { SosController } from "../sos/sos.controller";

export interface SosWsDeps {
  sosController?: Pick<SosController, "cleanupSosDevice"> &
    Partial<Pick<SosController, "handleSosTriggerViaWs">>;
}

export interface SosWsHandler {
  handleSosMessage(
    ws: ServerWebSocket<WsData>,
    type: string,
    payload: Record<string, unknown>,
    raw: string
  ): Promise<boolean>;
  cleanupSosDevice(userId: string): void;
}

export function createSosWsHandler(deps: SosWsDeps = {}): SosWsHandler {
  const sosController = deps.sosController ?? defaultSosController;

  function cleanupSosDevice(userId: string): void {
    sosController.cleanupSosDevice(userId);
  }

  async function handleSosMessage(
    ws: ServerWebSocket<WsData>,
    type: string,
    payload: Record<string, unknown>,
    raw: string
  ): Promise<boolean> {
    switch (type) {
      case "sos_trigger": {
        if (!sosController.handleSosTriggerViaWs) return false;
        await sosController.handleSosTriggerViaWs(ws, payload, raw);
        return true;
      }
      default:
        return false;
    }
  }

  return { handleSosMessage, cleanupSosDevice };
}

const defaultHandler = createSosWsHandler();
export const handleSosMessage = defaultHandler.handleSosMessage;
export const cleanupSosDevice = defaultHandler.cleanupSosDevice;
