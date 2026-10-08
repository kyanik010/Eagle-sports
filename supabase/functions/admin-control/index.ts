import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "npm:@supabase/supabase-js@2";

const cors = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
  "Content-Type": "application/json; charset=utf-8",
};

const PROJECT_URL = Deno.env.get("SUPABASE_URL")!;
const SERVICE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
const admin = createClient(PROJECT_URL, SERVICE_KEY);

function json(data: unknown, status = 200) {
  return new Response(JSON.stringify(data), { status, headers: cors });
}

async function requireAdmin(req: Request) {
  const auth = req.headers.get("Authorization") ?? "";
  if (!auth.startsWith("Bearer ")) throw new Error("unauthorized");
  const token = auth.slice(7);
  const { data: userData, error: userError } = await admin.auth.getUser(token);
  if (userError || !userData.user) throw new Error("unauthorized");
  const { data: row, error } = await admin.from("admin_users")
    .select("user_id").eq("user_id", userData.user.id).maybeSingle();
  if (error || !row) throw new Error("forbidden");
  return userData.user;
}

async function enrichDevices(devices: any[]) {
  const videoIds = [...new Set(devices.map(d => d.video_profile_id).filter(Boolean))];
  const audioIds = [...new Set(devices.map(d => d.audio_profile_id).filter(Boolean))];
  const videoMap = new Map<string, any>();
  const audioMap = new Map<string, any>();
  const hostMap = new Map<string, any>();

  if (videoIds.length) {
    const { data, error } = await admin.from("video_profiles")
      .select("id,name,server_url,username,password,enabled").in("id", videoIds);
    if (error) throw error;
    for (const v of data ?? []) videoMap.set(v.id, v);
    const hostIds = [...new Set((data ?? []).map((v: any) => String(v.name ?? "").match(/\|HOST:([0-9a-f-]{36})$/i)?.[1]).filter(Boolean))];
    if (hostIds.length) {
      const { data: hosts, error: hostError } = await admin.from("video_profiles")
        .select("id,name,server_url,enabled").in("id", hostIds);
      if (hostError) throw hostError;
      for (const h of hosts ?? []) hostMap.set(h.id, h);
    }
  }
  if (audioIds.length) {
    const { data, error } = await admin.from("audio_profiles")
      .select("id,m3u_url,enabled").in("id", audioIds);
    if (error) throw error;
    for (const a of data ?? []) audioMap.set(a.id, a);
  }

  return devices.map(d => ({
    ...d,
    video_config: d.video_profile_id ? (() => {
      const v = videoMap.get(d.video_profile_id);
      if (!v) return null;
      const marker = String(v.name ?? "").match(/\|HOST:([0-9a-f-]{36})$/i);
      const h = marker ? hostMap.get(marker[1]) : null;
      return { ...v, host_id: h?.id ?? null, host_name: h?.name?.replace(/^HOST::/,"") ?? null, host_server_url: h?.server_url ?? v.server_url };
    })() : null,
    audio_config: d.audio_profile_id ? (audioMap.get(d.audio_profile_id) ?? null) : null,
  }));
}

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: cors });
  if (req.method === "GET") return json({ ok: true, service: "admin-control" });
  if (req.method !== "POST") return json({ error: "method_not_allowed" }, 405);

  try {
    await requireAdmin(req);
    const body = await req.json();
    const action = String(body?.action ?? "");

    if (action === "stats") {
      const [{ count: total }, { count: trials }, { count: active }, { count: expired }, { count: suspended }] =
        await Promise.all([
          admin.from("devices").select("id", { count: "exact", head: true }),
          admin.from("devices").select("id", { count: "exact", head: true })
            .eq("subscription_type", "trial").eq("status", "active").gt("expires_at", new Date().toISOString()),
          admin.from("devices").select("id", { count: "exact", head: true })
            .eq("subscription_type", "paid").eq("status", "active").gt("expires_at", new Date().toISOString()),
          admin.from("devices").select("id", { count: "exact", head: true })
            .or("status.eq.expired,and(status.eq.active,expires_at.lte." + new Date().toISOString() + ")"),
          admin.from("devices").select("id", { count: "exact", head: true }).eq("status", "suspended"),
        ]);
      return json({ total: total ?? 0, trials: trials ?? 0, active: active ?? 0, expired: expired ?? 0, suspended: suspended ?? 0 });
    }

    if (action === "list") {
      const filter = String(body?.filter ?? "all");
      const page = Math.max(0, Number(body?.page ?? 0) || 0);
      const limit = Math.min(100, Math.max(1, Number(body?.limit ?? 50) || 50));
      const sortMap: Record<string, string> = {
        last_seen_at: "last_seen_at",
        created_at: "created_at",
        expires_at: "expires_at",
        device_id: "device_id",
        status: "status",
        subscription_type: "subscription_type",
      };
      const requestedSort = String(body?.sort ?? "last_seen_at");
      const sortColumn = sortMap[requestedSort] ?? "last_seen_at";
      const direction = String(body?.direction ?? "desc").toLowerCase() === "asc" ? "asc" : "desc";
      const ascending = direction === "asc";
      const from = page * limit;
      const to = from + limit - 1;

      let query = admin.from("devices").select(
        "id,device_id,mac_address,status,subscription_type,expires_at,trial_started_at,first_registered_at,video_profile_id,audio_profile_id,last_seen_at,created_at,updated_at",
        { count: "exact" }
      ).order(sortColumn, { ascending, nullsFirst: false });

      if (sortColumn === "created_at") {
        query = query.order("id", { ascending: false });
      } else {
        query = query.order("created_at", { ascending: false });
      }
      query = query.range(from, to);

      if (filter === "trial") query = query.eq("subscription_type", "trial").eq("status", "active").gt("expires_at", new Date().toISOString());
      if (filter === "active") query = query.eq("subscription_type", "paid").eq("status", "active").gt("expires_at", new Date().toISOString());
      if (filter === "expired") query = query.or("status.eq.expired,and(status.eq.active,expires_at.lte." + new Date().toISOString() + ")");
      if (filter === "suspended") query = query.eq("status", "suspended");

      const { data, error, count } = await query;
      if (error) throw error;
      return json({ devices: await enrichDevices(data ?? []), total: count ?? 0, page, limit, sort: requestedSort in sortMap ? requestedSort : "last_seen_at", direction });
    }

    if (action === "search") {
      const q = String(body?.query ?? "").trim().toLowerCase();
      if (!q || q.length > 128) return json({ devices: [] });
      const { data, error } = await admin.from("devices").select(
        "id,device_id,mac_address,status,subscription_type,expires_at,trial_started_at,first_registered_at,video_profile_id,audio_profile_id,last_seen_at,created_at,updated_at"
      ).order("created_at", { ascending: false }).limit(5000);
      if (error) throw error;
      const enriched = await enrichDevices(data ?? []);
      const devices = enriched.filter((d: any) => {
        const v = d.video_config ?? {};
        const a = d.audio_config ?? {};
        return [d.device_id,d.mac_address,v.username,v.server_url,v.host_server_url,a.m3u_url].some((x: any) => String(x ?? "").toLowerCase().includes(q));
      });
      return json({ devices });
    }


    if (action === "list_subscriptions") {
      const page = Math.max(0, Number(body?.page ?? 0) || 0);
      const limit = Math.min(100, Math.max(1, Number(body?.limit ?? 50) || 50));
      const queryText = String(body?.query ?? "").trim();
      let query = admin.from("customer_subscriptions")
        .select("id,username,password,status,expires_at,video_host_id,audio_m3u_url,last_seen_at,created_at,updated_at", { count: "exact" })
        .order("last_seen_at", { ascending: false, nullsFirst: false })
        .range(page * limit, page * limit + limit - 1);
      if (["active", "suspended", "expired"].includes(String(body?.filter ?? ""))) query = query.eq("status", String(body.filter));
      if (queryText) query = query.ilike("username", "%" + queryText.replace(/[%_]/g, "") + "%");
      const { data, error, count } = await query;
      if (error) throw error;
      return json({ subscriptions: data ?? [], total: count ?? 0, page, limit });
    }

    if (action === "update_subscription") {
      const username = String(body?.username ?? "").trim();
      const audioM3u = String(body?.audio_m3u_url ?? "").trim();
      if (!username) return json({ error: "username_required" }, 400);
      if (audioM3u) {
        let parsed: URL;
        try { parsed = new URL(audioM3u); } catch { return json({ error: "invalid_audio_url" }, 400); }
        if (!["http:", "https:"].includes(parsed.protocol)) return json({ error: "invalid_audio_url" }, 400);
      }
      const { data, error } = await admin.from("customer_subscriptions")
        .update({ audio_m3u_url: audioM3u || null, updated_at: new Date().toISOString() })
        .eq("username", username)
        .select("id,username,password,status,expires_at,video_host_id,audio_m3u_url,last_seen_at,created_at,updated_at")
        .maybeSingle();
      if (error) throw error;
      if (!data) return json({ error: "customer_not_found" }, 404);
      return json({ ok: true, subscription: data });
    }

    if (action === "list_hosts") {
      const { data, error } = await admin.from("video_profiles")
        .select("id,name,server_url,enabled,created_at,updated_at")
        .ilike("name", "HOST::%")
        .order("created_at", { ascending: true });
      if (error) throw error;
      const hosts = (data ?? []).map((h: any) => ({
        ...h,
        name: String(h.name ?? "").replace(/^HOST::/, ""),
      }));
      return json({ hosts, limit: 13, count: hosts.length });
    }

    if (action === "create_host") {
      const name = String(body?.name ?? "").trim();
      const serverUrl = String(body?.server_url ?? "").trim();
      if (!name || !serverUrl) return json({ error: "أدخل اسم الهوست والرابط." }, 400);
      const { count, error: countError } = await admin.from("video_profiles")
        .select("id", { count: "exact", head: true })
        .ilike("name", "HOST::%");
      if (countError) throw countError;
      if ((count ?? 0) >= 13) return json({ error: "تم الوصول إلى الحد الأقصى: 13 هوست." }, 400);
      const { data: duplicate, error: duplicateError } = await admin.from("video_profiles")
        .select("id").eq("name", "HOST::" + name).maybeSingle();
      if (duplicateError) throw duplicateError;
      if (duplicate) return json({ error: "اسم الهوست موجود مسبقًا." }, 400);
      const { data, error } = await admin.from("video_profiles").insert({
        name: "HOST::" + name,
        server_url: serverUrl,
        username: "",
        password: "",
        enabled: true
      }).select("id,name,server_url,enabled,created_at,updated_at").single();
      if (error) throw error;
      return json({ ok: true, host: { ...data, name: String(data.name).replace(/^HOST::/, "") } });
    }

    if (action === "update_host") {
      const id = String(body?.id ?? "").trim();
      const name = String(body?.name ?? "").trim();
      const serverUrl = String(body?.server_url ?? "").trim();
      const enabled = body?.enabled !== false;
      if (!id || !name || !serverUrl) return json({ error: "أدخل بيانات الهوست كاملة." }, 400);
      const { data: existing, error: existingError } = await admin.from("video_profiles")
        .select("id,name").eq("id", id).ilike("name", "HOST::%").maybeSingle();
      if (existingError) throw existingError;
      if (!existing) return json({ error: "الهوست غير موجود." }, 404);
      const { data: duplicate, error: duplicateError } = await admin.from("video_profiles")
        .select("id").eq("name", "HOST::" + name).neq("id", id).maybeSingle();
      if (duplicateError) throw duplicateError;
      if (duplicate) return json({ error: "اسم الهوست موجود مسبقًا." }, 400);
      const { data, error } = await admin.from("video_profiles").update({
        name: "HOST::" + name,
        server_url: serverUrl,
        username: "",
        password: "",
        enabled,
        updated_at: new Date().toISOString()
      }).eq("id", id).select("id,name,server_url,enabled,created_at,updated_at").single();
      if (error) throw error;
      return json({ ok: true, host: { ...data, name: String(data.name).replace(/^HOST::/, "") } });
    }

    if (action === "delete_host") {
      const id = String(body?.id ?? "").trim();
      if (!id) return json({ error: "invalid_host" }, 400);
      const { data: host, error: hostError } = await admin.from("video_profiles")
        .select("id,name").eq("id", id).ilike("name", "HOST::%").maybeSingle();
      if (hostError) throw hostError;
      if (!host) return json({ error: "الهوست غير موجود." }, 404);
      const { error } = await admin.from("video_profiles").delete().eq("id", id);
      if (error) throw error;
      return json({ ok: true, deleted: 1 });
    }

    if (action === "delete_device") {
      const id = String(body?.id ?? "").trim();
      if (!id) return json({ error: "invalid_device" }, 400);
      const { data: device, error: deviceError } = await admin.from("devices")
        .select("id,video_profile_id,audio_profile_id").eq("id", id).maybeSingle();
      if (deviceError) throw deviceError;
      if (!device) return json({ error: "device_not_found" }, 404);
      const { error: deleteError } = await admin.from("devices").delete().eq("id", id);
      if (deleteError) throw deleteError;
      return json({ ok: true, deleted: 1 });
    }

    if (action === "delete_all") {
      const { count: beforeCount, error: countError } = await admin.from("devices").select("id", { count: "exact", head: true });
      if (countError) throw countError;
      const { error: deleteError } = await admin.from("devices").delete().not("id", "is", null);
      if (deleteError) throw deleteError;
      const { count: remaining, error: remainingError } = await admin.from("devices").select("id", { count: "exact", head: true });
      if (remainingError) throw remainingError;
      if ((remaining ?? 0) !== 0) throw new Error("delete_incomplete: " + remaining + " devices remain");
      return json({ ok: true, deleted: beforeCount ?? 0, remaining: 0 });
    }


    // Independent management commands for the three control domains.
    if (action === "device_update") {
      const id = String(body?.id ?? "").trim();
      if (!id) return json({ error: "invalid_device" }, 400);
      const patch = {
        status: ["active", "suspended", "expired"].includes(body?.status) ? body.status : "suspended",
        expires_at: body?.expires_at || null,
        subscription_type: body?.subscription_type === "trial" ? "trial" : "paid",
        updated_at: new Date().toISOString(),
      };
      const { error } = await admin.from("devices").update(patch).eq("id", id);
      if (error) throw error;
      return json({ ok: true });
    }

    if (action === "host_assign") {
      const deviceId = String(body?.device_id ?? "").trim();
      const hostId = String(body?.host_id ?? "").trim();
      const videoUser = String(body?.username ?? "").trim();
      const videoPass = String(body?.password ?? "");
      if (!deviceId || !hostId || !videoUser || !videoPass) return json({ error: "أكمل Host وUsername وكلمة المرور." }, 400);

      const { data: device, error: deviceError } = await admin.from("devices")
        .select("id,video_profile_id").eq("id", deviceId).maybeSingle();
      if (deviceError) throw deviceError;
      if (!device) return json({ error: "device_not_found" }, 404);

      const { data: host, error: hostError } = await admin.from("video_profiles")
        .select("id,name,server_url,enabled").eq("id", hostId).ilike("name", "HOST::%").maybeSingle();
      if (hostError) throw hostError;
      if (!host || !host.enabled || !host.server_url) return json({ error: "الهوست غير موجود أو موقوف." }, 400);

      const profileName = "device_" + deviceId + "_video|HOST:" + host.id;
      let videoProfileId = device.video_profile_id;
      if (videoProfileId) {
        const { error } = await admin.from("video_profiles").update({
          name: profileName, server_url: host.server_url, username: videoUser,
          password: videoPass, enabled: true, updated_at: new Date().toISOString()
        }).eq("id", videoProfileId);
        if (error) throw error;
      } else {
        const { data, error } = await admin.from("video_profiles").insert({
          name: profileName, server_url: host.server_url, username: videoUser,
          password: videoPass, enabled: true
        }).select("id").single();
        if (error) throw error;
        videoProfileId = data.id;
      }

      const { error: updateError } = await admin.from("devices").update({
        video_profile_id: videoProfileId, updated_at: new Date().toISOString()
      }).eq("id", deviceId);
      if (updateError) throw updateError;
      return json({ ok: true, video_profile_id: videoProfileId, host_id: host.id });
    }

    if (action === "audio_assign") {
      const deviceId = String(body?.device_id ?? "").trim();
      const audioM3u = String(body?.m3u_url ?? "").trim();
      if (!deviceId) return json({ error: "invalid_device" }, 400);

      const { data: device, error: deviceError } = await admin.from("devices")
        .select("id,audio_profile_id").eq("id", deviceId).maybeSingle();
      if (deviceError) throw deviceError;
      if (!device) return json({ error: "device_not_found" }, 404);

      let audioProfileId = device.audio_profile_id;
      if (audioM3u) {
        if (audioProfileId) {
          const { error } = await admin.from("audio_profiles").update({
            m3u_url: audioM3u, enabled: true, updated_at: new Date().toISOString()
          }).eq("id", audioProfileId);
          if (error) throw error;
        } else {
          const audioName = "device_" + deviceId + "_audio";
          const { data: existingAudio, error: existingAudioError } = await admin.from("audio_profiles")
            .select("id").eq("name", audioName).maybeSingle();
          if (existingAudioError) throw existingAudioError;
          if (existingAudio) {
            const { error } = await admin.from("audio_profiles").update({
              m3u_url: audioM3u, enabled: true, updated_at: new Date().toISOString()
            }).eq("id", existingAudio.id);
            if (error) throw error;
            audioProfileId = existingAudio.id;
          } else {
            const { data, error } = await admin.from("audio_profiles").insert({
              name: audioName, m3u_url: audioM3u, enabled: true
            }).select("id").single();
            if (error) throw error;
            audioProfileId = data.id;
          }
        }
      } else if (audioProfileId) {
        const { error } = await admin.from("audio_profiles").update({
          enabled: false, updated_at: new Date().toISOString()
        }).eq("id", audioProfileId);
        if (error) throw error;
      }

      const { error: updateError } = await admin.from("devices").update({
        audio_profile_id: audioProfileId, updated_at: new Date().toISOString()
      }).eq("id", deviceId);
      if (updateError) throw updateError;
      return json({ ok: true, audio_profile_id: audioProfileId });
    }

    if (action === "configure_device") {
      const deviceId = String(body?.device_id ?? "").trim();
      const video = body?.video ?? {};
      const audio = body?.audio ?? {};
      if (!deviceId) return json({ error: "invalid_device" }, 400);

      const { data: device, error: deviceError } = await admin.from("devices")
        .select("id,video_profile_id,audio_profile_id").eq("id", deviceId).single();
      if (deviceError || !device) return json({ error: "device_not_found" }, 404);

      const hostId = String(video?.host_id ?? "").trim();
      const videoUser = String(video?.username ?? "").trim();
      const videoPass = String(video?.password ?? "");
      const audioM3u = String(audio?.m3u_url ?? "").trim();
      if (!hostId || !videoUser || !videoPass) return json({ error: "أكمل Host وUsername وكلمة المرور." }, 400);

      const { data: host, error: hostError } = await admin.from("video_profiles")
        .select("id,name,server_url,enabled").eq("id", hostId).ilike("name", "HOST::%").maybeSingle();
      if (hostError) throw hostError;
      if (!host || !host.enabled || !host.server_url) return json({ error: "الهوست غير موجود أو موقوف." }, 400);

      let videoProfileId = device.video_profile_id;
      const profileName = "device_" + deviceId + "_video|HOST:" + host.id;
      if (videoProfileId) {
        const { error } = await admin.from("video_profiles").update({
          name: profileName,
          server_url: host.server_url,
          username: videoUser,
          password: videoPass,
          enabled: true,
          updated_at: new Date().toISOString()
        }).eq("id", videoProfileId);
        if (error) throw error;
      } else {
        const { data, error } = await admin.from("video_profiles").insert({
          name: profileName, server_url: host.server_url, username: videoUser, password: videoPass, enabled: true
        }).select("id").single();
        if (error) throw error;
        videoProfileId = data.id;
      }

      let audioProfileId = device.audio_profile_id;
      if (audioM3u) {
        if (audioProfileId) {
          const { error } = await admin.from("audio_profiles").update({
            m3u_url: audioM3u, enabled: true, updated_at: new Date().toISOString()
          }).eq("id", audioProfileId);
          if (error) throw error;
        } else {
          const audioName = "device_" + deviceId + "_audio";
          const { data: existingAudio, error: existingAudioError } = await admin.from("audio_profiles")
            .select("id").eq("name", audioName).maybeSingle();
          if (existingAudioError) throw existingAudioError;
          if (existingAudio) {
            const { error } = await admin.from("audio_profiles").update({
              m3u_url: audioM3u, enabled: true, updated_at: new Date().toISOString()
            }).eq("id", existingAudio.id);
            if (error) throw error;
            audioProfileId = existingAudio.id;
          } else {
            const { data, error } = await admin.from("audio_profiles").insert({
              name: audioName, m3u_url: audioM3u, enabled: true
            }).select("id").single();
            if (error) throw error;
            audioProfileId = data.id;
          }
        }
      } else if (audioProfileId) {
        const { error } = await admin.from("audio_profiles").update({
          enabled: false, updated_at: new Date().toISOString()
        }).eq("id", audioProfileId);
        if (error) throw error;
      }

      const patch = {
        status: ["active","suspended","expired"].includes(body?.status) ? body.status : "active",
        expires_at: body?.expires_at || null,
        video_profile_id: videoProfileId,
        audio_profile_id: audioProfileId,
        subscription_type: "paid",
        updated_at: new Date().toISOString(),
      };
      const { error: updateError } = await admin.from("devices").update(patch).eq("id", deviceId);
      if (updateError) throw updateError;
      return json({ ok: true, activated: patch.status === "active" });
    }

    if (action === "update") {
      const id = String(body?.id ?? "");
      if (!id) return json({ error: "invalid_device" }, 400);
      const patch = {
        status: ["active", "suspended", "expired"].includes(body?.status) ? body.status : "suspended",
        expires_at: body?.expires_at || null,
        video_profile_id: body?.video_profile_id || null,
        audio_profile_id: body?.audio_profile_id || null,
        subscription_type: body?.subscription_type === "trial" ? "trial" : "paid",
        updated_at: new Date().toISOString(),
      };
      const { error } = await admin.from("devices").update(patch).eq("id", id);
      if (error) throw error;
      return json({ ok: true });
    }

    return json({ error: "unknown_action" }, 400);
  } catch (e) {
    const msg = e instanceof Error ? e.message : "server_error";
    const status = msg === "forbidden" ? 403 : 401;
    return json({ error: msg === "forbidden" ? "forbidden" : msg === "unauthorized" ? "unauthorized" : "server_error" }, status);
  }
});