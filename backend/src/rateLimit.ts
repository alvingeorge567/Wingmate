/** Tiny fixed-window in-memory rate limiter (per process). Use Redis/KV for multi-instance deploys. */
export function createRateLimiter(limitPerMinute: number, now: () => number = Date.now) {
  const windows = new Map<string, { start: number; count: number }>();
  return function allow(key: string): boolean {
    const t = now();
    const w = windows.get(key);
    if (!w || t - w.start >= 60_000) {
      windows.set(key, { start: t, count: 1 });
      if (windows.size > 10_000) {
        for (const [k, v] of windows) if (t - v.start >= 60_000) windows.delete(k);
      }
      return true;
    }
    w.count += 1;
    return w.count <= limitPerMinute;
  };
}
