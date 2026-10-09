"use client";

import { useEffect, useState } from "react";
import { receiptFrom, receiptFromSample, type BillData, type BillReceipt, type SampleBillData } from "@/domain/billReceipt";
import { PrimaryButton } from "@/ui/basics";
import { billPdfFile, billPreviewUrl, shareOrSaveFile } from "@/ui/billPdf";
import { prepareBillAssets } from "@/ui/billRender";

/** The /b/<token> page body: the receipt image + "Download PDF". */

type View =
  | { kind: "loading" }
  | { kind: "missing" }
  | { kind: "error" }
  | { kind: "ok"; receipt: BillReceipt; no: number | string; shopName: string; src: string };

export function PublicBill({ token }: { token: string }) {
  const [view, setView] = useState<View>({ kind: "loading" });
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    let live = true;
    fetch(`/api/laundry/public/bills/${encodeURIComponent(token)}`, { cache: "no-store" })
      .then(async (r) => {
        if (r.status === 404) return { kind: "missing" } as const;
        const body = (await r.json()) as { success?: boolean; bill?: BillData | SampleBillData };
        if (!r.ok || !body.success || !body.bill) return { kind: "error" } as const;
        const bill = body.bill;
        const receipt = "sample" in bill ? receiptFromSample(bill) : receiptFrom(bill);
        await prepareBillAssets(receipt); // fonts + logo, so the PDF below can draw synchronously
        const no = "sample" in bill ? 1001 : bill.order.serialNo?.trim() || bill.order.id;
        return { kind: "ok", receipt, no, shopName: bill.shop.name, src: billPreviewUrl(receipt) } as const;
      })
      .catch(() => ({ kind: "error" }) as const)
      .then((v) => {
        if (live) setView(v);
      });
    return () => {
      live = false;
    };
  }, [token]);

  useEffect(() => {
    if (view.kind === "ok") document.title = `Bill #${view.no} · ${view.shopName}`;
  }, [view]);

  const download = () => {
    if (view.kind !== "ok" || busy) return;
    setBusy(true);
    // Built + shared synchronously inside the tap (iOS Safari requirement).
    const file = billPdfFile(view.receipt, view.no);
    void shareOrSaveFile(file, `Bill #${view.no}`).finally(() => setBusy(false));
  };

  return (
    <main className="mx-auto flex min-h-dvh w-full max-w-[440px] flex-col gap-4 px-4 py-6">
      {view.kind === "loading" ? (
        <div className="flex flex-1 items-center justify-center text-[14px] text-muted">Loading your bill…</div>
      ) : view.kind === "ok" ? (
        <>
          {/* eslint-disable-next-line @next/next/no-img-element -- a data: URL, nothing to optimise */}
          <img
            src={view.src}
            alt={`Bill #${view.no} from ${view.shopName}`}
            className="w-full rounded-2xl border border-cardborder bg-white"
          />
          <PrimaryButton h={54} onClick={download} disabled={busy}>
            Download PDF
          </PrimaryButton>
        </>
      ) : (
        <div className="flex flex-1 flex-col items-center justify-center gap-2 text-center">
          <span className="bric text-[22px]">
            {view.kind === "missing" ? "Bill not available yet" : "Couldn't load the bill"}
          </span>
          <span className="max-w-[300px] text-[14px] text-muted">
            {view.kind === "missing"
              ? "The shop may still be syncing it. Please try again in a minute, or ask the shop to send it again."
              : "Please check your internet connection and try again."}
          </span>
        </div>
      )}
      <p className="mt-auto pt-2 text-center text-[12px] text-muted">Bill made with MyLaundry</p>
    </main>
  );
}
