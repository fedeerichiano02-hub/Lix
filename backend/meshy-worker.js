export default {
  async fetch(request, env) {
    const cors = {
      "Access-Control-Allow-Origin": "*",
      "Access-Control-Allow-Headers": "Content-Type",
      "Access-Control-Allow-Methods": "GET,POST,OPTIONS"
    };
    if (request.method === "OPTIONS") return new Response(null, { headers: cors });
    if (!env.MESHY_API_KEY) return json({ error: "MESHY_API_KEY no configurada" }, 500, cors);

    const url = new URL(request.url);
    const path = url.pathname.replace(/^\/+/, "");
    if (!path.startsWith("image-to-3d")) return json({ error: "Ruta no encontrada" }, 404, cors);

    const suffix = path === "image-to-3d" ? "" : "/" + path.slice("image-to-3d/".length);
    const target = `https://api.meshy.ai/openapi/v1/image-to-3d${suffix}`;
    const headers = new Headers({
      "Authorization": `Bearer ${env.MESHY_API_KEY}`,
      "Content-Type": "application/json"
    });
    const body = request.method === "POST" ? await request.text() : undefined;
    const upstream = await fetch(target, { method: request.method, headers, body });
    const text = await upstream.text();
    return new Response(text, {
      status: upstream.status,
      headers: { ...cors, "Content-Type": "application/json" }
    });
  }
};

function json(value, status, cors) {
  return new Response(JSON.stringify(value), {
    status,
    headers: { ...cors, "Content-Type": "application/json" }
  });
}
