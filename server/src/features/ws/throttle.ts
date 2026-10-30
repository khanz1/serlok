export interface LocationThrottleSnapshot {
  count: number;
  fps: number;
}

interface LocationEntry {
  count: number;
  lastLogMs: number;
}

export class LocationThrottle {
  private map = new Map<string, LocationEntry>();

  constructor(private readonly intervalMs: number = 5_000) {}

  record(userId: string, now: number): LocationThrottleSnapshot | null {
    let entry = this.map.get(userId);
    if (!entry) {
      entry = { count: 0, lastLogMs: now };
      this.map.set(userId, entry);
    }
    entry.count++;
    if (now - entry.lastLogMs < this.intervalMs) return null;

    const fps = entry.count / ((now - entry.lastLogMs) / 1000);
    const snapshot = { count: entry.count, fps };
    entry.count = 0;
    entry.lastLogMs = now;
    return snapshot;
  }

  delete(userId: string): void {
    this.map.delete(userId);
  }

  reset(): void {
    this.map.clear();
  }
}

export class AlertCooldown {
  private last = new Map<string, number>();

  constructor(private readonly intervalMs: number = 3_000) {}

  shouldFire(userId: string, now: number): boolean {
    const prev = this.last.get(userId);
    if (prev !== undefined && now - prev < this.intervalMs) return false;
    this.last.set(userId, now);
    return true;
  }

  delete(userId: string): void {
    this.last.delete(userId);
  }

  reset(): void {
    this.last.clear();
  }
}
