import { and, eq } from "drizzle-orm";
import { db } from "../../db";
import { devices } from "../../db/schema";

export type DeviceRow = typeof devices.$inferSelect;
export type NewDevice = typeof devices.$inferInsert;

class DeviceRepository {
  async upsert(data: NewDevice): Promise<DeviceRow> {
    const now = new Date();
    const [row] = await db
      .insert(devices)
      .values({ ...data, lastSeenAt: now, updatedAt: now })
      .onConflictDoUpdate({
        target: [devices.userId, devices.deviceInstanceId],
        set: {
          manufacturer: data.manufacturer,
          model: data.model,
          androidVersion: data.androidVersion,
          lastSeenAt: now,
          updatedAt: now,
        },
      })
      .returning();
    return row!;
  }

  async findByUserAndInstance(
    userId: string,
    deviceInstanceId: string
  ): Promise<DeviceRow | undefined> {
    return db.query.devices.findFirst({
      where: and(eq(devices.userId, userId), eq(devices.deviceInstanceId, deviceInstanceId)),
    });
  }
}

export const deviceRepository = new DeviceRepository();
