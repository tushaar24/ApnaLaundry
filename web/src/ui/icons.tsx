/**
 * Minimal inline stroke icons (24×24 viewBox) standing in for the Material
 * icons used by the app. No icon library — keeps the bundle light.
 */

import type { SVGProps } from "react";

type P = SVGProps<SVGSVGElement> & { size?: number };

function Base({ size = 24, children, ...rest }: P & { children: React.ReactNode }) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={2}
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      {...rest}
    >
      {children}
    </svg>
  );
}

export const IcSearch = (p: P) => (
  <Base {...p}><circle cx={11} cy={11} r={7} /><path d="m20 20-3.8-3.8" /></Base>
);
export const IcAdd = (p: P) => (
  <Base {...p}><path d="M12 5v14M5 12h14" /></Base>
);
export const IcMinus = (p: P) => (
  <Base {...p}><path d="M5 12h14" /></Base>
);
export const IcArrowUp = (p: P) => (
  <Base {...p}><path d="M12 19V5m0 0-6 6m6-6 6 6" /></Base>
);
export const IcArrowDown = (p: P) => (
  <Base {...p}><path d="M12 5v14m0 0-6-6m6 6 6-6" /></Base>
);
export const IcBack = (p: P) => (
  <Base {...p}><path d="M19 12H5m0 0 7-7m-7 7 7 7" /></Base>
);
export const IcEye = (p: P) => (
  <Base {...p}><path d="M2.5 12S6 5.5 12 5.5 21.5 12 21.5 12 18 18.5 12 18.5 2.5 12 2.5 12Z" /><circle cx={12} cy={12} r={3} /></Base>
);
export const IcEyeOff = (p: P) => (
  <Base {...p}><path d="M3 3l18 18" /><path d="M10.6 5.8A9.8 9.8 0 0 1 12 5.5c6 0 9.5 6.5 9.5 6.5a17.4 17.4 0 0 1-3.1 3.9M6.6 6.6A17 17 0 0 0 2.5 12S6 18.5 12 18.5a9.4 9.4 0 0 0 4.4-1.1" /><path d="M9.9 9.9a3 3 0 0 0 4.2 4.2" /></Base>
);
export const IcCall = (p: P) => (
  <Base {...p}><path d="M5 4h4l2 5-2.5 1.5a11 11 0 0 0 5 5L15 13l5 2v4a2 2 0 0 1-2 2A16 16 0 0 1 3 6a2 2 0 0 1 2-2Z" /></Base>
);
export const IcMore = (p: P) => (
  <Base {...p} strokeWidth={0} fill="currentColor">
    <circle cx={12} cy={5} r={1.8} /><circle cx={12} cy={12} r={1.8} /><circle cx={12} cy={19} r={1.8} />
  </Base>
);
export const IcHome = (p: P) => (
  <Base {...p}><path d="m3 11 9-8 9 8" /><path d="M5 9.5V21h14V9.5" /></Base>
);
export const IcStore = (p: P) => (
  <Base {...p}><path d="M4 4h16l1 5a3 3 0 0 1-3 3 3.2 3.2 0 0 1-3-2 3.2 3.2 0 0 1-3 2 3.2 3.2 0 0 1-3-2 3.2 3.2 0 0 1-3 2 3 3 0 0 1-3-3Z" /><path d="M5 12v9h14v-9M9 21v-6h6v6" /></Base>
);
export const IcReceipt = (p: P) => (
  <Base {...p}><path d="M6 2h12v20l-2-1.5L14 22l-2-1.5L10 22l-2-1.5L6 22Z" /><path d="M9 7h6M9 11h6M9 15h4" /></Base>
);
export const IcPeople = (p: P) => (
  <Base {...p}><circle cx={9} cy={8.5} r={3.5} /><path d="M2.5 19.5c0-3 3-5 6.5-5s6.5 2 6.5 5" /><path d="M16 5.5a3.5 3.5 0 0 1 0 6.6M17.5 14.9c2.4.6 4 2.3 4 4.6" /></Base>
);
export const IcChart = (p: P) => (
  <Base {...p}><path d="M4 20V10M10 20V4M16 20v-8M21 20H3" /></Base>
);
export const IcSettings = (p: P) => (
  <Base {...p}><circle cx={12} cy={12} r={3} /><path d="M19.4 15a1.7 1.7 0 0 0 .3 1.9l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.7 1.7 0 0 0-1.9-.3 1.7 1.7 0 0 0-1 1.5V21a2 2 0 1 1-4 0v-.1a1.7 1.7 0 0 0-1-1.6 1.7 1.7 0 0 0-1.9.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.7 1.7 0 0 0 .3-1.9 1.7 1.7 0 0 0-1.5-1H3a2 2 0 1 1 0-4h.1a1.7 1.7 0 0 0 1.6-1 1.7 1.7 0 0 0-.3-1.9l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.7 1.7 0 0 0 1.9.3h.1a1.7 1.7 0 0 0 1-1.5V3a2 2 0 1 1 4 0v.1a1.7 1.7 0 0 0 1 1.5 1.7 1.7 0 0 0 1.9-.3l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.7 1.7 0 0 0-.3 1.9v.1a1.7 1.7 0 0 0 1.5 1h.2a2 2 0 1 1 0 4h-.1a1.7 1.7 0 0 0-1.5 1Z" /></Base>
);
export const IcCalendar = (p: P) => (
  <Base {...p}><rect x={3} y={5} width={18} height={16} rx={2} /><path d="M8 3v4M16 3v4M3 10h18" /></Base>
);
export const IcClock = (p: P) => (
  <Base {...p}><circle cx={12} cy={12} r={9} /><path d="M12 7v5l3 2" /></Base>
);
export const IcShare = (p: P) => (
  <Base {...p}><circle cx={6} cy={12} r={2.5} /><circle cx={17.5} cy={5.5} r={2.5} /><circle cx={17.5} cy={18.5} r={2.5} /><path d="m8.3 10.8 7-4M8.3 13.2l7 4" /></Base>
);
export const IcEdit = (p: P) => (
  <Base {...p}><path d="M4 20h4L20.5 7.5a2.1 2.1 0 0 0-3-3L5 17Z" /><path d="m14.5 6.5 3 3" /></Base>
);
export const IcDelete = (p: P) => (
  <Base {...p}><path d="M4 7h16M9 7V4h6v3M6 7l1 14h10l1-14" /><path d="M10 11v6M14 11v6" /></Base>
);
export const IcInfo = (p: P) => (
  <Base {...p}><circle cx={12} cy={12} r={9} /><path d="M12 11v5" /><circle cx={12} cy={8} r={0.5} fill="currentColor" /></Base>
);
export const IcWarning = (p: P) => (
  <Base {...p}><path d="M10.3 3.9 2.4 18a2 2 0 0 0 1.7 3h15.8a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0Z" /><path d="M12 9v4" /><circle cx={12} cy={17} r={0.5} fill="currentColor" /></Base>
);
export const IcLock = (p: P) => (
  <Base {...p}><rect x={5} y={11} width={14} height={10} rx={2} /><path d="M8 11V7a4 4 0 0 1 8 0v4" /></Base>
);
export const IcCheck = (p: P) => (
  <Base {...p}><path d="m5 13 4 4L19 7" /></Base>
);
export const IcCancel = (p: P) => (
  <Base {...p}><circle cx={12} cy={12} r={9} /><path d="m9 9 6 6M15 9l-6 6" /></Base>
);
export const IcPerson = (p: P) => (
  <Base {...p}><circle cx={12} cy={8} r={4} /><path d="M4.5 20.5c0-3.5 3.4-5.5 7.5-5.5s7.5 2 7.5 5.5" /></Base>
);
export const IcTruck = (p: P) => (
  <Base {...p}><path d="M2 6h12v11H2zM14 10h4l3 3v4h-7" /><circle cx={6.5} cy={17.5} r={1.8} /><circle cx={17.5} cy={17.5} r={1.8} /></Base>
);
export const IcLaundry = (p: P) => (
  <Base {...p}><rect x={4} y={2.5} width={16} height={19} rx={2.5} /><circle cx={12} cy={13} r={5} /><path d="M7.5 13a2.6 2.6 0 0 0 3 .8 3.4 3.4 0 0 1 3 .2 2.6 2.6 0 0 0 3-.6" /><circle cx={7.5} cy={5.8} r={0.5} fill="currentColor" /><circle cx={10.3} cy={5.8} r={0.5} fill="currentColor" /></Base>
);
export const IcShirt = (p: P) => (
  <Base {...p}><path d="m9 4-5 3 1.8 3.8L8 9.6V20h8V9.6l2.2 1.2L20 7l-5-3a3 3 0 0 1-6 0Z" /></Base>
);
export const IcChevronRight = (p: P) => (
  <Base {...p}><path d="m9 6 6 6-6 6" /></Base>
);
export const IcChat = (p: P) => (
  <Base {...p}><path d="M4 4h16v12H8l-4 4V4Z" /></Base>
);
export const IcBook = (p: P) => (
  <Base {...p}><path d="M6.5 3H19v15H6.5A1.5 1.5 0 0 0 5 19.5V4.5A1.5 1.5 0 0 1 6.5 3Z" /><path d="M5 19.5A1.5 1.5 0 0 0 6.5 21H19M9 8h6" /></Base>
);
export const IcVolume = (p: P) => (
  <Base {...p}><path d="M11 5 6 9H3v6h3l5 4z" /><path d="M15.5 8.5a5 5 0 0 1 0 7M18.5 5.5a9 9 0 0 1 0 13" /></Base>
);
export const IcVolumeOff = (p: P) => (
  <Base {...p}><path d="M11 5 6 9H3v6h3l5 4z" /><path d="m22 9-6 6M16 9l6 6" /></Base>
);
export const IcReplay = (p: P) => (
  <Base {...p}><path d="M3 12a9 9 0 1 0 3-6.7L3 8" /><path d="M3 3v5h5" /></Base>
);
export const IcPlay = (p: P) => (
  <Base {...p}><path d="M7 4.5v15l12-7.5z" fill="currentColor" /></Base>
);
export const IcChevronLeft = (p: P) => (
  <Base {...p}><path d="m15 6-6 6 6 6" /></Base>
);
export const IcImage = (p: P) => (
  <Base {...p}><rect x={3} y={4} width={18} height={16} rx={2} /><circle cx={9} cy={10} r={2} /><path d="m21 17-5-5-9 8" /></Base>
);
export const IcCamera = (p: P) => (
  <Base {...p}><path d="M4 8h3l2-3h6l2 3h3v11H4V8Z" /><circle cx={12} cy={13} r={3.5} /></Base>
);
export const IcQr = (p: P) => (
  <Base {...p}><rect x={4} y={4} width={6} height={6} /><rect x={14} y={4} width={6} height={6} /><rect x={4} y={14} width={6} height={6} /><path d="M14 14h2v2h-2zM18 18h2v2h-2zM14 18h2M18 14h2" /></Base>
);
export const IcPin = (p: P) => (
  <Base {...p}><path d="M12 21s-6-5.6-6-11a6 6 0 0 1 12 0c0 5.4-6 11-6 11Z" /><circle cx={12} cy={10} r={2.2} /></Base>
);
export const IcList = (p: P) => (
  <Base {...p}><path d="M9 6h11M9 12h11M9 18h11M4 6h.01M4 12h.01M4 18h.01" /></Base>
);
export const IcBadge = (p: P) => (
  <Base {...p}><rect x={4} y={5} width={16} height={14} rx={2} /><path d="M8 10h8M8 14h5" /></Base>
);
/** WhatsApp glyph (filled, brand green applied by the caller). */
export const IcWhatsApp = ({ size = 24, ...rest }: P) => (
  <svg width={size} height={size} viewBox="0 0 24 24" aria-hidden="true" {...rest}>
    <path
      fill="currentColor"
      d="M12 2a10 10 0 0 0-8.6 15.1L2 22l5-1.3A10 10 0 1 0 12 2Zm0 18.2a8.2 8.2 0 0 1-4.2-1.2l-.3-.2-3 .8.8-2.9-.2-.3A8.2 8.2 0 1 1 12 20.2Zm4.5-6.1c-.2-.1-1.5-.7-1.7-.8s-.4-.1-.6.1-.7.8-.8 1-.3.2-.5.1a6.7 6.7 0 0 1-3.3-2.9c-.3-.4.2-.4.7-1.4.1-.2 0-.3 0-.4l-.8-1.8c-.2-.5-.4-.4-.6-.4h-.5a1 1 0 0 0-.7.3 3 3 0 0 0-.9 2.2 5.2 5.2 0 0 0 1.1 2.7 11.9 11.9 0 0 0 4.6 4c1.7.7 2.4.8 3.2.7a2.8 2.8 0 0 0 1.8-1.3 2.3 2.3 0 0 0 .2-1.3c-.1-.1-.3-.2-.5-.3Z"
    />
  </svg>
);
