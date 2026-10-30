import { sosRepository } from "../sos/sos.repository";
import { RoomController } from "./room.controller";
import { roomService } from "./room.service";

const roomController = new RoomController(roomService, sosRepository);

export { roomController };
