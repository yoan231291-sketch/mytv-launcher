// Cuentas Claras — servidor (Supabase Edge Function)
// Todo pasa por aquí: la clave secreta del link (k) decide a qué libreta se entra.
// Las tablas tienen RLS sin políticas: nadie entra directo, solo esta función.
import { createClient } from "jsr:@supabase/supabase-js@2";
import webpush from "npm:web-push@3.6.7";

const sb = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!);
const CORS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "content-type, apikey, authorization, x-client-info",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};
const json = (b: unknown, s = 200) => new Response(JSON.stringify(b), { status: s, headers: { ...CORS, "Content-Type": "application/json" } });
const TIPOS = ["medias", "completo", "pago"];
const WHO = ["yoan", "arelis"];
const r2 = (n: number) => Math.round(n * 100) / 100;

function delta(tipo: string, pago: string, a: number) {
  if (tipo === "medias") return pago === "arelis" ? a / 2 : -a / 2;
  if (tipo === "completo") return pago === "arelis" ? a : -a;
  return pago === "yoan" ? -a : a; // pago
}
const out = (x: any) => ({ id: x.id, fecha: x.fecha, desc: x.descr, monto: Number(x.monto), tipo: x.tipo, pago: x.pago, delta: Number(x.delta), cat: x.cat, por: x.por, ts: Number(x.ts), cubre: x.cubre || [] });

const n2 = (v: any) => (v === null || v === undefined || v === "" ? null : r2(Number(v)));
const rOut = (x: any) => ({ id: x.id, fecha: x.fecha, tienda: x.tienda, subtotal: x.subtotal === null ? null : Number(x.subtotal), tax: x.tax === null ? null : Number(x.tax),
  total: Number(x.total), items: x.items || [], estado: x.estado, pago: x.pago, mov_id: x.mov_id, nota: x.nota, por: x.por, ts: Number(x.ts) });
// Bills por pagar: no tocan la deuda hasta que se marcan pagados.
const bOut = (x: any) => ({ id: x.id, nombre: x.nombre, total: Number(x.total), parte_yoan: Number(x.parte_yoan), vence: x.vence,
  pagado: !!x.pagado, pago: x.pago, mov_id: x.mov_id, nota: x.nota, por: x.por, ts: Number(x.ts) });
const isDate = (s: any) => /^\d{4}-\d{2}-\d{2}$/.test(String(s || ""));
const isId = (s: any) => /^[0-9a-f-]{36}$/i.test(String(s || ""));
function cleanItems(arr: any) {
  if (!Array.isArray(arr)) return [];
  return arr.slice(0, 300).map((i: any) => ({ c: String(i?.c || "").slice(0, 40), n: String(i?.n || i?.c || "").slice(0, 80), p: r2(Number(i?.p) || 0) }))
    .filter((i: any) => i.n && isFinite(i.p));
}
const okFoto = (d: any) => typeof d === "string" && d.length > 100 && d.length < 3_000_000 && /^[A-Za-z0-9+/=]+$/.test(d);

/* ---------- Avisos (Web Push) al otro teléfono ---------- */
let VAPID: { pub: string; priv: string } | null = null;
async function vapid() {
  if (VAPID) return VAPID;
  const { data } = await sb.from("cc_config").select("key,value").in("key", ["vapid_public", "vapid_private"]);
  const m: Record<string, string> = {}; for (const r of data || []) m[r.key] = r.value;
  if (!m.vapid_public || !m.vapid_private) return null;
  webpush.setVapidDetails("https://yoan231291-sketch.github.io/mytv-launcher/cuentas/", m.vapid_public, m.vapid_private);
  VAPID = { pub: m.vapid_public, priv: m.vapid_private }; return VAPID;
}
const NOM: Record<string, string> = { yoan: "Yoan", arelis: "La Tía" };
const usd = (n: number) => "$" + (Math.round(Math.abs(n) * 100) / 100).toFixed(2).replace(/\.00$/, "");
const MES = ["ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic"];
const fechaTxt = (f: string) => { const [, m, d] = f.split("-"); return `${Number(d)} ${MES[Number(m) - 1]}`; };
async function saldoTxt(hid: string) {
  const { data } = await sb.from("cc_movs").select("delta").eq("household_id", hid);
  const b = r2((data || []).reduce((a: number, x: any) => a + Number(x.delta), 0));
  return Math.abs(b) < 0.005 ? "¡Tamos echa'o! 🎉" : b > 0 ? `Yoan le debe a la Tía ${usd(b)}` : `La Tía le debe a Yoan ${usd(b)}`;
}
async function notify(hid: string, actor: string, title: string, body: string, tag = "cc") {
  if (!WHO.includes(actor)) return;
  const target = actor === "yoan" ? "arelis" : "yoan";
  try {
    if (!(await vapid())) return;
    const { data: subs } = await sb.from("cc_push").select("id,sub").eq("household_id", hid).eq("quien", target);
    for (const s of subs || []) {
      try { await webpush.sendNotification(s.sub, JSON.stringify({ title, body: body.slice(0, 180), tag }), { TTL: 86400, urgency: "high" }); }
      catch (e: any) { if (e?.statusCode === 404 || e?.statusCode === 410) await sb.from("cc_push").delete().eq("id", s.id); else console.error("push", e?.statusCode, e?.body || String(e)); }
    }
  } catch (e) { console.error("notify", String(e)); }
}
const later = (p: Promise<unknown>) => { const er = (globalThis as any).EdgeRuntime; if (er?.waitUntil) er.waitUntil(p); else return p; };

