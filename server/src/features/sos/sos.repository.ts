import { and, desc, eq } from "drizzle-orm";
import { db } from "../../db";
import { sosEvents, users } from "../../db/schema";

export type NewSosEvent = typeof sosEvents.$inferInsert;
export type SosEventRow = typeof sosEvents.$inferSelect;

export interface PublicIncidentLocation {
  latitude: number;
  longitude: number;
  accuracy?: number;
}

export interface PublicIncident {
  incidentId: string;
  userId: string;
  name: string;
  magnitudeG: number;
  location: PublicIncidentLocation | null;
  triggeredAt: string;
}

export interface CreateSosResult {
  event: SosEventRow;
  created: boolean;
}

const DEFAULT_ROOM_INCIDENT_LIMIT = 50;

export class SosRepository {
  async listPublicByRoom(
    roomCode: string,
    limit = DEFAULT_ROOM_INCIDENT_LIMIT
  ): Promise<PublicIncident[]> {
    const rows = await db
      .select({
        id: sosEvents.id,
        userId: sosEvents.userId,
        name: users.name,
        magnitude: sosEvents.magnitude,
        latitude: sosEvents.latitude,
        longitude: sosEvents.longitude,
        accuracy: sosEvents.accuracy,
        createdAt: sosEvents.createdAt,
      })
      .from(sosEvents)
      .innerJoin(users, eq(sosEvents.userId, users.id))
      .where(eq(sosEvents.roomCode, roomCode))
      .orderBy(desc(sosEvents.createdAt))
      .limit(limit);

    return rows.map((row) => ({
      incidentId: row.id,
      userId: row.userId,
      name: row.name,
      magnitudeG: row.magnitude,
      location:
        row.latitude != null && row.longitude != null
          ? {
              latitude: row.latitude,
              longitude: row.longitude,
              ...(row.accuracy != null ? { accuracy: row.accuracy } : {}),
            }
          : null,
      triggeredAt: row.createdAt.toISOString(),
    }));
  }

  async create(data: NewSosEvent): Promise<CreateSosResult> {
    return db.transaction(async (tx) => {
      const [inserted] = await tx
        .insert(sosEvents)
        .values(data)
        .onConflictDoNothing({
          target: [sosEvents.userId, sosEvents.idempotencyKey],
        })
        .returning();

      if (!inserted) {
        const existing = await tx.query.sosEvents.findFirst({
          where: and(
            eq(sosEvents.userId, data.userId),
            eq(sosEvents.idempotencyKey, data.idempotencyKey!)
          ),
        });
        if (!existing) throw new Error("Failed to create or recover SOS incident");
        return { event: existing, created: false };
      }

      return { event: inserted, created: true };
    });
  }
}

export const sosRepository = new SosRepository();
