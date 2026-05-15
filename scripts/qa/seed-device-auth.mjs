#!/usr/bin/env node
import { createRequire } from "node:module"
import { resolve } from "node:path"

const webRoot = process.env.WEB_ROOT || "/Users/diegolden/Code/LCX-OS/lcx-pwa"
const requireFromWeb = createRequire(resolve(webRoot, "package.json"))
const { createClient } = requireFromWeb("@supabase/supabase-js")

function env(name, fallback = "") {
  const value = process.env[name] ?? fallback
  return typeof value === "string" ? value.trim() : ""
}

function required(name, fallback = "") {
  const value = env(name, fallback)
  if (!value) {
    throw new Error(`Missing required env var: ${name}`)
  }
  return value
}

function log(message) {
  process.stderr.write(`[seed-device-auth] ${message}\n`)
}

const supabaseUrl = required("NEXT_PUBLIC_SUPABASE_URL", env("SUPABASE_URL"))
const anonKey = required("NEXT_PUBLIC_SUPABASE_ANON_KEY", env("ANON_KEY"))
const serviceRoleKey = required("SUPABASE_SERVICE_ROLE_KEY", env("SERVICE_ROLE_KEY"))
const pwaBaseUrl = env("LCX_E2E_PWA_BASE_URL", env("PWA_BASE_URL", "http://127.0.0.1:3000")).replace(/\/+$/, "")

const branch = env("LCX_E2E_BRANCH", "La Esperanza")
const pin = env("LCX_E2E_PIN", "1234")
const operator = {
  email: env("LCX_E2E_OPERATOR_EMAIL", "e2e.operator@cleanx.local"),
  password: env("LCX_E2E_OPERATOR_PASSWORD", env("E2E_PASSWORD", "Password123!")),
  fullName: env("LCX_E2E_OPERATOR_FULL_NAME", "Operador E2E"),
  role: "employee",
  branch,
  city: env("LCX_E2E_CITY", "CDMX"),
  shift: env("LCX_E2E_SHIFT", "AM"),
}
const manager = {
  email: env("LCX_E2E_MANAGER_EMAIL", "e2e.manager@cleanx.local"),
  password: env("LCX_E2E_MANAGER_PASSWORD", env("E2E_PASSWORD", "Password123!")),
  fullName: env("LCX_E2E_MANAGER_FULL_NAME", "Manager E2E"),
  role: "manager",
  branch,
  city: env("LCX_E2E_CITY", "CDMX"),
  shift: env("LCX_E2E_SHIFT", "AM"),
}

const shouldSeedTicket = env("LCX_E2E_SEED_TICKET", "true") !== "false"
const ticketAmount = Number(env("LCX_E2E_TICKET_AMOUNT", "1.00"))
if (!Number.isFinite(ticketAmount) || ticketAmount <= 0) {
  throw new Error("LCX_E2E_TICKET_AMOUNT must be a positive number")
}

const admin = createClient(supabaseUrl, serviceRoleKey, {
  auth: {
    autoRefreshToken: false,
    persistSession: false,
  },
})

const anon = createClient(supabaseUrl, anonKey, {
  auth: {
    autoRefreshToken: false,
    persistSession: false,
  },
})

async function ensureUser(userConfig) {
  const {
    data: { users },
    error: listError,
  } = await admin.auth.admin.listUsers({ page: 1, perPage: 1000 })
  if (listError) throw listError

  const existing = users.find((user) => user.email?.toLowerCase() === userConfig.email.toLowerCase())
  const attributes = {
    email: userConfig.email,
    password: userConfig.password,
    email_confirm: true,
    user_metadata: { full_name: userConfig.fullName },
  }
  const { data, error } = existing
    ? await admin.auth.admin.updateUserById(existing.id, attributes)
    : await admin.auth.admin.createUser(attributes)
  if (error || !data.user) {
    throw error ?? new Error(`Could not create/update auth user ${userConfig.email}`)
  }

  const nowIso = new Date().toISOString()
  const { error: profileError } = await admin.from("profiles").upsert(
    {
      id: data.user.id,
      full_name: userConfig.fullName,
      role: userConfig.role,
      city: userConfig.city,
      branch: userConfig.branch,
      shift: userConfig.shift,
      is_active: true,
      updated_at: nowIso,
    },
    { onConflict: "id" },
  )
  if (profileError) throw profileError

  return data.user.id
}

