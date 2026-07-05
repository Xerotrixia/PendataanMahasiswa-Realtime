// =========================================================================
// SUPABASE EDGE FUNCTION: app-api
// =========================================================================
// Ini adalah SATU-SATUNYA pintu untuk operasi TULIS (insert/update/delete)
// dan LOGIN. Anon key publik (yang ikut ter-bundle di aplikasi yang
// dibagikan ke banyak orang) TIDAK LAGI bisa menulis langsung ke tabel -
// semua tulisan asli dieksekusi di sini, memakai service_role key yang
// TIDAK PERNAH keluar dari server Supabase.
//
// CARA DEPLOY (lewat Dashboard, tidak perlu install apapun):
// 1. Buka dashboard Supabase project kamu
// 2. Klik menu "Edge Functions" di sidebar kiri
// 3. Klik "Deploy a new function" -> "Via Editor" (atau "Create function")
// 4. Kasih nama function: app-api  (HARUS PERSIS "app-api", huruf kecil)
// 5. Hapus semua kode contoh di editor, lalu copy-paste SELURUH isi file
//    ini ke editor tersebut
// 6. Klik "Deploy"
// 7. Masih di halaman Edge Functions -> buka tab "Secrets" (atau menu
//    Settings -> Edge Functions -> Secrets) -> tambahkan secret baru:
//      Nama:  SESSION_SECRET
//      Nilai: (ketik apa saja yang panjang & acak, minimal 32 karakter,
//              contoh: tempel hasil dari https://www.uuidgenerator.net/
//              atau ketik sembarang kalimat panjang acak sendiri)
//    Secret ini dipakai untuk menandatangani token sesi login, JANGAN
//    dibagikan ke siapapun dan JANGAN sama dengan anon key/service key.
// 8. Selesai - lanjut ke LANGKAH SQL di file SETUP-SUPABASE-UPDATE.sql
// =========================================================================

const SUPABASE_URL = Deno.env.get("SUPABASE_URL")!;
const SERVICE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
const SESSION_SECRET = Deno.env.get("SESSION_SECRET")!;
const TOKEN_TTL_MS = 12 * 60 * 60 * 1000; // token sesi berlaku 12 jam

function corsHeaders() {
  return {
    "Access-Control-Allow-Origin": "*",
    "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
    "Access-Control-Allow-Methods": "POST, OPTIONS",
  };
}

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json", ...corsHeaders() },
  });
}

async function hmac(data: string): Promise<string> {
  const key = await crypto.subtle.importKey(
    "raw",
    new TextEncoder().encode(SESSION_SECRET),
    { name: "HMAC", hash: "SHA-256" },
    false,
    ["sign"],
  );
  const sig = await crypto.subtle.sign("HMAC", key, new TextEncoder().encode(data));
  return btoa(String.fromCharCode(...new Uint8Array(sig)));
}

async function makeToken(username: string, role: string): Promise<string> {
  const expires = Date.now() + TOKEN_TTL_MS;
  const payload = `${username}|${role}|${expires}`;
  const sig = await hmac(payload);
  return btoa(payload) + "." + sig;
}

async function verifyToken(token: string | undefined): Promise<{ username: string; role: string } | null> {
  if (!token) return null;
  const parts = token.split(".");
  if (parts.length !== 2) return null;
  let payload: string;
  try {
    payload = atob(parts[0]);
  } catch {
    return null;
  }
  const expectedSig = await hmac(payload);
  if (expectedSig !== parts[1]) return null;
  const [username, role, expiresStr] = payload.split("|");
  const expires = Number(expiresStr);
  if (!username || !role || Number.isNaN(expires) || Date.now() > expires) return null;
  return { username, role };
}

async function rest(path: string, init: RequestInit = {}) {
  return await fetch(`${SUPABASE_URL}/rest/v1${path}`, {
    ...init,
    headers: {
      "apikey": SERVICE_KEY,
      "Authorization": `Bearer ${SERVICE_KEY}`,
      "Content-Type": "application/json",
      ...(init.headers || {}),
    },
  });
}

