const state = { shortCode: "", shortUrl: "", config: null };
const $ = (id) => document.getElementById(id);

function element(tag, text, className) {
  const node = document.createElement(tag);
  if (text !== undefined) node.textContent = text;
  if (className) node.className = className;
  return node;
}

function logEvent(title, detail = "") {
  const item = element("li");
  item.append(element("strong", title), document.createTextNode(detail ? ` - ${detail}` : ""));
  $("eventLog").prepend(item);
  while ($("eventLog").children.length > 12) $("eventLog").lastElementChild.remove();
}

async function request(path, options = {}) {
  const response = await fetch(path, {
    ...options, redirect: "manual",
    headers: { "Content-Type": "application/json", ...(options.headers || {}) },
  });
  const text = await response.text();
  let body = null;
  if (text) { try { body = JSON.parse(text); } catch { body = text; } }
  return { response, body };
}

async function checkHealth() {
  let ok = false;
  try { ok = (await request("/actuator/health/readiness")).response.ok; } catch { /* display unavailable */ }
  $("healthDot").className = `status-dot ${ok ? "ok" : "bad"}`;
  $("healthLabel").textContent = ok ? "Service healthy" : "Service unavailable";
  $("apiState").textContent = ok ? "Healthy" : "Down";
}

async function createShortUrl() {
  const button = $("createButton");
  button.disabled = true;
  try {
    if (!state.config) throw new Error("Service configuration is unavailable. Reload to try again.");
    const payload = { originalUrl: $("originalUrl").value.trim() };
    const alias = $("customAlias").value.trim();
    const ttl = $("ttlSeconds").value.trim();
    if (alias) payload.customAlias = alias;
    if (ttl) payload.ttlSeconds = Number(ttl);
    const path = state.config.demo ? "/api/v1/demo/urls" : "/api/v1/urls";
    const { response, body } = await request(path, { method: "POST", body: JSON.stringify(payload) });
    if (!response.ok) throw new Error(body?.message || `Create failed with ${response.status}`);
    if (!/^[A-Za-z0-9_-]{4,32}$/.test(body.shortCode)) throw new Error("Invalid short code returned");
    state.shortCode = body.shortCode;
    state.shortUrl = new URL(`/${body.shortCode}`, window.location.origin).href;
    $("shortCode").value = body.shortCode;
    const link = element("a", body.shortUrl);
    link.href = state.shortUrl;
    link.target = "_blank";
    link.rel = "noopener noreferrer";
    const copy = element("button", "Copy", "ghost-button");
    copy.id = "copyShortUrl";
    copy.addEventListener("click", () => navigator.clipboard.writeText(state.shortUrl)
      .catch(() => logEvent("Copy unavailable", "Select and copy the link manually")));
    const row = element("div", undefined, "short-url");
    row.append(link, copy);
    $("createResult").replaceChildren(element("div", "Short code", "muted"), row,
      element("p", `Original: ${body.originalUrl}`, "hint"));
    logEvent("Created", body.shortCode);
  } catch (error) {
    $("createResult").replaceChildren(element("span", error.message, "muted"));
    logEvent("Create failed", error.message);
  } finally { button.disabled = false; }
}

function shortCode() {
  const code = $("shortCode").value.trim();
  if (!/^[A-Za-z0-9_-]{4,32}$/.test(code)) {
    logEvent("Invalid code", "Use 4 to 32 letters, numbers, underscores or hyphens");
    return null;
  }
  return code;
}

function testRedirect() {
  const code = shortCode();
  if (!code) return;
  window.open(`/${encodeURIComponent(code)}`, "_blank", "noopener,noreferrer");
  $("lastStatus").textContent = "opened";
  logEvent("Redirect opened", "The destination opens in a new tab");
}

async function fetchStats() {
  const code = shortCode();
  if (!code) return;
  try {
    const { response, body } = await request(`/api/v1/urls/${encodeURIComponent(code)}/stats`);
    if (!response.ok) throw new Error(body?.message || `Stats failed with ${response.status}`);
    $("cacheState").textContent = body.cacheHit ? "Warm" : "Cold";
    $("eventCount").textContent = body.redirectEvents;
    logEvent("Stats fetched", `${body.redirectEvents} events, cache ${body.cacheHit ? "warm" : "cold"}`);
  } catch (error) { logEvent("Stats failed", error.message); }
}

$("createButton").addEventListener("click", createShortUrl);
$("clearButton").addEventListener("click", () => {
  $("createResult").replaceChildren(element("span", "Create a URL to see the generated short code, redirect target, and next actions.", "muted"));
});
$("loadExample").addEventListener("click", () => {
  $("originalUrl").value = "https://spring.io/projects/spring-boot";
  $("customAlias").value = "";
  $("ttlSeconds").value = "";
});
$("redirectButton").addEventListener("click", testRedirect);
$("statsButton").addEventListener("click", fetchStats);
$("cacheButton").addEventListener("click", testRedirect);

async function initialize() {
  $("createButton").disabled = true;
  try {
    const { response, body } = await request("/api/v1/config");
    if (!response.ok) throw new Error("Service configuration unavailable");
    state.config = body;
    if (body.demo) {
      $("creationPolicy").textContent = `Public demo: destinations on ${body.demoHosts.join(", ")}. Up to 10 links per visitor daily; links expire within one day. Analytics show the last 7 days.`;
      $("ttlSeconds").max = "86400";
      $("createButton").disabled = !body.demoEnabled;
    } else { $("createButton").disabled = false; }
  } catch (error) { logEvent("Configuration unavailable", error.message); }
}
initialize();
checkHealth();
setInterval(checkHealth, 30_000);
