"use client";

import { Gate } from "@/ui/gate";

/** Authenticated app zone: everything inside requires login + setup done. */
export default function AppLayout({ children }: { children: React.ReactNode }) {
  return <Gate zone="app">{children}</Gate>;
}