function parseJsonReply(text: string) {
  const s = text.indexOf("{"), e = text.lastIndexOf("}");
  try { return JSON.parse(text.slice(s, e + 1)); } catch { return { respuesta: text.slice(0, 500), movimientos: [] }; }
}
// Gemini (Google, tiene plan gratis). Se usa si existe GEMINI_API_KEY.
async function askGemini(key: string, system: string, messages: any[], image: string | null) {
  const contents = messages.map((m: any, i: number) => {
    const parts: any[] = [{ text: typeof m.content === "string" ? m.content : "" }];
    if (image && i === messages.length - 1) parts.unshift({ inline_data: { mime_type: "image/jpeg", data: image } });
    return { role: m.role === "assistant" ? "model" : "user", parts };
  });
  const req = { system_instruction: { parts: [{ text: system }] }, contents, generationConfig: { responseMimeType: "application/json", maxOutputTokens: 8000, temperature: 0.2 } };
  for (const model of [Deno.env.get("GEMINI_MODEL") || "gemini-flash-latest", "gemini-2.5-flash", "gemini-flash-lite-latest"]) {
    const r = await fetch(`https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent`, {
      method: "POST", headers: { "content-type": "application/json", "x-goog-api-key": key }, body: JSON.stringify(req),
    });
    if (r.status === 404) continue;
    if (!r.ok) { const t = await r.text(); console.error("gemini", model, r.status, t.slice(0, 300)); if (r.status === 429) return { noai: true, limit: true }; continue; }
    const j = await r.json();
    const text = (j.candidates?.[0]?.content?.parts || []).map((p: any) => p.text || "").join("");
    if (text) return parseJsonReply(text);
  }
  return { noai: true };
}

