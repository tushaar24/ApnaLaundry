/**
 * Date helpers ported from core/AppDate.kt (itself from the prototype's
 * `dates()`). ISO dates are "yyyy-MM-dd" strings.
 *
 * "Today" comes from NEXT_PUBLIC_PINNED_TODAY when set (so the web app stays
 * coherent with the Android app's pinned demo clock + seeded data); otherwise
 * the real device clock is used.
 */

const PINNED_TODAY = process.env.NEXT_PUBLIC_PINNED_TODAY || "";
const PINNED_NOW = process.env.NEXT_PUBLIC_PINNED_NOW_MINUTES || "";

function realTodayIso(): string {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
}

export function today(): string {
  return PINNED_TODAY || realTodayIso();
}

export function nowMinutes(): number {
  if (PINNED_NOW) return parseInt(PINNED_NOW, 10);
  const d = new Date();
  return d.getHours() * 60 + d.getMinutes();
}

export function nowText(): string {
  const mins = nowMinutes();
  const hh = Math.floor(mins / 60);
  const disp = hh % 12 === 0 ? 12 : hh % 12;
  const ap = hh >= 12 ? "PM" : "AM";
  return `${disp}:${String(mins % 60).padStart(2, "0")} ${ap}`;
}

/** True after 9 PM — ready-messages are queued for 9 AM. */
export function isLateNight(): boolean {
  return nowMinutes() >= 21 * 60;
}

const MON = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];
const DAY_SHORT = ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"];
const DAY_LONG = ["Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"];

/** Parse "yyyy-MM-dd" to a UTC-noon Date (immune to DST/timezone edges). */
function parse(iso: string): Date {
  const [y, m, d] = iso.split("-").map((v) => parseInt(v, 10));
  return new Date(Date.UTC(y, m - 1, d, 12));
}

function toIso(d: Date): string {
  return `${d.getUTCFullYear()}-${String(d.getUTCMonth() + 1).padStart(2, "0")}-${String(d.getUTCDate()).padStart(2, "0")}`;
}

export function add(iso: string, days: number): string {
  const d = parse(iso);
  d.setUTCDate(d.getUTCDate() + days);
  return toIso(d);
}

export function daysBetween(from: string, to: string): number {
  return Math.round((parse(to).getTime() - parse(from).getTime()) / 86_400_000);
}

function dayIndex(d: Date): number {
  return d.getUTCDay(); // Sun=0
}

export function rel(iso: string, todayIso: string = today()): string | null {
  const df = daysBetween(todayIso, iso);
  if (df === 0) return "Today";
  if (df === 1) return "Tomorrow";
  if (df === -1) return "Yesterday";
  return null;
}

/** "Sunday, 27 Sep" / "Today, 25 Sep" */
export function long(iso: string, todayIso: string = today()): string {
  const d = parse(iso);
  const prefix = rel(iso, todayIso) ?? DAY_LONG[dayIndex(d)];
  return `${prefix}, ${d.getUTCDate()} ${MON[d.getUTCMonth()]}`;
}

/** "Sun, 27 Sep" / "Today, 25 Sep" */
export function short(iso: string, todayIso: string = today()): string {
  const d = parse(iso);
  const prefix = rel(iso, todayIso) ?? DAY_SHORT[dayIndex(d)];
  return `${prefix}, ${d.getUTCDate()} ${MON[d.getUTCMonth()]}`;
}

/** "Fri, 25 Sep" — never relative. */
export function plain(iso: string): string {
  const d = parse(iso);
  return `${DAY_SHORT[dayIndex(d)]}, ${d.getUTCDate()} ${MON[d.getUTCMonth()]}`;
}

export function dayName(iso: string): string {
  return DAY_SHORT[dayIndex(parse(iso))];
}

export function dayOfMonth(iso: string): number {
  return parse(iso).getUTCDate();
}

/** 24h "HH:mm" -> "5:00 PM". Blank in -> blank out. */
export function to12h(hhmm: string): string {
  if (!hhmm.trim()) return "";
  const parts = hhmm.split(":");
  const h = parseInt(parts[0], 10);
  if (Number.isNaN(h)) return "";
  const m = parts[1] ?? "00";
  const hr = h % 12;
  const disp = hr === 0 ? 12 : hr;
  const ap = h >= 12 ? "PM" : "AM";
  return `${disp}:${m.padStart(2, "0")} ${ap}`;
}

/** "5:00 PM" -> "17:00". Blank -> blank. */
export function to24h(t12: string): string {
  if (!t12.trim()) return "";
  const m = /(\d+):(\d+)\s*(AM|PM)/i.exec(t12);
  if (!m) return "";
  let hh = parseInt(m[1], 10) % 12;
  if (m[3].toUpperCase() === "PM") hh += 12;
  return `${String(hh).padStart(2, "0")}:${m[2]}`;
}
