import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "npm:@supabase/supabase-js@2";

const headers = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
  "Content-Type": "application/json; charset=utf-8",
};
const respond = (data: unknown, status = 200) =>
  new Response(JSON.stringify(data), { status, headers });

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers });
  if (req.method !== "POST") return respond({ error: "method_not_allowed" }, 405);

  try {
    const body = await req.json();
    const username = String(body?.username ?? "").trim();
    const password = String(body?.password ?? "");
    if (!username || !password) return respond({ activated: false, status: "credentials_required", config: null }, 400);
    if (username.length > 128 || password.length > 256) return respond({ activated: false, status: "invalid_credentials", config: null }, 400);

    const db = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!);
    const { data: saved, error: savedError } = await db.from("customer_subscriptions")
      .select("status,password,expires_at,video_host_id,audio_m3u_url").eq("username", username).maybeSingle();
    if (savedError) throw savedError;
    const { data: enabledHosts, error: hostsError } = await db.from("video_profiles")
      .select("id,name,server_url,enabled").ilike("name", "HOST::%")
      .eq("enabled", true).order("created_at", { ascending: true });
    if (hostsError) throw hostsError;
    let hosts: any[] = enabledHosts ?? [];
    // Prefer the customer's last successful host, but fall back to all other enabled hosts.
    if (saved?.video_host_id) {
      hosts = hosts.sort((a, b) =>
        Number(b.id === saved.video_host_id) - Number(a.id === saved.video_host_id)
      );
    }
    if (!hosts.length) return respond({ activated: false, status: "no_enabled_hosts", config: null }, 503);

    let match: any = null;
    let account: any = null;
    let responses = 0;
    for (const host of hosts) {
      let base = String(host.server_url ?? "").trim();
      while (base.endsWith("/")) base = base.slice(0, -1);
      if (!base) continue;
      try {
        const url = new URL(base + "/player_api.php");
        url.searchParams.set("username", username);
        url.searchParams.set("password", password);
        const res = await fetch(url, { signal: AbortSignal.timeout(8000) });
        responses++;
        if (!res.ok) continue;
        const payload = await res.json();
        const info = payload?.user_info;
        if (Number(info?.auth) === 1 && String(info?.status ?? "").toLowerCase() === "active") {
          match = host;
          account = info;
          break;
        }
      } catch (_) { /* Continue to the next video host. */ }
    }
    if (!match || !account) {
      return respond({ activated: false, status: responses ? "invalid_credentials" : "host_unreachable", config: null }, responses ? 401 : 503);
    }

    const rawExpiry = Number(account.exp_date ?? 0);
    const expiry = Number.isFinite(rawExpiry) && rawExpiry > 0 ? new Date(rawExpiry * 1000).toISOString() : null;
    const now = new Date().toISOString();
    if (expiry && Date.parse(expiry) <= Date.now()) {
      const { error } = await db.from("customer_subscriptions").upsert({
        username, password, status: "expired", expires_at: expiry, video_host_id: match.id,
        audio_m3u_url: saved?.audio_m3u_url ?? null, last_seen_at: now, updated_at: now,
      }, { onConflict: "username" });
      if (error) throw error;
      return respond({ activated: false, status: "expired", expires_at: expiry, config: null }, 403);
    }

    const { error: saveError } = await db.from("customer_subscriptions").upsert({
      username, password, status: "active", expires_at: expiry, video_host_id: match.id,
      audio_m3u_url: saved?.audio_m3u_url ?? null, last_seen_at: now, updated_at: now,
    }, { onConflict: "username" });
    if (saveError) throw saveError;

    return respond({
      activated: true, status: "active", expires_at: expiry,
      config: { video: { server_url: match.server_url, username, password }, ...(String(saved?.audio_m3u_url ?? "").trim() ? { audio: { m3u_url: String(saved.audio_m3u_url).trim() } } : {}) },
    });
  } catch (_) {
    return respond({ error: "server_error" }, 500);
  }
});