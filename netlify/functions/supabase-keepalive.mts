import { createClient } from "@supabase/supabase-js";

/**
 * Supabase pauses free-plan projects after about a week without activity.
 * Netlify runs this once a day so the database always sees a real query.
 *
 * It signs in as nobody and uses the publishable key, so row level security
 * hands back an empty list. That is fine: Postgres still has to run the query,
 * which is what counts as activity.
 */
export default async (): Promise<Response> => {
  const supabaseUrl = process.env.SUPABASE_URL ?? process.env.VITE_SUPABASE_URL;
  const supabaseKey =
    process.env.SUPABASE_PUBLISHABLE_KEY ?? process.env.VITE_SUPABASE_PUBLISHABLE_KEY;

  if (!supabaseUrl || !supabaseKey) {
    console.error("supabase-keepalive: missing configuration, refusing to run");
    return new Response("missing configuration", { status: 500 });
  }

  const supabase = createClient(supabaseUrl, supabaseKey, {
    auth: { persistSession: false, autoRefreshToken: false },
  });

  const { error } = await supabase.from("trips").select("id").limit(1);
  if (error) {
    console.error("supabase-keepalive: ping failed", error.message);
    return new Response("ping failed", { status: 502 });
  }

  console.log("supabase-keepalive: ok");
  return new Response("ok");
};

export const config = {
  schedule: "@daily",
};
