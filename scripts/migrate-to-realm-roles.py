#!/usr/bin/env python3
"""One-time migration for an existing demo; fresh installations only use realm import."""

import json
import os
from pathlib import Path
import runpy
from urllib.parse import quote

HELPERS = runpy.run_path(str(Path(__file__).with_name("configure-user-operator.py")))
request = HELPERS["request"]
REALM_PATH = HELPERS["REALM_PATH"]


def migrate():
    token = request("POST", "/realms/master/protocol/openid-connect/token", form={
        "grant_type": "password", "client_id": "admin-cli",
        "username": os.environ.get("KEYCLOAK_BOOTSTRAP_ADMIN_USERNAME", "admin"),
        "password": os.environ.get("KEYCLOAK_BOOTSTRAP_ADMIN_PASSWORD", "admin-local-only"),
    })["access_token"]
    client_identifier = HELPERS["find_client"](token, "orders-api")
    roles_path = REALM_PATH + "/clients/" + client_identifier + "/roles"
    bootstrap = json.loads((Path(__file__).resolve().parents[1] / "keycloak/demo-realm.json").read_text())
    definitions = bootstrap["roles"]["client"]["orders-api"]
    existing = {role["name"] for role in request("GET", roles_path, token)}
    for definition in definitions:
        role_path = roles_path + "/" + quote(definition["name"], safe="")
        role = {"name": definition["name"], "description": definition["description"]}
        if definition["name"] in existing:
            current = request("GET", role_path, token)
            role["attributes"] = {key: value for key, value in current.get("attributes", {}).items()
                                  if key != "managed-by"}
            request("PUT", role_path, token, body=role)
        else:
            request("POST", roles_path, token, body=role)
    for definition in definitions:
        composites_path = roles_path + "/" + quote(definition["name"], safe="") + "/composites"
        current = request("GET", composites_path, token)
        desired_names = definition.get("composites", {}).get("client", {}).get("orders-api", [])
        desired = [request("GET", roles_path + "/" + quote(name, safe=""), token) for name in desired_names]
        desired_ids = {role["id"] for role in desired}
        obsolete = [role for role in current if role["id"] not in desired_ids]
        if obsolete:
            request("DELETE", composites_path, token, body=obsolete)
        if desired:
            request("POST", composites_path, token, body=desired)
    for client in request("GET", REALM_PATH + "/clients?clientId=roles-sync", token):
        request("DELETE", REALM_PATH + "/clients/" + client["id"], token)
    print("Role definitions and composites applied from realm JSON; user mappings and role IDs preserved.")
    print("Obsolete roles-sync client removed. Application needs no Keycloak administrative credentials.")


if __name__ == "__main__":
    migrate()
