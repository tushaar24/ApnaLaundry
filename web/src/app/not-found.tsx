import Link from "next/link";
import { IcLaundry } from "@/ui/icons";

/** Branded 404 — unknown routes get a way back instead of the Next default. */
export default function NotFound() {
  return (
    <div className="flex min-h-dvh flex-col items-center justify-center gap-4 bg-bg px-8 text-center">
      <div className="flex size-14 items-center justify-center rounded-2xl bg-blue text-ondark">
        <IcLaundry size={30} />
      </div>
      <h1 className="bric text-[26px]">Page not found</h1>
      <p className="max-w-sm text-[14px] text-muted">
        This link doesn&apos;t go anywhere. Your orders and khata are safe.
      </p>
      <Link
        href="/"
        className="flex h-[52px] items-center justify-center rounded-[14px] bg-blue px-8 text-[16px] font-bold text-ondark"
      >
        Go to my orders
      </Link>
    </div>
  );
}
