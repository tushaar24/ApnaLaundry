import type { Service } from "./models";

/**
 * Default rate card for a NEW account (port of domain/SeedData.kt services).
 * Matches the Android app's first-login bootstrap: shop defaults + these
 * services only — no demo customers/orders (see sync-and-auth spec).
 */

function items(pairs: [string, number][]) {
  return pairs.map(([name, price]) => ({ name, price }));
}

export const DEFAULT_EXPRESS_PCT = 50;

export const seedServices: Service[] = [
  {
    id: "wf", name: "Wash & Fold", mode: "WEIGHT",
    ratePerKg: 60, minKg: 3.0, readyInDays: null, lockedToPiece: false, sortOrder: 0,
    items: items([["Shirt", 15], ["T-shirt", 12], ["Pant", 20], ["Jeans", 25], ["Kurta", 20], ["Saree", 50], ["Bedsheet", 40], ["Blanket", 100]]),
  },
  {
    id: "wi", name: "Wash & Iron", mode: "PIECE",
    ratePerKg: null, minKg: null, readyInDays: null, lockedToPiece: false, sortOrder: 1,
    items: items([["Shirt", 25], ["T-shirt", 20], ["Pant", 30], ["Jeans", 35], ["Kurta", 30], ["Saree", 80], ["Bedsheet", 60], ["Blanket", 150]]),
  },
  {
    id: "io", name: "Iron Only", mode: "PIECE",
    ratePerKg: null, minKg: null, readyInDays: null, lockedToPiece: false, sortOrder: 2,
    items: items([["Shirt", 10], ["T-shirt", 8], ["Pant", 12], ["Jeans", 15], ["Kurta", 12], ["Saree", 40], ["Bedsheet", 25], ["Blanket", 60]]),
  },
  {
    id: "dc", name: "Dry Cleaning", mode: "PIECE",
    ratePerKg: null, minKg: null, readyInDays: null, lockedToPiece: true, sortOrder: 3,
    items: items([["Suit", 350], ["Blazer", 250], ["Saree", 180], ["Lehenga", 450], ["Sherwani", 400], ["Jacket", 250], ["Shirt", 60], ["Pant", 80]]),
  },
];
