export const CATEGORY_KEYS = [
  "flights",
  "stays",
  "food",
  "activities",
  "transport",
  "shopping",
  "other",
] as const;

export type CategoryKey = (typeof CATEGORY_KEYS)[number];

export type Trip = {
  id: string;
  user_id: string;
  name: string;
  destination: string | null;
  start_date: string | null;
  end_date: string | null;
  budget: number;
  currency: string;
  accent: string;
  share_code: string | null;
  /** Storage path of the generated destination shot, once there is one. */
  cover_path: string | null;
  /** The string the cover was drawn from, so we can tell when it went stale. */
  cover_subject: string | null;
  cover_status: CoverStatus;
  cover_claimed_at: string | null;
  created_at: string;
};

/** Where a trip's cover is in its lifecycle. Written only by the server. */
export type CoverStatus = "idle" | "pending" | "ready" | "failed";

export type Expense = {
  id: string;
  trip_id: string;
  user_id: string;
  title: string;
  /** Always denominated in the trip's currency — every total reads this. */
  amount: number;
  /** What was actually typed in, when it differed from the trip currency. */
  original_amount: number | null;
  original_currency: string | null;
  fx_rate: number | null;
  category: CategoryKey;
  spent_on: string;
  note: string | null;
  created_at: string;
};

export type TripMemberRow = {
  trip_id: string;
  user_id: string;
  role: "owner" | "member";
  joined_at: string;
};

export type TripMember = TripMemberRow & {
  display_name: string | null;
  is_you: boolean;
};

export type TripInput = Omit<
  Trip,
  "id" | "user_id" | "created_at" | "share_code" | `cover_${string}`
>;
export type ExpenseInput = Omit<Expense, "id" | "user_id" | "created_at">;

/** Minimal hand-written shape so the client is typed without codegen. */
export type Database = {
  public: {
    Tables: {
      trips: {
        Row: Trip;
        Insert: Partial<Trip> & { user_id: string; name: string };
        Update: Partial<Trip>;
        Relationships: [];
      };
      expenses: {
        Row: Expense;
        Insert: Partial<Expense> & { trip_id: string; user_id: string; title: string; amount: number };
        Update: Partial<Expense>;
        Relationships: [];
      };
      trip_members: {
        Row: TripMemberRow;
        Insert: TripMemberRow;
        Update: Partial<TripMemberRow>;
        Relationships: [];
      };
      profiles: {
        Row: { id: string; display_name: string | null; home_currency: string; created_at: string };
        Insert: { id: string; display_name?: string | null; home_currency?: string };
        Update: { display_name?: string | null; home_currency?: string };
        Relationships: [];
      };
    };
    Views: { trip_summaries: { Row: Trip & { spent: number; entries: number }; Relationships: [] } };
    Functions: {
      join_trip: {
        Args: { p_code: string };
        Returns: string;
      };
      claim_trip_cover: {
        Args: { p_trip: string };
        Returns: boolean;
      };
      set_trip_cover: {
        Args: { p_trip: string; p_path: string | null; p_subject: string | null };
        Returns: undefined;
      };
    };
    Enums: Record<never, never>;
    CompositeTypes: Record<never, never>;
  };
};
