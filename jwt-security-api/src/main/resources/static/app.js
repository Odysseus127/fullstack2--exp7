const TOKEN_KEY = "keyspace.accessToken";
const REFRESH_KEY = "keyspace.refreshToken";

const authView = document.querySelector("#auth-view");
const appView = document.querySelector("#app-view");
const authForm = document.querySelector("#auth-form");
const authError = document.querySelector("#auth-error");
const authSubmit = document.querySelector("#auth-submit");
const authModeTabs = document.querySelectorAll(".auth-tab");
const credentialForm = document.querySelector("#credential-form");
const credentialSubmit = document.querySelector("#credential-submit");
const pageTitles = { overview: "Overview", profile: "My profile", credentials: "Credentials", users: "User directory" };
let authMode = "login";
let currentUsername = "";
let currentRole = "USER";
let toastTimer;

class ApiError extends Error {
  constructor(message, status) {
    super(message);
    this.status = status;
  }
}

function storedToken(key) {
  return sessionStorage.getItem(key);
}

function saveTokens(data) {
  if (!data || typeof data.token !== "string" || typeof data.refreshToken !== "string") {
    throw new Error("The server returned an invalid authentication response.");
  }
  sessionStorage.setItem(TOKEN_KEY, data.token);
  sessionStorage.setItem(REFRESH_KEY, data.refreshToken);
}

function clearTokens() {
  sessionStorage.removeItem(TOKEN_KEY);
  sessionStorage.removeItem(REFRESH_KEY);
}

async function responseMessage(response) {
  const body = await response.text();
  if (!body) return `Request failed (${response.status}).`;
  try {
    const data = JSON.parse(body);
    return data.message || data.error || body;
  } catch {
    return body;
  }
}

async function refreshSession() {
  const refreshToken = storedToken(REFRESH_KEY);
  if (!refreshToken) return false;

  const response = await fetch("/api/auth/refresh", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ refreshToken })
  });
  if (!response.ok) {
    clearTokens();
    return false;
  }
  saveTokens(await response.json());
  return true;
}

async function apiRequest(path, options = {}, retry = true) {
  const headers = new Headers(options.headers || {});
  const token = storedToken(TOKEN_KEY);
  if (token) headers.set("Authorization", `Bearer ${token}`);
  if (options.body && !headers.has("Content-Type")) headers.set("Content-Type", "application/json");

  let response = await fetch(path, { ...options, headers });
  if ((response.status === 401 || response.status === 403) && retry && storedToken(REFRESH_KEY)) {
    if (await refreshSession()) {
      return apiRequest(path, options, false);
    }
    if (!storedToken(TOKEN_KEY)) showAuth();
  }
  if (!response.ok) throw new ApiError(await responseMessage(response), response.status);
  if (response.status === 204) return null;
  const contentType = response.headers.get("content-type") || "";
  return contentType.includes("application/json") ? response.json() : response.text();
}

function setAuthMode(mode) {
  authMode = mode;
  const registering = mode === "register";
  document.querySelector("#auth-title").textContent = registering ? "Create account" : "Sign in";
  document.querySelector("#auth-submit span:first-child").textContent = registering ? "Create account" : "Sign in";
  document.querySelector("#password").autocomplete = registering ? "new-password" : "current-password";
  authModeTabs.forEach((tab) => {
    const active = tab.dataset.mode === mode;
    tab.classList.toggle("active", active);
    tab.setAttribute("aria-selected", String(active));
  });
  authError.hidden = true;
  authError.textContent = "";
}

function showAuth() {
  clearTokens();
  appView.hidden = true;
  authView.hidden = false;
}

function setCurrentUser(username, role) {
  currentUsername = username;
  currentRole = role;
  const initial = username.slice(0, 1).toUpperCase() || "U";
  document.querySelector("#sidebar-avatar").textContent = initial;
  document.querySelector("#topbar-avatar").textContent = initial;
  document.querySelector("#profile-avatar").textContent = initial;
  document.querySelector("#sidebar-username").textContent = username;
  document.querySelector("#sidebar-role").textContent = role === "ADMIN" ? "Administrator" : "Member";
  document.querySelector("#welcome-name").textContent = username;
  document.querySelector("#access-level").textContent = role === "ADMIN" ? "Admin" : "Member";
  document.querySelector("#profile-username").textContent = username;
  document.querySelector("#profile-username-field").textContent = username;
  document.querySelector("#profile-role").textContent = role;
  document.querySelector("#profile-role-field").textContent = role;
  const isAdmin = role === "ADMIN";
  document.querySelector("#admin-nav").hidden = !isAdmin;
  document.querySelector("#admin-shortcut").hidden = !isAdmin;
}

function showPage(page) {
  if (!pageTitles[page] || (page === "users" && currentRole !== "ADMIN")) return;
  document.querySelectorAll(".page-section").forEach((section) => {
    section.hidden = section.id !== `page-${page}`;
  });
  document.querySelectorAll(".nav-link").forEach((link) => {
    link.classList.toggle("active", link.dataset.page === page);
  });
  document.querySelector("#breadcrumb-current").textContent = pageTitles[page];
  if (page === "profile") loadProfile();
  if (page === "credentials") loadCredentialProviders();
  if (page === "users") loadUsers();
}

