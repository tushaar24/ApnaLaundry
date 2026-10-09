"use client";

import { authedFetch } from "./syncApi";
import { registerLogo } from "@/ui/billRender";

/**
 * Shop logo upload: the picked photo is resized (≤512px longest side, JPEG)
 * in the browser so a 12-megapixel camera shot never reaches the bill
 * renderer or the server, then POSTed to /api/laundry/shop/logo. The caller
 * stores the returned id on the shop (Repo.updateShopDetails), which syncs.
 */

const MAX_SIDE = 512;

function loadImage(src: string): Promise<HTMLImageElement> {
  return new Promise((resolve, reject) => {
    const img = new Image();
    img.onload = () => resolve(img);
    img.onerror = () => reject(new Error("That file isn't a picture we can read"));
    img.src = src;
  });
}

/** Resized JPEG of the picked file, as base64 (no data: prefix) + its image. */
async function resize(file: File): Promise<{ base64: string; img: HTMLImageElement }> {
  const url = URL.createObjectURL(file);
  try {
    const src = await loadImage(url);
    const k = Math.min(1, MAX_SIDE / Math.max(src.naturalWidth, src.naturalHeight));
    const c = document.createElement("canvas");
    c.width = Math.max(1, Math.round(src.naturalWidth * k));
    c.height = Math.max(1, Math.round(src.naturalHeight * k));
    const ctx = c.getContext("2d");
    if (!ctx) throw new Error("Couldn't process the picture");
    ctx.fillStyle = "#ffffff"; // transparent PNG logos print on white
    ctx.fillRect(0, 0, c.width, c.height);
    ctx.drawImage(src, 0, 0, c.width, c.height);
    const dataUrl = c.toDataURL("image/jpeg", 0.85);
    return { base64: dataUrl.slice(dataUrl.indexOf(",") + 1), img: await loadImage(dataUrl) };
  } finally {
    URL.revokeObjectURL(url);
  }
}

/** Resizes + uploads a logo; returns its id (already usable by the bill renderer). */
export async function uploadLogo(file: File): Promise<string> {
  const { base64, img } = await resize(file);
  const res = await authedFetch("/api/laundry/shop/logo", {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ contentType: "image/jpeg", dataBase64: base64 }),
  });
  const body = (await res.json().catch(() => ({}))) as { success?: boolean; logoId?: string; message?: string };
  if (!res.ok || !body.success || !body.logoId) {
    throw new Error(body.message || "Couldn't upload the logo — check internet and try again");
  }
  registerLogo(body.logoId, img);
  return body.logoId;
}
