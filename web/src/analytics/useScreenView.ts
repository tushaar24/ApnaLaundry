"use client";

import { useEffect } from "react";
import { Analytics, type ScreenName } from "./events";

/** Fire a `Screen Viewed` event once when a screen mounts. */
export function useScreenView(screen: ScreenName): void {
  useEffect(() => {
    Analytics.screen(screen);
  }, [screen]);
}
