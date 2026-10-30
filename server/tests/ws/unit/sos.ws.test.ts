import { describe, expect, test, mock } from "bun:test";
import { createSosWsHandler } from "../../../src/features/ws/sos.ws";
import { mockServerWebSocket } from "../../helpers/mock-websocket";

function makeController() {
  return {
    handleSosTriggerViaWs: mock(async () => {}),
    cleanupSosDevice: mock(() => {}),
  };
}

describe("createSosWsHandler", () => {
  test("sos_trigger routes to the canonical controller", async () => {
    const controller = makeController();
    const handler = createSosWsHandler({ sosController: controller });
    const { ws } = mockServerWebSocket();
    const payload = { magnitudeG: 4.1, x: 40, y: 0, z: 0 };
    expect(await handler.handleSosMessage(ws, "sos_trigger", payload, "raw1")).toBe(true);
    expect(controller.handleSosTriggerViaWs).toHaveBeenCalledWith(ws, payload, "raw1");
  });

  test("sos_resolve is not a handled SOS type", async () => {
    const controller = makeController();
    const handler = createSosWsHandler({ sosController: controller });
    const { ws } = mockServerWebSocket();
    expect(
      await handler.handleSosMessage(ws, "sos_resolve", { incidentId: "id" }, "raw2")
    ).toBe(false);
  });

  test("sos_trigger returns false when its controller method is unavailable", async () => {
    const controller = makeController();
    const handler = createSosWsHandler({
      sosController: {
        cleanupSosDevice: controller.cleanupSosDevice,
      },
    });
    const { ws } = mockServerWebSocket();
    expect(await handler.handleSosMessage(ws, "sos_trigger", {}, "raw2")).toBe(false);
  });

  test("unknown type returns false", async () => {
    const controller = makeController();
    const handler = createSosWsHandler({ sosController: controller });
    const { ws } = mockServerWebSocket();
    expect(await handler.handleSosMessage(ws, "nope", {}, "")).toBe(false);
  });

  test("cleanupSosDevice delegates to controller", () => {
    const controller = makeController();
    const handler = createSosWsHandler({ sosController: controller });
    handler.cleanupSosDevice("user-1");
    expect(controller.cleanupSosDevice).toHaveBeenCalledWith("user-1");
  });
});
