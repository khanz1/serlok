import type { ServerWebSocket } from "bun";
import {
  cleanupRoomDevice,
  handleRoomDisconnect,
  handleRoomMessage,
  handleRoomReconnect,
} from "./room.ws";
import { cleanupSosDevice, handleSosMessage } from "./sos.ws";
import { ts } from "../../utils/log";
import { short, wsManager } from "./ws.manager";
import { ClientMessageSchema, type WsData } from "./ws.types";
import { elapsed } from "./ws.utils";

type QueuedMessage = string | Uint8Array;

async function processMessage(ws: ServerWebSocket<WsData>, message: QueuedMessage): Promise<void> {
  if (message instanceof Uint8Array) {
    ws.close(1003, "Binary messages are not supported");
    return;
  }

  const { userId } = ws.data;
  try {
    const result = ClientMessageSchema.safeParse(JSON.parse(message));
    if (!result.success) {
      console.warn(`${ts()} BAD_MSG ${short(userId)} ${result.error.issues[0]?.message}`);
      return;
    }

    const { type } = result.data;
    const payload = (result.data.payload ?? {}) as Record<string, unknown>;

    if (type === "ping") {
      ws.send(JSON.stringify({ type: "pong", timestamp: Date.now() }));
      return;
    }

    const handled =
      (await handleRoomMessage(ws, type, payload)) ||
      (await handleSosMessage(ws, type, payload, message));

    if (!handled) {
      console.warn(`${ts()} UNHANDLED ${short(userId)} type=${type}`);
    }
  } catch (error) {
    console.error(`${ts()} MESSAGE_ERR ${short(userId)} ${(error as Error).message}`);
  }
}

export function createWsHandler(
  reconnect: (ws: ServerWebSocket<WsData>) => Promise<unknown> = handleRoomReconnect
) {
  return {
    maxPayloadLength: 512 * 1024,
    backpressureLimit: 8 * 1024 * 1024,
    idleTimeout: 120,

    open(ws: ServerWebSocket<WsData>) {
      const { userId, name, deviceInstanceId } = ws.data;
      const existing = wsManager.getConnection(userId);
      if (existing && existing !== ws) existing.close(1000, "Replaced by newer connection");
      ws.data.messageChain = Promise.resolve(reconnect(ws)).then(() => undefined);
      wsManager.addConnection(userId, ws);
      console.log(
        `${ts()} CONNECT ${short(userId).padEnd(12)} name="${name}" device="${deviceInstanceId}" [total: ${wsManager.getConnectionCount()}]`
      );
    },

    message(ws: ServerWebSocket<WsData>, message: string | Buffer) {
      const queuedMessage: QueuedMessage =
        typeof message === "string"
          ? message
          : Uint8Array.from(new Uint8Array(message.buffer, message.byteOffset, message.byteLength));
      const previous = ws.data.messageChain ?? Promise.resolve();
      ws.data.messageChain = previous.then(
        () => processMessage(ws, queuedMessage),
        () => processMessage(ws, queuedMessage)
      );
    },

    close(ws: ServerWebSocket<WsData>, code: number, _reason: string) {
      void (async () => {
        const { userId } = ws.data;
        await ws.data.messageChain?.catch(() => undefined);
        if (wsManager.getConnection(userId) !== ws) {
          // An older socket must not tear down the newer connection's Room state.
          return;
        }
        wsManager.removeConnection(userId);
        cleanupRoomDevice(userId);
        cleanupSosDevice(userId);
        const duration = elapsed(Date.now() - ws.data.connectedAt);
        const result = await handleRoomDisconnect(ws);
        const roomNote = result ? ` room=${result.code}` : "";
        console.log(
          `${ts()} DISCONNECT ${short(userId).padEnd(12)} up=${duration} code=${code}${roomNote}`
        );
        console.log(`[total: ${wsManager.getConnectionCount()}]`);
      })();
    },

    drain(_ws: ServerWebSocket<WsData>) { },
  };
}

export const wsHandler = createWsHandler();

export type { WsData } from "./ws.types";