async function hashPasswordSHA256(plain: string): Promise<string> {
  const data = new TextEncoder().encode(plain);
  const digest = await crypto.subtle.digest("SHA-256", data);
  return Array.from(new Uint8Array(digest)).map((b) => b.toString(16).padStart(2, "0")).join("");
}

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders() });
  }
  if (req.method !== "POST") {
    return jsonResponse({ ok: false, error: "Method not allowed" }, 405);
  }

  let body: any;
  try {
    body = await req.json();
  } catch {
    return jsonResponse({ ok: false, error: "Body tidak valid" }, 400);
  }

  const action = body?.action as string;

  try {
    switch (action) {
      // Bootstrap akun default admin/user - aman, cuma jalan kalau tabel
      // users masih kosong sama sekali.
      case "init_default_users": {
        const check = await rest("/users?select=username&limit=1");
        const rows = await check.json();
        if (Array.isArray(rows) && rows.length === 0) {
          await rest("/users", {
            method: "POST",
            headers: { "Prefer": "return=minimal" },
            body: JSON.stringify([
              { username: "admin", password: await hashPasswordSHA256("admin123"), role: "admin", nama_lengkap: "Administrator" },
              { username: "user", password: await hashPasswordSHA256("user123"), role: "user", nama_lengkap: "User Biasa" },
            ]),
          });
        }
        return jsonResponse({ ok: true });
      }

      case "login": {
        const { username, password } = body;
        if (!username || !password) {
          return jsonResponse({ ok: false, error: "Username dan password harus diisi!" }, 400);
        }
        const res = await rest(`/users?username=eq.${encodeURIComponent(username)}&select=username,password,role,nama_lengkap`);
        const rows = await res.json();
        if (!Array.isArray(rows) || rows.length === 0) {
          return jsonResponse({ ok: false, error: "Username tidak ditemukan!" });
        }
        const user = rows[0];
        const hashed = await hashPasswordSHA256(password);
        if (hashed !== user.password) {
          return jsonResponse({ ok: false, error: "Password salah!" });
        }
        const token = await makeToken(user.username, user.role);
        return jsonResponse({ ok: true, token, role: user.role, nama_lengkap: user.nama_lengkap });
      }

      case "log_activity": {
        const session = await verifyToken(body.token);
        if (!session) return jsonResponse({ ok: false, error: "Sesi tidak valid, silakan login ulang." }, 401);
        await rest("/activity_log", {
          method: "POST",
          headers: { "Prefer": "return=minimal" },
          body: JSON.stringify({ username: session.username, aktivitas: body.aktivitas, waktu: body.waktu }),
        });
        return jsonResponse({ ok: true });
      }

      case "insert_mahasiswa":
      case "update_mahasiswa":
      case "delete_mahasiswa": {
        const session = await verifyToken(body.token);
        if (!session) return jsonResponse({ ok: false, error: "Sesi tidak valid, silakan login ulang." }, 401);

        let r: Response;
        if (action === "insert_mahasiswa") {
          r = await rest("/mahasiswa", {
            method: "POST",
            headers: { "Prefer": "return=minimal" },
            body: JSON.stringify({ nim: body.nim, nama: body.nama, prodi: body.prodi }),
          });
        } else if (action === "update_mahasiswa") {
          r = await rest(`/mahasiswa?nim=eq.${encodeURIComponent(body.originalNim)}`, {
            method: "PATCH",
            headers: { "Prefer": "return=minimal" },
            body: JSON.stringify({ nim: body.nim, nama: body.nama, prodi: body.prodi }),
          });
        } else {
          r = await rest(`/mahasiswa?nim=eq.${encodeURIComponent(body.nim)}`, {
            method: "DELETE",
            headers: { "Prefer": "return=minimal" },
          });
        }
        if (!r.ok) {
          return jsonResponse({ ok: false, error: await r.text() }, r.status);
        }
        return jsonResponse({ ok: true });
      }

      case "get_users": {
        const session = await verifyToken(body.token);
        if (!session || session.role !== "admin") {
          return jsonResponse({ ok: false, error: "Hanya admin yang boleh melihat daftar user." }, 403);
        }
        const r = await rest("/users?select=username,role,nama_lengkap&order=username.asc");
        const rows = await r.json();
        return jsonResponse({ ok: true, users: rows });
      }

      case "insert_user":
      case "update_user":
      case "delete_user": {
        const session = await verifyToken(body.token);
        if (!session || session.role !== "admin") {
          return jsonResponse({ ok: false, error: "Hanya admin yang boleh mengelola user." }, 403);
        }

        let r: Response;
        if (action === "insert_user") {
          r = await rest("/users", {
            method: "POST",
            headers: { "Prefer": "return=minimal" },
            body: JSON.stringify({
              username: body.username,
              password: await hashPasswordSHA256(body.passwordPlain),
              role: body.role,
              nama_lengkap: body.namaLengkap,
            }),
          });
        } else if (action === "update_user") {
          const patch: Record<string, unknown> = {
            username: body.username,
            nama_lengkap: body.namaLengkap,
            role: body.role,
          };
          if (body.passwordPlain) {
            patch.password = await hashPasswordSHA256(body.passwordPlain);
          }
          r = await rest(`/users?username=eq.${encodeURIComponent(body.originalUsername)}`, {
            method: "PATCH",
            headers: { "Prefer": "return=minimal" },
            body: JSON.stringify(patch),
          });
        } else {
          r = await rest(`/users?username=eq.${encodeURIComponent(body.username)}`, {
            method: "DELETE",
            headers: { "Prefer": "return=minimal" },
          });
        }
        if (!r.ok) {
          return jsonResponse({ ok: false, error: await r.text() }, r.status);
        }
        return jsonResponse({ ok: true });
      }

      default:
        return jsonResponse({ ok: false, error: "Aksi tidak dikenal: " + action }, 400);
    }
  } catch (e) {
    return jsonResponse({ ok: false, error: String(e) }, 500);
  }
});
