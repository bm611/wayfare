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
};

export const CATEGORIES: Record<CategoryKey, CategoryMeta> = {
  flights:    { key: "flights",    label: "Flights",    icon: AirplaneTilt },
  stays:      { key: "stays",      label: "Stays",      icon: Bed },
  food:       { key: "food",       label: "Food",       icon: ForkKnife },
  activities: { key: "activities", label: "Activities", icon: Binoculars },
  transport:  { key: "transport",  label: "Transport",  icon: Car },
  shopping:   { key: "shopping",   label: "Shopping",   icon: ShoppingBag },
  other:      { key: "other",      label: "Other",      icon: DotsThreeOutline },
};

export const CATEGORY_LIST = Object.values(CATEGORIES);
