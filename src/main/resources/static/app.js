const state = {
  shortCode: "",
  shortUrl: "",
};

const $ = (id) => document.getElementById(id);

function logEvent(title, detail = "") {
  const item = document.createElement("li");
  item.innerHTML = `<strong>${title}</strong>${detail ? ` - ${detail}` : ""}`;
  $("eventLog").prepend(item);
  while ($("eventLog").children.length > 12) {
    $("eventLog").lastElementChild.remove();
  }
}

async function request(path, options = {}) {
  const response = await fetch(path, {
    redirect: "manual",
    headers: {
      "Content-Type": "application/json",
      ...(options.headers || {}),
    },
    ...options,
  });

  const text = await response.text();
  let body = null;
  if (text) {
    try {
      body = JSON.parse(text);
    } catch {
      body = text;
    }
  }
  return { response, body };
}

function setBusy(button, busy) {
  button.disabled = busy;
}

function setHealth(ok) {
  $("healthDot").className = `status-dot ${ok ? "ok" : "bad"}`;
  $("healthLabel").textContent = ok ? "Service healthy" : "Service unavailable";
  $("apiState").textContent = ok ? "Healthy" : "Down";
}

async function checkHealth() {
  try {
    const { response } = await request("/actuator/health", { headers: {} });
    setHealth(response.ok);
  } catch {
    setHealth(false);
  }
}

async function createShortUrl() {
  const button = $("createButton");
  setBusy(button, true);
  try {
    const payload = {
      originalUrl: $("originalUrl").value.trim(),
    };
    const alias = $("customAlias").value.trim();
    const ttl = $("ttlSeconds").value.trim();
    if (alias) payload.customAlias = alias;
    if (ttl) payload.ttlSeconds = Number(ttl);

    const { response, body } = await request("/api/v1/urls", {
      method: "POST",
      body: JSON.stringify(payload),
    });

    if (!response.ok) {
      throw new Error(body?.message || `Create failed with ${response.status}`);
    }

    state.shortCode = body.shortCode;
    state.shortUrl = body.shortUrl;
    $("shortCode").value = body.shortCode;
    $("createResult").innerHTML = `
      <div class="muted">Short code</div>
      <div class="short-url">
        <a href="/${body.shortCode}" target="_blank" rel="noreferrer">${body.shortUrl}</a>
        <button class="ghost-button" id="copyShortUrl">Copy</button>
      </div>
      <p class="hint">Original: ${body.originalUrl}</p>
    `;
    $("copyShortUrl").addEventListener("click", () => navigator.clipboard.writeText(body.shortUrl));
    logEvent("Created", body.shortCode);
  } catch (error) {
    $("createResult").innerHTML = `<span class="muted">${error.message}</span>`;
    logEvent("Create failed", error.message);
  } finally {
    setBusy(button, false);
  }
}

async function testRedirect() {
  const code = $("shortCode").value.trim();
  if (!code) return;
  try {
    window.open(`/${code}`, "_blank", "noopener,noreferrer");
    $("lastStatus").textContent = "opened";
    logEvent("Redirect opened", "Use Postman or curl to inspect the exact 302 Location header");
  } catch (error) {
    $("lastStatus").textContent = "error";
    logEvent("Redirect failed", error.message);
  }
}

async function fetchStats() {
  const code = $("shortCode").value.trim();
  if (!code) return;
  try {
    const { response, body } = await request(`/api/v1/urls/${code}/stats`, { headers: {} });
    if (!response.ok) throw new Error(body?.message || `Stats failed with ${response.status}`);
    $("cacheState").textContent = body.cacheHit ? "Warm" : "Cold";
    $("eventCount").textContent = body.redirectEvents;
    logEvent("Stats fetched", `${body.redirectEvents} events, cache ${body.cacheHit ? "warm" : "cold"}`);
  } catch (error) {
    logEvent("Stats failed", error.message);
  }
}

async function burstRateLimit() {
  const code = $("shortCode").value.trim();
  if (!code) return;
  const total = 140;
  const requests = Array.from({ length: total }, () => fetch(`/${code}`, { redirect: "manual" }));
  const responses = await Promise.allSettled(requests);
  const limited = responses.filter((r) => r.status === "fulfilled" && r.value.status === 429).length;
  const allowed = responses.filter((r) => r.status === "fulfilled" && (r.value.status === 302 || r.value.type === "opaqueredirect")).length;
  $("lastStatus").textContent = `${allowed}/${limited}`;
  logEvent("Burst complete", `${allowed} allowed redirects, ${limited} rate limited`);
}

async function warmCache() {
  await testRedirect();
  await fetchStats();
}

async function evictCache() {
  const code = $("shortCode").value.trim();
  if (!code) return;
  try {
    const { response, body } = await request(`/api/v1/urls/${code}/cache`, {
      method: "DELETE",
      headers: {},
    });
    if (!response.ok) throw new Error(body?.message || `Evict failed with ${response.status}`);
    $("cacheState").textContent = "Cold";
    logEvent("Cache evicted", code);
  } catch (error) {
    logEvent("Evict failed", error.message);
  }
}

function wire() {
  $("createButton").addEventListener("click", createShortUrl);
  $("clearButton").addEventListener("click", () => {
    $("createResult").innerHTML = '<span class="muted">Create a URL to see the generated short code, redirect target, and next actions.</span>';
  });
  $("loadExample").addEventListener("click", () => {
    $("originalUrl").value = "https://spring.io/projects/spring-boot";
    $("customAlias").value = "";
    $("ttlSeconds").value = "";
  });
  $("redirectButton").addEventListener("click", testRedirect);
  $("statsButton").addEventListener("click", fetchStats);
  $("burstButton").addEventListener("click", burstRateLimit);
  $("cacheButton").addEventListener("click", warmCache);
  $("evictButton").addEventListener("click", evictCache);
}

wire();
checkHealth();
setInterval(checkHealth, 10_000);