async function ask(body: any) {
  const gkey = Deno.env.get("GEMINI_API_KEY");
  const key = Deno.env.get("ANTHROPIC_API_KEY");
  if (!key && !gkey) return { noai: true };
  const turns = Array.isArray(body.turns) ? body.turns.slice(-9) : [];
  const messages: any[] = [];
  for (const t of turns) {
    if (!t || !["user", "assistant"].includes(t.role) || typeof t.content !== "string" || !t.content.trim()) continue;
    const content = t.content.slice(0, 4000);
    const last = messages[messages.length - 1];
    if (last && last.role === t.role) last.content += "\n" + content; else messages.push({ role: t.role, content });
  }
  while (messages.length && messages[0].role !== "user") messages.shift();
  if (!messages.length || messages[messages.length - 1].role !== "user") return { error: "turnos" };
  const img = typeof body.image === "string" && body.image.length < 4_000_000 && /^[A-Za-z0-9+/=]+$/.test(body.image) ? body.image : null;
  if (gkey) return await askGemini(gkey, String(body.system || "").slice(0, 12000), messages, img);
  if (img) {
    const last = messages[messages.length - 1];
    last.content = [{ type: "image", source: { type: "base64", media_type: "image/jpeg", data: img } }, { type: "text", text: last.content }];
  }
  const r = await fetch("https://api.anthropic.com/v1/messages", {
    method: "POST",
    headers: { "x-api-key": key, "anthropic-version": "2023-06-01", "content-type": "application/json" },
    body: JSON.stringify({ model: Deno.env.get("CC_MODEL") || "claude-haiku-5-5", max_tokens: 4000, system: String(body.system || "").slice(0, 12000), messages }),
  });
  if (!r.ok) { const t = await r.text(); console.error("anthropic", r.status, t.slice(0, 300)); return { noai: true, detail: r.status, msg: t.slice(0, 200) }; }
  const j = await r.json();
  const text = (j.content || []).filter((c: any) => c.type === "text").map((c: any) => c.text).join("");
  return parseJsonReply(text);
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: CORS });
  if (req.method !== "POST") return json({ error: "método" }, 405);
  let body: any;
  try { body = await req.json(); } catch { return json({ error: "json" }, 400); }
  const k = String(body.k || "");
  if (k.length < 20) return json({ error: "no autorizado" }, 401);
  const { data: h } = await sb.from("cc_household").select("id").eq("token", k).maybeSingle();
  if (!h) return json({ error: "no autorizado" }, 401);
  const yo = WHO.includes(body.yo) ? body.yo : "";

  try {
    switch (body.action) {
      case "list": {
        const { data, error } = await sb.from("cc_movs").select("*").eq("household_id", h.id).order("fecha").order("ts").limit(3000);
        if (error) throw error;
        const { data: fr } = await sb.from("cc_frases").select("id,quien,texto,por,ts").eq("household_id", h.id).order("ts").limit(300);
        const { data: rc } = await sb.from("cc_recibos").select("*").eq("household_id", h.id).order("fecha").limit(1000);
        const { data: fc } = await sb.from("cc_fotos").select("recibo_id").eq("household_id", h.id);
        const nf: Record<string, number> = {};
        for (const f of fc || []) nf[f.recibo_id] = (nf[f.recibo_id] || 0) + 1;
        const { data: ms } = await sb.from("cc_mensajes").select("id,de,texto,ref_tipo,ref_id,ref_txt,ts").eq("household_id", h.id).order("ts", { ascending: false }).limit(300);
        const { data: bl } = await sb.from("cc_bills").select("*").eq("household_id", h.id).order("vence").limit(500);
        return json({
          vapid: (await vapid())?.pub || null,
          mensajes: (ms || []).reverse().map((m: any) => ({ ...m, ts: Number(m.ts) })),
          movs: (data || []).map(out),
          frases: (fr || []).map((f: any) => ({ ...f, ts: Number(f.ts) })),
          recibos: (rc || []).map(rOut).map((r: any) => ({ ...r, fotos: nf[r.id] || 0 })),
          bills: (bl || []).map(bOut),
        });
      }
      case "add": {
        const m = body.mov || {};
        const monto = r2(Number(m.monto));
        if (!TIPOS.includes(m.tipo) || !WHO.includes(m.pago) || !(monto > 0 && monto < 100000)) return json({ error: "datos" }, 400);
        const fecha = /^\d{4}-\d{2}-\d{2}$/.test(m.fecha || "") ? m.fecha : new Date().toISOString().slice(0, 10);
        const row = {
          household_id: h.id, fecha, descr: String(m.desc || "").slice(0, 80), monto, tipo: m.tipo, pago: m.pago,
          delta: r2(delta(m.tipo, m.pago, monto)), cat: Array.from(String(m.cat || "")).slice(0, 2).join(""),
          por: WHO.includes(m.por) ? m.por : null, ts: Date.now(),
          // un pago puede decir qué deudas cubre: [{ id, m }]
          cubre: m.tipo === "pago" && Array.isArray(m.cubre) ? m.cubre.slice(0, 60).map((c: any) => ({ id: String(c?.id || ""), m: r2(Number(c?.m) || 0) }))
            .filter((c: any) => /^[0-9a-f-]{36}$/i.test(c.id) && c.m > 0) : [],
        };
        const { data, error } = await sb.from("cc_movs").insert(row).select().single();
        if (error) throw error;
        const who = row.por || yo;
        await later((async () => notify(h.id, who, "💰 Cuentas Claras", `${NOM[who] || "Alguien"} anotó: ${row.descr} (${usd(monto)}). ${await saldoTxt(h.id)}`, "mov"))());
        return json(out(data));
      }
      case "del": {
        const { data: old } = await sb.from("cc_movs").select("descr,monto").eq("household_id", h.id).eq("id", String(body.id || "")).maybeSingle();
        const { error } = await sb.from("cc_movs").delete().eq("household_id", h.id).eq("id", String(body.id || ""));
        if (error) throw error;
        // Si ese movimiento era el pago de un bill, el bill vuelve a "por pagar".
        await sb.from("cc_bills").update({ pagado: false, pago: null, mov_id: null }).eq("household_id", h.id).eq("mov_id", String(body.id || ""));
        if (old) await later((async () => notify(h.id, yo, "🗑️ Cuentas Claras", `${NOM[yo] || "Alguien"} borró: ${old.descr} (${usd(Number(old.monto))}). ${await saldoTxt(h.id)}`, "mov"))());
        return json({ ok: true });
      }
      /* ---------- Bills por pagar ---------- */
      case "bill_add":
      case "bill_edit": {
        const b = body.bill || {};
        const total = r2(Number(b.total)), py = r2(Number(b.parte_yoan));
        const nombre = String(b.nombre || "").trim().slice(0, 60);
        if (!nombre || !(total > 0 && total < 100000) || !(py >= 0 && py <= total) || !isDate(b.vence)) return json({ error: "datos" }, 400);
        const fields = { nombre, total, parte_yoan: py, vence: b.vence, nota: String(b.nota || "").slice(0, 200) };
        const who = WHO.includes(body.por) ? body.por : yo;
        if (body.action === "bill_edit") {
          const { data, error } = await sb.from("cc_bills").update(fields).eq("household_id", h.id).eq("id", String(body.id || "")).eq("pagado", false).select().single();
          if (error) throw error;
          return json(bOut(data));
        }
        const { data, error } = await sb.from("cc_bills").insert({ ...fields, household_id: h.id, por: who || null, ts: Date.now() }).select().single();
        if (error) throw error;
        await later(notify(h.id, who, "📅 Cuentas Claras", `${NOM[who] || "Alguien"} anotó un bill: ${nombre} ${usd(total)} (Yoan ${usd(py)} · Tía ${usd(total - py)}), vence el ${fechaTxt(fields.vence)}`, "bill"));
        return json(bOut(data));
      }
      case "bill_pay": {
        // pago = quién le pagó a la compañía. El otro queda debiendo su parte.
        const pago = String(body.pago || "");
        if (!WHO.includes(pago)) return json({ error: "datos" }, 400);
        const { data: b } = await sb.from("cc_bills").select("*").eq("household_id", h.id).eq("id", String(body.id || "")).maybeSingle();
        if (!b) return json({ error: "bill" }, 404);
        if (b.pagado) return json(bOut(b));
        const total = Number(b.total), py = Number(b.parte_yoan);
        const debe = r2(pago === "yoan" ? total - py : py); // la parte del que NO pagó
        const fecha = isDate(body.fecha) ? body.fecha : new Date().toISOString().slice(0, 10);
        const who = WHO.includes(body.por) ? body.por : yo;
        let movId: string | null = null, mov: any = null;
        if (debe > 0) {
          const row = {
            household_id: h.id, fecha, descr: `${b.nombre} (bill de ${usd(total)})`.slice(0, 80), monto: debe, tipo: "completo", pago,
            delta: r2(delta("completo", pago, debe)), cat: "📅", por: who || null, ts: Date.now(), cubre: [],
          };
          const { data, error } = await sb.from("cc_movs").insert(row).select().single();
          if (error) throw error;
          movId = data.id; mov = out(data);
        }
        const { data: nb, error } = await sb.from("cc_bills").update({ pagado: true, pago, mov_id: movId }).eq("household_id", h.id).eq("id", b.id).select().single();
        if (error) throw error;
        await later((async () => notify(h.id, who, "📅 Cuentas Claras",
          `${NOM[pago]} pagó ${b.nombre} (${usd(total)}).${debe > 0 ? ` ${pago === "yoan" ? "La Tía" : "Yoan"} le debe su parte: ${usd(debe)}.` : ""} ${await saldoTxt(h.id)}`, "bill"))());
        return json({ bill: bOut(nb), mov });
      }
      case "bill_unpay": {
        const { data: b } = await sb.from("cc_bills").select("*").eq("household_id", h.id).eq("id", String(body.id || "")).maybeSingle();
        if (!b) return json({ error: "bill" }, 404);
        if (b.mov_id) await sb.from("cc_movs").delete().eq("household_id", h.id).eq("id", b.mov_id);
        const { data: nb, error } = await sb.from("cc_bills").update({ pagado: false, pago: null, mov_id: null }).eq("household_id", h.id).eq("id", b.id).select().single();
        if (error) throw error;
        return json({ bill: bOut(nb), mov_id: b.mov_id });
      }
      case "bill_del": {
        const { data: b } = await sb.from("cc_bills").select("pagado").eq("household_id", h.id).eq("id", String(body.id || "")).maybeSingle();
        if (b?.pagado) return json({ error: "pagado" }, 400); // primero hay que deshacer el pago
        const { error } = await sb.from("cc_bills").delete().eq("household_id", h.id).eq("id", String(body.id || ""));
        if (error) throw error;
        return json({ ok: true });
      }
      case "frase_add": {
        const quien = String(body.quien || ""), texto = String(body.texto || "").trim().slice(0, 80);
        if (!WHO.includes(quien) || !texto) return json({ error: "datos" }, 400);
        const { data, error } = await sb.from("cc_frases").insert({ household_id: h.id, quien, texto, por: WHO.includes(body.por) ? body.por : null, ts: Date.now() }).select("id,quien,texto,por,ts").single();
        if (error) throw error;
        const who = WHO.includes(body.por) ? body.por : yo;
        await later(notify(h.id, who, "💬 Cuentas Claras", `${NOM[who] || "Alguien"} puso a ${quien === "yoan" ? "Yoan" : "la Tía"} a decir: "${texto}"`, "frase"));
        return json({ ...data, ts: Number(data.ts) });
      }
      case "frase_del": {
        const { error } = await sb.from("cc_frases").delete().eq("household_id", h.id).eq("id", String(body.id || ""));
        if (error) throw error;
        return json({ ok: true });
      }
      case "recibo_add": {
        const r = body.recibo || {};
        const total = r2(Number(r.total));
        if (!(total > 0 && total < 100000)) return json({ error: "datos" }, 400);
        const row = {
          household_id: h.id, fecha: /^\d{4}-\d{2}-\d{2}$/.test(r.fecha || "") ? r.fecha : new Date().toISOString().slice(0, 10),
          tienda: String(r.tienda || "").slice(0, 60), subtotal: n2(r.subtotal), tax: n2(r.tax), total, items: cleanItems(r.items),
          estado: ["pendiente", "cuenta", "nota"].includes(r.estado) ? r.estado : "pendiente", pago: WHO.includes(r.pago) ? r.pago : null,
          mov_id: r.mov_id ? String(r.mov_id) : null, nota: String(r.nota || "").slice(0, 300), por: WHO.includes(body.por) ? body.por : null, ts: Date.now(),
        };
        const { data, error } = await sb.from("cc_recibos").insert(row).select().single();
        if (error) throw error;
        const fotos = Array.isArray(body.fotos) ? body.fotos.filter(okFoto).slice(0, 3) : [];
        for (let i = 0; i < fotos.length; i++) await sb.from("cc_fotos").insert({ household_id: h.id, recibo_id: data.id, orden: i, data: fotos[i] });
        const who = row.por || yo;
        await later(notify(h.id, who, "🧾 Cuentas Claras", `${NOM[who] || "Alguien"} guardó un recibo de ${row.tienda || "la tienda"} (${usd(total)}, ${row.items.length} productos)`, "rec"));
        return json({ ...rOut(data), fotos: fotos.length });
      }
      case "recibo_set": {
        const upd: any = {};
        if (["pendiente", "cuenta", "nota"].includes(body.estado)) upd.estado = body.estado;
        if (body.pago === null || WHO.includes(body.pago)) upd.pago = body.pago;
        if ("mov_id" in body) upd.mov_id = body.mov_id ? String(body.mov_id) : null;
        const { data, error } = await sb.from("cc_recibos").update(upd).eq("household_id", h.id).eq("id", String(body.id || "")).select().single();
        if (error) throw error;
        const est = data.estado === "cuenta" ? `lo sumó a la cuenta${data.pago ? " (lo pagó " + (data.pago === "yoan" ? "Yoan" : "la Tía") + ")" : ""}` : data.estado === "nota" ? "lo marcó como que ya estaba en la cuenta" : "lo dejó pendiente";
        await later(notify(h.id, yo, "🧾 Cuentas Claras", `${NOM[yo] || "Alguien"} cambió el recibo de ${data.tienda} del ${data.fecha}: ${est}`, "rec"));
        return json(rOut(data));
      }
      case "recibo_del": {
        const { error } = await sb.from("cc_recibos").delete().eq("household_id", h.id).eq("id", String(body.id || ""));
        if (error) throw error;
        return json({ ok: true });
      }
      case "foto_add": {
        if (!okFoto(body.data)) return json({ error: "foto" }, 400);
        const { data: rc } = await sb.from("cc_recibos").select("id").eq("household_id", h.id).eq("id", String(body.recibo_id || "")).maybeSingle();
        if (!rc) return json({ error: "recibo" }, 404);
        const { error } = await sb.from("cc_fotos").insert({ household_id: h.id, recibo_id: rc.id, orden: Number(body.orden) || 0, data: body.data });
        if (error) throw error;
        return json({ ok: true });
      }
      case "fotos_get": {
        const { data, error } = await sb.from("cc_fotos").select("orden,data").eq("household_id", h.id).eq("recibo_id", String(body.recibo_id || "")).order("orden");
        if (error) throw error;
        return json({ fotos: (data || []).map((f: any) => f.data) });
      }
      case "msg_add": {
        const de = String(body.de || ""), texto = String(body.texto || "").trim().slice(0, 1000);
        const ref_tipo = ["mov", "rec", "saldo"].includes(body.ref_tipo) ? body.ref_tipo : null;
        if (!WHO.includes(de) || (!texto && !ref_tipo)) return json({ error: "datos" }, 400);
        const ref_id = ref_tipo && ref_tipo !== "saldo" && /^[0-9a-f-]{36}$/i.test(String(body.ref_id || "")) ? String(body.ref_id) : null;
        const { data, error } = await sb.from("cc_mensajes").insert({ household_id: h.id, de, texto, ref_tipo, ref_id, ref_txt: String(body.ref_txt || "").slice(0, 200), ts: Date.now() })
          .select("id,de,texto,ref_tipo,ref_id,ref_txt,ts").single();
        if (error) throw error;
        await later(notify(h.id, de, `💌 ${NOM[de]}`, texto || (ref_tipo === "saldo" ? "📊 " : "📎 ") + String(body.ref_txt || ""), "msg"));
        return json({ ...data, ts: Number(data.ts) });
      }
      case "msg_del": {
        const { error } = await sb.from("cc_mensajes").delete().eq("household_id", h.id).eq("id", String(body.id || ""));
        if (error) throw error;
        return json({ ok: true });
      }
      case "push_sub": {
        const sub = body.sub, quien = String(body.quien || "");
        if (!WHO.includes(quien) || !sub || typeof sub.endpoint !== "string" || !/^https:\/\//.test(sub.endpoint) || !sub.keys?.p256dh || !sub.keys?.auth) return json({ error: "datos" }, 400);
        const clean = { endpoint: sub.endpoint, keys: { p256dh: String(sub.keys.p256dh), auth: String(sub.keys.auth) } };
        const { error } = await sb.from("cc_push").upsert({ household_id: h.id, quien, endpoint: clean.endpoint, sub: clean, ts: Date.now() }, { onConflict: "endpoint" });
        if (error) throw error;
        return json({ ok: true });
      }
      case "push_unsub": {
        await sb.from("cc_push").delete().eq("household_id", h.id).eq("endpoint", String(body.endpoint || ""));
        return json({ ok: true });
      }
      case "push_test": {
        const target = WHO.includes(body.quien) ? body.quien : yo;
        await notify(h.id, target === "yoan" ? "arelis" : "yoan", "🔔 Cuentas Claras", "¡Los avisos ya funcionan! 🐷", "test");
        return json({ ok: true });
      }
      case "ask":
        return json(await ask(body));
      default:
        return json({ error: "acción" }, 400);
    }
  } catch (e) {
    console.error(e);
    return json({ error: "servidor" }, 500);
  }
});
