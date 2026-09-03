import {
  AirplaneTilt,
  Bed,
  ForkKnife,
  Binoculars,
  Car,
  ShoppingBag,
  DotsThreeOutline,
} from "@phosphor-icons/react";
import type { Icon } from "@phosphor-icons/react";
import type { CategoryKey } from "./types";

export type CategoryMeta = {
  key: CategoryKey;
  label: string;
  icon: Icon;
  /** Solid ink for dots, bars and icons. */
  color: string;
  /** Paper-toned wash for chips and icon plates. */
  wash: string;
};

export const CATEGORIES: Record<CategoryKey, CategoryMeta> = {
  flights:    { key: "flights",    label: "Flights",    icon: AirplaneTilt,     color: "#40697d", wash: "#e2eaee" },
  stays:      { key: "stays",      label: "Stays",      icon: Bed,              color: "#8a5a44", wash: "#efe2da" },
  food:       { key: "food",       label: "Food",       icon: ForkKnife,        color: "#a8761f", wash: "#f2e7cf" },
  activities: { key: "activities", label: "Activities", icon: Binoculars,       color: "#55713f", wash: "#e4ebdc" },
  transport:  { key: "transport",  label: "Transport",  icon: Car,              color: "#66628a", wash: "#e6e4ee" },
  shopping:   { key: "shopping",   label: "Shopping",   icon: ShoppingBag,      color: "#9d4f61", wash: "#f0e0e4" },
  other:      { key: "other",      label: "Other",      icon: DotsThreeOutline, color: "#7a736a", wash: "#eae5dd" },
};

export const CATEGORY_LIST = Object.values(CATEGORIES);
