/*
 * Vérification de bout en bout gestionnaire ↔ application mobile du chauffeur.
 *
 * 1. Le gestionnaire agit par l'API (poste, trajet, pièce demandée, absence, incident + demande d'information).
 * 2. L'application mobile (export web, servi par `expo start --web` sur :8082) est ouverte en tant que driver.bella :
 *    chaque information doit apparaître dans les notifications et sur l'écran concerné ; une notification ouvre l'écran.
 * 3. Sens inverse depuis l'application : accusé de réception des postes et réponse à l'incident, vus par le gestionnaire.
 * 4. Tout est supprimé ou restauré à la fin.
 *
 * Usage : node scripts/mobile_cross_role_check.cjs   (backend :8081, application mobile :8082, Playwright du front)
 */
const fs = require("fs");
const path = require("path");
const { execFileSync } = require("child_process");
const { chromium } = require(path.join(__dirname, "../../fleet_management_front/node_modules/playwright"));

const API = "http://localhost:8081";
const APP = process.env.MOBILE_URL || "http://localhost:8082";
const SHOTS = process.env.SHOTS_DIR || "/tmp/mobile-cross-role-shots";
const PW = fs.readFileSync(path.join(__dirname, "../.env"), "utf8").split("\n")
  .find((l) => l.startsWith("DEMO_PASSWORD=")).split("=").slice(1).join("=").trim();