async function signIn(email, password) {
  const client = createClient(supabaseUrl, anonKey, {
    auth: {
      autoRefreshToken: false,
      persistSession: false,
    },
  })
  const { data, error } = await client.auth.signInWithPassword({ email, password })
  if (error || !data.session?.access_token) {
    throw error ?? new Error(`Could not sign in ${email}`)
  }
  return {
    client,
    accessToken: data.session.access_token,
  }
}

async function callPwa(path, token, body) {
  const response = await fetch(`${pwaBaseUrl}${path}`, {
    method: "POST",
    headers: {
      authorization: `Bearer ${token}`,
      "content-type": "application/json",
    },
    body: JSON.stringify(body),
  })
  const text = await response.text()
  let payload = null
  try {
    payload = text ? JSON.parse(text) : null
  } catch {
    payload = { raw: text }
  }
  if (!response.ok) {
    throw new Error(`${path} failed: ${response.status} ${JSON.stringify(payload)}`)
  }
  return payload
}

async function ensureOpeningChecklistCompleted(userId) {
  const today = new Date().toISOString().slice(0, 10)
  const { data: existing, error: existingError } = await admin
    .from("maintenance_checklists")
    .select("id")
    .eq("checklist_type", "entrada")
    .eq("checklist_date", today)
    .order("created_at", { ascending: false })
    .limit(1)
    .maybeSingle()
  if (existingError) throw existingError

  const payload = {
    status: "completed",
    completed_by: userId,
    completed_at: new Date().toISOString(),
    completion_notes: "Completed by Android hardware E2E setup",
  }

  if (existing?.id) {
    const { error } = await admin.from("maintenance_checklists").update(payload).eq("id", existing.id)
    if (error) throw error
    return
  }

  const { error } = await admin.from("maintenance_checklists").insert({
    checklist_type: "entrada",
    checklist_date: today,
    ...payload,
  })
  if (error) throw error
}

async function seedTicket(operatorToken) {
  const unique = Date.now().toString().slice(-8)
  const payload = await callPwa("/api/tickets", operatorToken, {
    source: "encargo",
    tickets: [
      {
        customer_name: `Android HW E2E ${unique}`,
        customer_phone: `55${unique}`,
        service_type: "wash-fold",
        service: "Lavado por kilo",
        status: "received",
        total_amount: ticketAmount,
        subtotal: ticketAmount,
        add_ons_total: 0,
        payment_status: "pending",
        payment_method: "card",
        paid_amount: 0,
        promised_pickup_date: new Date(Date.now() + 86_400_000).toISOString(),
      },
    ],
  })
  const created = Array.isArray(payload?.data) ? payload.data[0] : null
  if (!created?.id || !created?.ticket_number) {
    throw new Error(`Unexpected ticket response: ${JSON.stringify(payload)}`)
  }
  return {
    id: created.id,
    ticketNumber: created.ticket_number,
  }
}

async function main() {
  log(`webRoot=${webRoot}`)
  log(`pwaBaseUrl=${pwaBaseUrl}`)
  log(`branch=${branch}`)

  const operatorId = await ensureUser(operator)
  const managerId = await ensureUser(manager)
  await ensureOpeningChecklistCompleted(operatorId)

  const managerSession = await signIn(manager.email, manager.password)
  await callPwa("/api/device-auth/admin/pins", managerSession.accessToken, {
    profileId: operatorId,
    pin,
  })

  const operatorSession = await signIn(operator.email, operator.password)
  const ticket = shouldSeedTicket ? await seedTicket(operatorSession.accessToken) : null

  process.stdout.write(
    JSON.stringify(
      {
        branch,
        pin: "[REDACTED]",
        operatorId,
        operatorEmail: operator.email,
        operatorFullName: operator.fullName,
        managerId,
        ticket,
      },
      null,
      2,
    ),
  )
  process.stdout.write("\n")

  await anon.auth.signOut().catch(() => {})
}

main().catch((error) => {
  process.stderr.write(`[seed-device-auth] ERROR: ${error?.stack ?? error}\n`)
  process.exitCode = 1
})
