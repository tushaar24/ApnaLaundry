/** Indian-grouped rupee formatting: ₹1,25,000. Port of core/Money.kt. */

export function grouping(value: number): string {
  const neg = value < 0;
  let s = Math.abs(Math.trunc(value)).toString();
  if (s.length > 3) {
    const head = s.slice(0, s.length - 3);
    const tail = s.slice(s.length - 3);
    const chunks: string[] = [];
    let rev = head.split("").reverse().join("");
    for (let i = 0; i < rev.length; i += 2) {
      chunks.push(rev.slice(i, i + 2).split("").reverse().join(""));
    }
    s = chunks.reverse().join(",") + "," + tail;
  }
  return (neg ? "-" : "") + s;
}

/** ₹ with the absolute value, Indian-grouped (callers prepend +/− as needed). */
export function rupees(value: number): string {
  return "₹" + grouping(Math.abs(Math.round(value)));
}

/** Compact form for charts: 2.3k, 980. */
export function short(value: number): string {
  if (value >= 1000) {
    const t = (value / 1000).toFixed(1).replace(/\.0$/, "");
    return `${t}k`;
  }
  return value.toString();
}
