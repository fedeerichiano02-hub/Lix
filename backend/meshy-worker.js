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
    const body = request.method === "POST" ? await request.text() : undefined;

    if (path === "text-to-3d" || path.startsWith("text-to-3d/")) {
      const suffix = path === "text-to-3d" ? "" : "/" + path.slice("text-to-3d/".length);
      return proxy("https://api.meshy.ai/openapi/v2/text-to-3d" + suffix, request.method, body, cors, env);
    }

    if (path === "image-to-3d" || path.startsWith("image-to-3d/")) {
      const suffix = path === "image-to-3d" ? "" : "/" + path.slice("image-to-3d/".length);
      return proxy("https://api.meshy.ai/openapi/v1/image-to-3d" + suffix, request.method, body, cors, env);
    }

    if (path === "rigging" || path.startsWith("rigging/")) {
      const suffix = path === "rigging" ? "" : "/" + path.slice("rigging/".length);
      return proxy("https://api.meshy.ai/openapi/v1/rigging" + suffix, request.method, body, cors, env);
    }

    if (path === "animations" || path.startsWith("animations/")) {
      const suffix = path === "animations" ? "" : "/" + path.slice("animations/".length);
      return proxy("https://api.meshy.ai/openapi/v1/animations" + suffix, request.method, body, cors, env);
    }

    return json({ error: "Ruta no encontrada" }, 404, cors);
  }
};

async function proxy(target, method, body, cors, env) {
  const headers = new Headers({
    "Authorization": `Bearer ${env.MESHY_API_KEY}`,
    "Content-Type": "application/json"
  });
  const upstream = await fetch(target, { method, headers, body });
  const text = await upstream.text();
  return new Response(text, {
    status: upstream.status,
    headers: { ...cors, "Content-Type": "application/json" }
  });
}

function json(value, status, cors) {
  return new Response(JSON.stringify(value), {
    status,
    headers: { ...cors, "Content-Type": "application/json" }
  });
}
