import type { NextConfig } from "next";

// /api/laundry/* is proxied to the Express backend by the route handler in
// src/app/api/laundry/[...path]/route.ts (it strips the browser's Origin
// header so the backend's CORS allowlist never has to know this site).

const nextConfig: NextConfig = {
  reactStrictMode: true,
};

export default nextConfig;
