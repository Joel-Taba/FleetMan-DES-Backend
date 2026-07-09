#!/usr/bin/env python3
"""Full read+write business operations test aligned with frontend API calls."""
import datetime
import json
import time
import urllib.error
import urllib.request

BASE = "http://localhost:8081"
PWD = "FleetMan2026!"


def req(method, path, token=None, body=None):
    headers = {"Accept": "application/json"}
    data = None
    if body is not None:
        data = json.dumps(body).encode()
        headers["Content-Type"] = "application/json"
    if token:
        headers["Authorization"] = f"Bearer {token}"
    request = urllib.request.Request(BASE + path, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(request, timeout=40) as resp:
            text = resp.read().decode() or "null"
            try:
                payload = json.loads(text)
            except json.JSONDecodeError:
                payload = {"raw": text[:300]}
            return resp.status, payload
    except urllib.error.HTTPError as e:
        text = e.read().decode() or ""
        try:
            payload = json.loads(text)
        except json.JSONDecodeError:
            payload = {"raw": text[:300]}
        return e.code, payload


def data_of(payload):
    if isinstance(payload, dict) and "data" in payload:
        return payload["data"]
    return payload


def login(identifier):
    status, payload = req("POST", "/api/v1/auth/login", body={"identifier": identifier, "password": PWD})
    token = data_of(payload).get("accessToken") if isinstance(payload, dict) else None
    return status, token


def record(results, role, op, status, payload=None):
    ok = 200 <= status < 300 or status == 204
    detail = None
    if isinstance(payload, dict):
        detail = payload.get("detail") or payload.get("message") or payload.get("errorCode")
    results.append({"role": role, "op": op, "status": status, "ok": ok, "detail": detail})
    return ok


def main():
    suffix = str(int(time.time()))
    today = datetime.date.today().isoformat()
    month = datetime.date.today().replace(day=1).isoformat()
    results = []

    # ── SUPER ADMIN ─────────────────────────────────────────────────────────
    _, tok_super = login("joeltaba4@gmail.com")
    plan_id = None
    if tok_super:
        s, p = req("POST", "/api/v1/admin/super/plans", tok_super, {
            "name": f"Plan QA {suffix}",
            "description": "auto test",
            "maxFleets": 2,
            "maxVehicles": 10,
            "maxDrivers": 10,
            "monthlyPrice": 19900,
            "annualPrice": 199000,
            "currency": "XAF",
            "features": "TRIPS,SCHEDULES",
            "technicalFeatures": [{"key": "TRIPS", "label": "Trajets", "enabled": True}],
        })
        record(results, "super-admin", "create_plan", s, p)
        if 200 <= s < 300:
            plan_id = data_of(p).get("id")

        if plan_id:
            s, p = req("PUT", f"/api/v1/admin/super/plans/{plan_id}", tok_super, {
                "name": f"Plan QA {suffix} U",
                "description": "updated",
                "maxFleets": 3,
                "maxVehicles": 12,
                "maxDrivers": 12,
                "monthlyPrice": 24900,
                "annualPrice": 249000,
                "features": "TRIPS,SCHEDULES,OPERATIONS",
            })
            record(results, "super-admin", "update_plan", s, p)

            s, p = req("PUT", f"/api/v1/admin/super/plans/{plan_id}/features", tok_super, {
                "features": [
                    {"key": "TRIPS", "label": "Trajets", "enabled": True},
                    {"key": "GEOFENCING", "label": "Geofencing", "enabled": False},
                ]
            })
            record(results, "super-admin", "update_plan_features", s, p)

            s, p = req("DELETE", f"/api/v1/admin/super/plans/{plan_id}", tok_super)
            record(results, "super-admin", "delete_plan", s, p)

        s, p = req("PUT", "/api/v1/admin/super/settings/subscription-grace-days", tok_super, {"graceDays": 10})
        record(results, "super-admin", "update_grace_days", s, p)

    # ── ADMIN ───────────────────────────────────────────────────────────────
    _, tok_admin = login("admin@fleetman.cm")
    ref_id = None
    if tok_admin:
        s, p = req("POST", "/api/v1/admin/resources/manufacturers", tok_admin, {"name": f"Maker QA {suffix}"})
        record(results, "admin", "create_manufacturer", s, p)
        if 200 <= s < 300:
            ref_id = data_of(p).get("id")

        if ref_id:
            s, p = req("PUT", f"/api/v1/admin/resources/manufacturers/{ref_id}", tok_admin, {"name": f"Maker QA {suffix} U"})
            record(results, "admin", "update_manufacturer", s, p)
            s, p = req("DELETE", f"/api/v1/admin/resources/manufacturers/{ref_id}", tok_admin)
            record(results, "admin", "delete_manufacturer", s, p)

        s, p = req("POST", "/api/v1/admin/resources/colors", tok_admin, {"name": f"Color QA {suffix}"})
        record(results, "admin", "create_color", s, p)
        color_id = data_of(p).get("id") if 200 <= s < 300 and isinstance(p, dict) else None
        if color_id:
            req("DELETE", f"/api/v1/admin/resources/colors/{color_id}", tok_admin)

    # ── MANAGER ─────────────────────────────────────────────────────────────
    _, tok_mgr = login("manager1@fleetman.cm")
    fleet_id = vehicle_id = driver_id = trip_id = schedule_id = assignment_id = None
    budget_id = expense_id = incident_id = None

    if tok_mgr:
        s, p = req("POST", "/api/v1/fleets", tok_mgr, {"name": f"Fleet QA {suffix}"})
        record(results, "manager", "create_fleet", s, p)
        if 200 <= s < 300:
            fleet_id = data_of(p).get("id")

        if fleet_id:
            s, p = req("PUT", f"/api/v1/fleets/{fleet_id}", tok_mgr, {"name": f"Fleet QA {suffix} U"})
            record(results, "manager", "update_fleet", s, p)

            s, p = req("POST", "/api/v1/vehicles", tok_mgr, {
                "fleetId": fleet_id,
                "licensePlate": f"QA{suffix[-6:]}",
                "brand": "Toyota",
                "model": "Hilux",
                "manufacturingYear": 2021,
                "fuelType": "DIESEL",
                "transmissionType": "MANUAL",
                "color": "WHITE",
            })
            record(results, "manager", "create_vehicle", s, p)
            if 200 <= s < 300:
                vehicle_id = data_of(p).get("id")

            s, p = req("POST", "/api/v1/drivers", tok_mgr, {
                "fleetId": fleet_id,
                "firstName": "QA",
                "lastName": f"Driver{suffix[-4:]}",
                "licenceNumber": f"LIC{suffix[-7:]}",
                "email": f"qa.driver.{suffix}@fleetman.cm",
                "phone": "+237690000111",
            })
            record(results, "manager", "create_driver", s, p)
            if 200 <= s < 300:
                driver_id = data_of(p).get("userId") or data_of(p).get("id")

            if driver_id:
                s, p = req("PUT", f"/api/v1/drivers/{driver_id}", tok_mgr, {"status": "ACTIVE"})
                record(results, "manager", "update_driver", s, p)

        if vehicle_id and driver_id:
            s, p = req("POST", "/api/v1/trips", tok_mgr, {
                "vehicleId": vehicle_id,
                "driverId": driver_id,
                "startDate": today,
                "startTime": "08:00",
                "departureLocation": "Yaounde",
            })
            record(results, "manager", "create_trip", s, p)
            if 200 <= s < 300:
                trip_id = data_of(p).get("id")

            if trip_id:
                s, p = req("POST", f"/api/v1/trips/{trip_id}/start", tok_mgr)
                record(results, "manager", "start_trip", s, p)
                s, p = req("PATCH", f"/api/v1/trips/{trip_id}", tok_mgr, {"missionObject": "QA mission"})
                record(results, "manager", "patch_trip", s, p)

        if fleet_id:
            s, p = req("POST", "/api/v1/schedules", tok_mgr, {
                "fleetId": fleet_id,
                "title": f"Schedule QA {suffix}",
                "periodType": "WEEKLY",
                "startDate": today,
                "endDate": today,
                "notes": "auto",
            })
            record(results, "manager", "create_schedule", s, p)
            if 200 <= s < 300:
                schedule_id = data_of(p).get("id")

            if schedule_id:
                s, p = req("PATCH", f"/api/v1/schedules/{schedule_id}/publish", tok_mgr)
                record(results, "manager", "publish_schedule", s, p)

        if schedule_id and fleet_id and vehicle_id and driver_id:
            start_dt = f"{today}T08:00:00"
            end_dt = f"{today}T18:00:00"
            s, p = req("POST", "/api/v1/assignments", tok_mgr, {
                "scheduleId": schedule_id,
                "fleetId": fleet_id,
                "vehicleId": vehicle_id,
                "driverId": driver_id,
                "startDatetime": start_dt,
                "endDatetime": end_dt,
                "startLocation": "A",
                "endLocation": "B",
                "estimatedKm": 120,
            })
            record(results, "manager", "create_assignment", s, p)
            if 200 <= s < 300:
                assignment_id = data_of(p).get("id")

            if assignment_id:
                s, p = req("PATCH", f"/api/v1/assignments/{assignment_id}", tok_mgr, {"vehicleId": vehicle_id})
                record(results, "manager", "patch_assignment", s, p)

        if vehicle_id:
            s, p = req("POST", "/api/v1/operations/incidents", tok_mgr, {
                "type": "ACCIDENT",
                "description": "QA incident",
                "severity": "LOW",
                "cost": 1000,
                "vehicleId": vehicle_id,
            })
            record(results, "manager", "create_incident", s, p)
            if 200 <= s < 300:
                incident_id = data_of(p).get("id")

            if incident_id:
                s, p = req("PATCH", f"/api/v1/operations/incidents/{incident_id}/status", tok_mgr, {"status": "RESOLVED"})
                record(results, "manager", "update_incident_status", s, p)

            s, p = req("POST", "/api/v1/operations/maintenances", tok_mgr, {
                "subject": "QA maintenance",
                "cost": 5000,
                "report": "ok",
                "vehicleId": vehicle_id,
                "locationName": "Depot",
            })
            record(results, "manager", "create_maintenance", s, p)

            s, p = req("POST", "/api/v1/operations/fuel-recharges", tok_mgr, {
                "quantity": 20,
                "price": 15000,
                "vehicleId": vehicle_id,
                "stationName": "Total",
            })
            record(results, "manager", "create_fuel_recharge", s, p)

        if fleet_id:
            s, p = req("POST", "/api/v1/budget/budgets", tok_mgr, {
                "scope": "FLEET",
                "entityId": fleet_id,
                "amount": 500000,
                "budgetMonth": month,
                "notes": "auto",
            })
            record(results, "manager", "create_budget", s, p)
            if 200 <= s < 300:
                budget_id = data_of(p).get("id")

        if vehicle_id:
            s, p = req("POST", "/api/v1/budget/expenses", tok_mgr, {
                "vehicleId": vehicle_id,
                "expenseType": "MAINTENANCE",
                "amount": 12000,
                "description": "QA expense",
            })
            record(results, "manager", "create_expense", s, p)
            if 200 <= s < 300:
                expense_id = data_of(p).get("id")

            if expense_id:
                s, p = req("PATCH", f"/api/v1/budget/expenses/{expense_id}/approve", tok_mgr)
                record(results, "manager", "approve_expense", s, p)

        if budget_id:
            s, p = req("POST", f"/api/v1/budget/budgets/{budget_id}/recalculate", tok_mgr)
            record(results, "manager", "recalculate_budget", s, p)
            s, p = req("DELETE", f"/api/v1/budget/budgets/{budget_id}", tok_mgr)
            record(results, "manager", "delete_budget", s, p)

        s, p = req("PATCH", "/api/v1/alerts/events/read-all", tok_mgr)
        record(results, "manager", "mark_all_alerts_read", s, p)

        # cleanup
        if vehicle_id:
            s, p = req("DELETE", f"/api/v1/vehicles/{vehicle_id}", tok_mgr)
            record(results, "manager", "delete_vehicle", s, p)
        if fleet_id:
            s, p = req("DELETE", f"/api/v1/fleets/{fleet_id}", tok_mgr)
            record(results, "manager", "delete_fleet", s, p)

    summary = {}
    for role in ("super-admin", "admin", "manager"):
        role_ops = [r for r in results if r["role"] == role]
        summary[role] = {
            "total": len(role_ops),
            "ok": sum(1 for r in role_ops if r["ok"]),
            "fails": [r for r in role_ops if not r["ok"]],
        }

    print(json.dumps({"summary": summary, "results": results}, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