const results = [];
function check(label, ok, detail = "") {
  results.push(!!ok);
  console.log(`  ${ok ? "✅" : "❌"} ${label} ${detail}`);
}
function sql(query) {
  return execFileSync("docker", ["exec", "-i", "fleet-management-db", "sh", "-c", 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -At'],
    { input: query, encoding: "utf8" }).trim();
}
async function call(method, p, token, body) {
  const res = await fetch(API + p, {
    method,
    headers: { "content-type": "application/json", ...(token ? { authorization: `Bearer ${token}` } : {}) },
    body: body ? JSON.stringify(body) : undefined,
  });
  let data = null;
  try { data = await res.json(); } catch { /* corps vide */ }
  if (data && typeof data === "object" && "success" in data && "data" in data) data = data.data;
  return [res.status, data];
}
async function login(user) {
  for (let i = 0; i < 6; i++) {
    const [s, d] = await call("POST", "/api/v1/auth/login", null, { identifier: user, password: PW });
    if (s === 200 && d?.accessToken) return d.accessToken;
    await new Promise((r) => setTimeout(r, 15000)); // Kernel parfois indisponible (AUTH_006)
  }
  throw new Error(`connexion impossible pour ${user}`);
}
const pad = (n) => String(n).padStart(2, "0");
const dayIso = (n) => { const d = new Date(); d.setDate(d.getDate() + n); return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`; };
const norm = (s) => String(s || "").replace(/\s+/g, " ").trim();

(async () => {
  fs.mkdirSync(SHOTS, { recursive: true });
  const MGR = await login("manager.dupont");
  const DRV = await login("driver.bella");
  const BELLA = sql("select id from fleet.users where username='driver.bella'");
  const DOUALA = sql("select id from fleet.fleets where name='Flotte Douala'");
  const VEH = sql("select id from fleet.vehicles where license_plate='DL-201-CC'");
  const mark = sql("select coalesce(max(created_at), 'epoch') from fleet.notifications");
  const markReq = sql("select coalesce(max(requested_at), 'epoch') from fleet.document_requests");
  const savedAck = sql(`select coalesce(string_agg(id::text || '|' || coalesce(acknowledged_at::text, ''), ','), '') from fleet.assignments where driver_id='${BELLA}'`);
  const created = { shift: null, trip: null, absence: null, incident: null };
  const browser = await chromium.launch({ channel: "chrome", headless: true });
  try {
    console.log("1. Le gestionnaire agit pour la chauffeuse");
    let [s, r] = await call("POST", "/api/v1/assignments", MGR, {
      fleetId: DOUALA, vehicleId: VEH, driverId: BELLA, startDatetime: `${dayIso(20)}T08:00:00`, endDatetime: `${dayIso(20)}T16:00:00`,
      notes: "Vérification mobile",
    });
    created.shift = r?.id;
    check("poste créé (hors planning)", s === 201, `(${s} ${r?.code || ""})`);
    [s, r] = await call("POST", "/api/v1/trips", MGR, {
      vehicleId: VEH, driverId: BELLA, fleetId: DOUALA, startDate: dayIso(21), startTime: "09:00:00",
      scheduledReturnDatetime: `${dayIso(21)}T15:00:00`, departureLocation: "Dépôt Bonabéri", departureKmIndex: 1000,
      departureFuelIndex: 40, missionObject: "Tournée vérification mobile",
      stops: [{ stopType: "DELIVERY", location: "Boutique Akwa (vérification)", plannedArrival: `${dayIso(21)}T10:00:00` }],
      details: [{ itemType: "CARGO", description: "Cartons de vérification", quantity: 4, stopIndex: 0 }],
    });
    created.trip = r?.id;
    const tripCode = r?.tripCode;
    check("trajet planifié", s === 201 && tripCode, `(${s} ${tripCode || r?.code || ""})`);
    [s, r] = await call("POST", `/api/v1/drivers/${BELLA}/documents/requests`, MGR, { docType: "PROFESSIONAL_CARD", message: "Merci de la déposer cette semaine" });
    check("pièce demandée", s === 200 || s === 201, `(${s} ${r?.code || ""})`);
    [s, r] = await call("POST", `/api/v1/drivers/${BELLA}/absences`, MGR, { type: "LEAVE", startDate: dayIso(30), endDate: dayIso(31), reason: "Congé de vérification mobile" });
    created.absence = r?.id;
    check("absence enregistrée", s === 201, `(${s} ${r?.code || ""})`);
    [s, r] = await call("POST", "/api/v1/operations/incidents", MGR, {
      type: "BREAKDOWN", severity: "LOW", description: "Vérification mobile : voyant moteur allumé", vehicleId: VEH, driverId: BELLA,
      reportedBy: "Gestionnaire", vehicleDrivable: true,
    });
    created.incident = r?.id;
    check("incident déclaré par le gestionnaire", s === 201, `(${s} ${r?.code || ""})`);
    [s, r] = await call("POST", `/api/v1/operations/incidents/${created.incident}/comments`, MGR, { message: "Pouvez-vous envoyer une photo du tableau de bord ?", requestInfo: true });
    check("demande d'information envoyée", s === 200 || s === 201, `(${s} ${r?.code || ""})`);

    [s, r] = await call("POST", `/api/v1/messages/drivers/${BELLA}`, MGR, { body: "Vérification mobile : passez au dépôt avant 8 h." });
    check("message envoyé par le gestionnaire", s === 201, `(${s} ${r?.code || ""})`);

    const [, notifPage] = await call("GET", "/api/v1/notifications?page=0&size=50", DRV);
    const notifs = (notifPage?.content || notifPage || []).filter((n) => n.createdAt && n.createdAt > mark.replace(" ", "T"));
    const expected = ["SHIFT_CHANGED", "TRIP_ASSIGNED", "DOCUMENT_REQUESTED", "ABSENCE_RECORDED", "INCIDENT_DECLARED", "INCIDENT_INFO_REQUESTED", "MESSAGE_RECEIVED"];
    const byEvent = Object.fromEntries(expected.map((e) => [e, notifs.find((n) => (n.event || n.data?.type) === e)]));
    check("la chauffeuse a reçu les 7 notifications", expected.every((e) => byEvent[e]),
      `(${expected.filter((e) => !byEvent[e]).join(", ") || "toutes"})`);

    console.log("2. Application mobile de la chauffeuse");
    const ctx = await browser.newContext({ viewport: { width: 400, height: 860 } });
    await ctx.addInitScript(() => { try { localStorage.setItem("fleetman-driver-lang", "fr"); } catch { /* */ } });
    const page = await ctx.newPage();
    const errors = [];
    page.on("pageerror", (e) => errors.push(String(e.message).slice(0, 120)));
    // Le premier chargement après une reconstruction du bundle peut être lent : on attend l'écran prêt,
    // on vérifie la saisie et on réessaie au besoin.
    for (let attempt = 0; attempt < 3 && (attempt === 0 || page.url().includes("/login")); attempt++) {
      await page.goto(`${APP}/login`, { waitUntil: "networkidle", timeout: 180000 });
      await page.getByText("Se connecter", { exact: false }).last().waitFor({ timeout: 120000 });
      await page.waitForTimeout(3000);
      const inputs = page.locator("input");
      await inputs.nth(0).fill("driver.bella");
      await inputs.nth(1).fill(PW);
      if ((await inputs.nth(0).inputValue()) !== "driver.bella") continue;
      await page.getByText("Se connecter", { exact: false }).last().click();
      for (let i = 0; i < 40 && page.url().includes("/login"); i++) await page.waitForTimeout(2000);
    }
    check("connexion dans l'application", !page.url().includes("/login"));
    const open = async (route, shot) => {
      await page.goto(`${APP}${route}`, { waitUntil: "networkidle", timeout: 120000 });
      await page.waitForTimeout(3500);
      await page.screenshot({ path: path.join(SHOTS, shot), fullPage: true });
      return norm(await page.innerText("body"));
    };

    let text = await open("/notifications", "01-notifications.png");
    for (const e of expected) {
      const n = byEvent[e];
      check(`notification visible : ${e}`, n && text.includes(norm(n.title)), `(${n ? norm(n.title) : "absente"})`);
    }
    if (byEvent.INCIDENT_INFO_REQUESTED) {
      await page.getByText(norm(byEvent.INCIDENT_INFO_REQUESTED.title)).first().click();
      await page.waitForTimeout(3500);
      check("toucher la notification ouvre le dossier d'incident", page.url().includes(`/incident/${created.incident}`), `(${page.url().replace(APP, "")})`);
    }
    text = await open("/assignments", "02-assignments.png");
    check("poste visible dans « Mes affectations » (08:00 → 16:00)", /08:00\s*→\s*16:00/.test(text));
    check("absence visible dans « Mes absences »", text.includes("Congé de vérification mobile"));
    text = await open("/trips", "03-trips.png");
    check("trajet visible dans l'onglet Trajets", tripCode && text.includes(tripCode), `(${tripCode})`);
    text = await open("/documents", "04-documents.png");
    check("pièce demandée visible dans « Mes documents »", /Demand[ée]e? par votre gestionnaire/i.test(text));
    text = await open("/messages", "05a-messages.png");
    check("message du gestionnaire visible dans « Messages »", text.includes("passez au dépôt avant 8 h"));
    const composer = page.locator("textarea, input").last();
    await composer.fill("Vérification mobile : bien reçu, j'y serai.");
    await composer.press("Enter").catch(() => {});
    const sendBtn = page.getByRole("button", { name: /Envoyer/ });
    if (await sendBtn.count()) await sendBtn.last().click().catch(() => {});
    await page.waitForTimeout(4000);
    await page.screenshot({ path: path.join(SHOTS, "05b-messages-reply.png"), fullPage: true });
    [s, r] = await call("GET", `/api/v1/messages/drivers/${BELLA}`, MGR);
    check("le gestionnaire reçoit la réponse écrite dans l'application",
      s === 200 && (r || []).some((m) => m.fromDriver && String(m.body).includes("bien reçu, j'y serai")), `(${s})`);
    const [, mgrN] = await call("GET", "/api/v1/notifications?page=0&size=50", MGR);
    check("le gestionnaire est notifié du message (MESSAGE_RECEIVED)",
      (mgrN?.content || mgrN || []).some((n) => (n.event || n.data?.type) === "MESSAGE_RECEIVED" && n.data?.driverId === BELLA));
    text = await open(`/incident/${created.incident}`, "05-incident.png");
    check("incident : message du gestionnaire visible", text.includes("Pouvez-vous envoyer une photo du tableau de bord"));

    console.log("3. Sens inverse : la chauffeuse agit depuis l'application");
    const reply = page.locator("textarea, input").last();
    await reply.fill("Voici les informations : le voyant s'est éteint après redémarrage.");
    const send = page.getByText(/Envoyer|Répondre/).last();
    await send.click();
    await page.waitForTimeout(4000);
    await page.screenshot({ path: path.join(SHOTS, "06-incident-reply.png"), fullPage: true });
    [s, r] = await call("GET", `/api/v1/operations/incidents/${created.incident}/case`, MGR);
    const timeline = r?.timeline || [];
    check("le gestionnaire voit la réponse dans l'historique du dossier",
      timeline.some((e) => String(e.note || e.message || e.detail || "").includes("voyant s'est éteint")), `(${s})`);
    const [, mgrNotifs] = await call("GET", "/api/v1/notifications?page=0&size=50", MGR);
    const mgrList = mgrNotifs?.content || mgrNotifs || [];
    check("le gestionnaire est notifié de la réponse (INCIDENT_DRIVER_REPLY)",
      mgrList.some((n) => (n.event || n.data?.type) === "INCIDENT_DRIVER_REPLY" && n.data?.incidentId === created.incident));

    await open("/assignments", "07-assignments-before-ack.png");
    const ackAll = page.getByText("Tout confirmer");
    if (await ackAll.count()) {
      await ackAll.first().click();
      await page.waitForTimeout(5000);
    }
    [s, r] = await call("GET", `/api/v1/assignments/${created.shift}`, MGR);
    check("le gestionnaire voit l'accusé de réception du poste", s === 200 && r?.acknowledgedAt, `(${s} ${r?.acknowledgedAt || ""})`);
    check("aucune erreur JavaScript dans l'application", errors.length === 0, errors.length ? `(${errors[0]})` : "");
    await ctx.close();
  } catch (e) {
    check("déroulé sans exception", false, String(e.message).slice(0, 200));
  } finally {
    await browser.close();
    // Restauration : accusés de réception d'origine, puis suppression de tout ce qui a été créé.
    for (const row of savedAck ? savedAck.split(",") : []) {
      const [id, ack] = row.split("|");
      sql(`update fleet.assignments set acknowledged_at=${ack ? `'${ack}'` : "NULL"} where id='${id}'`);
    }
    if (created.incident) {
      sql(`delete from fleet.alert_events where source_entity_id='${created.incident}';
           delete from fleet.notifications where data like '%${created.incident}%';
           delete from fleet.maintenances where id in (select maintenance_id from fleet.incident_cases where incident_id='${created.incident}');
           delete from fleet.incidents where id='${created.incident}';`);
    }
    if (created.trip) {
      sql(`delete from fleet.notifications where data like '%${created.trip}%';
           delete from fleet.trip_details where trip_id='${created.trip}';
           delete from fleet.trip_stops where trip_id='${created.trip}';
           delete from fleet.trips where id='${created.trip}';`);
    }
    if (created.shift) sql(`delete from fleet.notifications where data like '%${created.shift}%'; delete from fleet.assignments where id='${created.shift}';`);
    if (created.absence) sql(`delete from fleet.notifications where data like '%${created.absence}%'; delete from fleet.driver_absences where id='${created.absence}';`);
    sql(`delete from fleet.driver_messages where thread_driver_id='${BELLA}' and created_at > '${mark}';`);
    sql(`delete from fleet.document_requests where target_id='${BELLA}' and requested_at > '${markReq}';
         delete from fleet.notifications where created_at > '${mark}' and user_id in ('${BELLA}', (select manager_id from fleet.fleets where id='${DOUALA}'));`);
    console.log(`\n${results.filter(Boolean).length}/${results.length} vérifications réussies`);
    process.exit(results.every(Boolean) ? 0 : 1);
  }
})();