function showToast(message, isError = false) {
  const toast = document.querySelector("#page-message");
  toast.textContent = message;
  toast.classList.toggle("error", isError);
  toast.hidden = false;
  window.clearTimeout(toastTimer);
  toastTimer = window.setTimeout(() => { toast.hidden = true; }, 4200);
}

async function loadProfile() {
  const profileMessage = document.querySelector("#profile-message");
  try {
    const profile = await apiRequest("/api/users/profile");
    profileMessage.textContent = typeof profile === "string" ? profile : `Signed in as ${currentUsername}.`;
    profileMessage.classList.remove("error");
  } catch (error) {
    profileMessage.textContent = error.message;
    profileMessage.classList.add("error");
    if (!storedToken(TOKEN_KEY)) showAuth();
  }
}

function renderCredentialProviders(providers) {
  const list = document.querySelector("#credentials-list");
  list.replaceChildren();
  if (!providers.length) {
    const empty = document.createElement("li");
    empty.className = "credential-empty";
    empty.textContent = "No credentials saved.";
    list.append(empty);
    return;
  }

  providers.forEach((provider) => {
    const item = document.createElement("li");
    item.className = "credential-item";
    const name = document.createElement("span");
    name.textContent = provider;
    const remove = document.createElement("button");
    remove.className = "delete-user";
    remove.type = "button";
    remove.textContent = "Remove";
    remove.setAttribute("aria-label", `Remove ${provider} credential`);
    remove.addEventListener("click", () => deleteCredential(provider));
    item.append(name, remove);
    list.append(item);
  });
}

async function loadCredentialProviders() {
  const list = document.querySelector("#credentials-list");
  list.replaceChildren();
  const loading = document.createElement("li");
  loading.className = "credential-empty";
  loading.textContent = "Loading credentials…";
  list.append(loading);
  try {
    const providers = await apiRequest("/api/users/credentials");
    if (!Array.isArray(providers) || providers.some((provider) => typeof provider !== "string")) {
      throw new Error("The server returned an invalid credentials response.");
    }
    renderCredentialProviders(providers);
  } catch (error) {
    list.replaceChildren();
    const message = document.createElement("li");
    message.className = "credential-empty error";
    message.textContent = error.message;
    list.append(message);
    if (!storedToken(TOKEN_KEY)) showAuth();
  }
}

async function deleteCredential(provider) {
  if (!window.confirm(`Remove the saved ${provider} credential?`)) return;
  try {
    await apiRequest(`/api/users/credentials/${encodeURIComponent(provider)}`, { method: "DELETE" });
    showToast(`${provider} credential removed.`);
    await loadCredentialProviders();
  } catch (error) {
    showToast(error.message, true);
    if (!storedToken(TOKEN_KEY)) showAuth();
  }
}

function renderUsers(users) {
  const tbody = document.querySelector("#users-table-body");
  document.querySelector("#user-count").textContent = `${users.length} ${users.length === 1 ? "MEMBER" : "MEMBERS"}`;
  if (!users.length) {
    tbody.innerHTML = '<tr><td colspan="4" class="table-empty">No accounts found.</td></tr>';
    return;
  }
  tbody.replaceChildren(...users.map((user) => {
    const row = document.createElement("tr");
    const member = document.createElement("td");
    member.className = "member-cell";
    const avatar = document.createElement("span");
    avatar.className = "member-mini-avatar";
    avatar.textContent = user.username.slice(0, 1).toUpperCase();
    const username = document.createElement("span");
    username.textContent = user.username;
    member.append(avatar, username);
    const id = document.createElement("td");
    id.textContent = String(user.id);
    const roleCell = document.createElement("td");
    const role = document.createElement("span");
    role.className = "table-role";
    role.textContent = user.role;
    roleCell.append(role);
    const actionCell = document.createElement("td");
    const remove = document.createElement("button");
    remove.className = "delete-user";
    remove.type = "button";
    remove.textContent = "Remove";
    remove.setAttribute("aria-label", `Remove ${user.username}`);
    remove.addEventListener("click", () => deleteUser(user.id, user.username));
    actionCell.append(remove);
    row.append(member, id, roleCell, actionCell);
    return row;
  }));
}

async function loadUsers() {
  const tbody = document.querySelector("#users-table-body");
  tbody.innerHTML = '<tr><td colspan="4" class="table-empty">Loading members…</td></tr>';
  try {
    const users = await apiRequest("/api/admin/users");
    renderUsers(users);
  } catch (error) {
    tbody.replaceChildren();
    const row = document.createElement("tr");
    const cell = document.createElement("td");
    cell.colSpan = 4;
    cell.className = "table-empty";
    cell.textContent = error.message;
    row.append(cell);
    tbody.append(row);
    if (!storedToken(TOKEN_KEY)) showAuth();
  }
}

async function deleteUser(id, username) {
  if (!window.confirm(`Remove ${username} from this workspace? This cannot be undone.`)) return;
  try {
    await apiRequest(`/api/admin/users/${encodeURIComponent(id)}`, { method: "DELETE" });
    showToast(`${username} was removed from the workspace.`);
    await loadUsers();
  } catch (error) {
    showToast(error.message, true);
    if (!storedToken(TOKEN_KEY)) showAuth();
  }
}

async function enterWorkspace(username) {
  appView.hidden = false;
  authView.hidden = true;
  setCurrentUser(username, "USER");
  showPage("overview");
  try {
    await apiRequest("/api/users/profile");
    const dashboard = await apiRequest("/api/users/dashboard");
    if (typeof dashboard === "string") {
      document.querySelector("#dashboard-status").textContent = dashboard;
    }
  } catch (error) {
    if (!storedToken(TOKEN_KEY)) {
      showAuth();
      return;
    }
    showToast(error.message, true);
  }
  try {
    const dashboard = await apiRequest("/api/admin/dashboard");
    setCurrentUser(username, "ADMIN");
    if (typeof dashboard === "string") {
      document.querySelector("#dashboard-status").textContent = dashboard;
    }
  } catch (error) {
    if (!storedToken(TOKEN_KEY)) {
      showAuth();
      return;
    }
    if (error.status === 403) return;
    showToast(error.message, true);
  }
  showPage("overview");
}

authModeTabs.forEach((tab) => tab.addEventListener("click", () => setAuthMode(tab.dataset.mode)));

document.querySelector(".password-toggle").addEventListener("click", (event) => {
  const input = document.querySelector("#password");
  const show = input.type === "password";
  input.type = show ? "text" : "password";
  event.currentTarget.textContent = show ? "Hide" : "Show";
  event.currentTarget.setAttribute("aria-label", `${show ? "Hide" : "Show"} password`);
});

authForm.addEventListener("submit", async (event) => {
  event.preventDefault();
  authError.hidden = true;
  authSubmit.disabled = true;
  authSubmit.querySelector("span:first-child").textContent = authMode === "register" ? "Creating account…" : "Signing in…";
  const credentials = {
    username: authForm.elements.username.value.trim(),
    password: authForm.elements.password.value
  };
  try {
    const response = await fetch(`/api/auth/${authMode}`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(credentials)
    });
    if (!response.ok) throw new Error(await responseMessage(response));
    if (authMode === "register") {
      setAuthMode("login");
      authForm.elements.username.value = credentials.username;
      authForm.elements.password.value = "";
      authError.textContent = "Account created. Sign in with your new credentials.";
      authError.classList.add("success-message");
      authError.hidden = false;
      return;
    }
    saveTokens(await response.json());
    await enterWorkspace(credentials.username);
  } catch (error) {
    authError.textContent = error.message || "Unable to connect to the server. Please try again.";
    authError.classList.remove("success-message");
    authError.hidden = false;
  } finally {
    authSubmit.disabled = false;
    authSubmit.querySelector("span:first-child").textContent =
      authMode === "register" ? "Create account" : "Sign in";
  }
});

document.querySelectorAll("[data-page]").forEach((control) => {
  control.addEventListener("click", (event) => {
    event.preventDefault();
    showPage(control.dataset.page);
  });
});

document.querySelector("#refresh-users").addEventListener("click", () => loadUsers());
credentialForm.addEventListener("submit", async (event) => {
  event.preventDefault();
  credentialSubmit.disabled = true;
  try {
    await apiRequest("/api/users/credentials", {
      method: "PUT",
      body: JSON.stringify({
        provider: credentialForm.elements.provider.value.trim(),
        secret: credentialForm.elements.secret.value
      })
    });
    credentialForm.reset();
    showToast("Credential saved securely.");
    await loadCredentialProviders();
  } catch (error) {
    showToast(error.message, true);
    if (!storedToken(TOKEN_KEY)) showAuth();
  } finally {
    credentialSubmit.disabled = false;
  }
});

document.querySelector("#logout-button").addEventListener("click", async () => {
  const refreshToken = storedToken(REFRESH_KEY);
  let logoutError = "";
  try {
    if (refreshToken) {
      const response = await fetch("/api/auth/logout", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ refreshToken })
      });
      if (!response.ok) throw new Error(await responseMessage(response));
    }
  } catch (error) {
    logoutError = error.message;
  }
  showAuth();
  authForm.reset();
  setAuthMode("login");
  if (logoutError) {
    authError.textContent = `You were signed out here, but the server could not revoke your refresh token: ${logoutError}`;
    authError.classList.remove("success-message");
    authError.hidden = false;
  }
});

document.querySelector("#today-date").textContent = new Intl.DateTimeFormat(undefined, {
  weekday: "short",
  month: "short",
  day: "numeric",
  year: "numeric"
}).format(new Date());

async function restoreSession() {
  const token = storedToken(TOKEN_KEY);
  if (!token) return;
  let username = "";
  try {
    const payload = JSON.parse(atob(token.split(".")[1].replace(/-/g, "+").replace(/_/g, "/")));
    username = typeof payload.sub === "string" ? payload.sub : "";
  } catch {
    clearTokens();
    return;
  }
  if (!username) {
    clearTokens();
    return;
  }
  await enterWorkspace(username);
}

restoreSession();
