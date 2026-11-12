#!/usr/bin/env python3
"""Update the existing local demo realm for browser registration; preserve users and roles."""

import json
import os
from pathlib import Path
import sys
from urllib.error import HTTPError
from urllib.parse import urlencode
from urllib.request import Request, urlopen

BASE_URL = os.environ.get("KEYCLOAK_URL", "http://localhost:8180").rstrip("/")
REALM = os.environ.get("KEYCLOAK_REALM", "demo")
API_URL = os.environ.get("API_URL", "http://localhost:8280").rstrip("/")


def request(method, path, token=None, body=None, form=None):
    headers = {}
    data = None
    if token:
        headers["Authorization"] = "Bearer " + token
    if body is not None:
        headers["Content-Type"] = "application/json"
        data = json.dumps(body).encode()
    if form is not None:
        headers["Content-Type"] = "application/x-www-form-urlencoded"
        data = urlencode(form).encode()
    try:
        with urlopen(Request(BASE_URL + path, data=data, headers=headers, method=method), timeout=15) as response:
            raw_body = response.read()
            return json.loads(raw_body) if raw_body else None
    except HTTPError as error:
        raise RuntimeError(f"Keycloak {method} {path}: HTTP {error.code}") from None


def configure():
    token = request("POST", "/realms/master/protocol/openid-connect/token", form={
        "grant_type": "password", "client_id": "admin-cli",
        "username": os.environ.get("KEYCLOAK_BOOTSTRAP_ADMIN_USERNAME", "admin"),
        "password": os.environ.get("KEYCLOAK_BOOTSTRAP_ADMIN_PASSWORD", "admin-local-only"),
    })["access_token"]
    realm_path = "/admin/realms/" + REALM
    request("PUT", realm_path, token, body={"registrationAllowed": True})
    bootstrap = json.loads((Path(__file__).resolve().parents[1] / "keycloak/demo-realm.json").read_text())
    desired = next(client for client in bootstrap["clients"] if client["clientId"] == "orders-ui")
    desired["secret"] = os.environ.get("KEYCLOAK_UI_CLIENT_SECRET", desired["secret"])
    desired["redirectUris"] = sorted(set(desired["redirectUris"] + [API_URL + "/login/oauth2/code/keycloak"]))
    logout_uris = desired["attributes"]["post.logout.redirect.uris"].split("##")
    desired["attributes"]["post.logout.redirect.uris"] = "##".join(sorted(set(logout_uris + [API_URL + "/"])))
    clients = request("GET", realm_path + "/clients?clientId=orders-ui", token)
    if clients:
        request("PUT", realm_path + "/clients/" + clients[0]["id"], token, body=desired)
    else:
        request("POST", realm_path + "/clients", token, body=desired)
    scopes = request("GET", realm_path + "/client-scopes", token)
    scopes_by_name = {scope["name"]: scope for scope in scopes}
    clients_and_scopes = {
        "orders-ui": desired["defaultClientScopes"],
        "roles-sync": ["basic"],
        "demo-cli": ["basic"],
    }
    for client_name, required_scopes in clients_and_scopes.items():
        matching_clients = request("GET", realm_path + "/clients?clientId=" + client_name, token)
        for client in matching_clients:
            for scope_name in required_scopes:
                request("PUT", realm_path + "/clients/" + client["id"]
                        + "/default-client-scopes/" + scopes_by_name[scope_name]["id"], token)
    resources = request("GET", realm_path + "/clients?clientId=orders-api", token)
    if len(resources) != 1:
        raise RuntimeError("Expected the existing orders-api client")
    resource_identifier = resources[0]["id"]
    reader_role = request("GET", realm_path + "/clients/" + resource_identifier + "/roles/READER", token)
    groups = request("GET", realm_path + "/groups?search=Readers&exact=true", token)
    if not groups:
        request("POST", realm_path + "/groups", token, body={"name": "Readers"})
        groups = request("GET", realm_path + "/groups?search=Readers&exact=true", token)
    group_path = realm_path + "/groups/" + groups[0]["id"]
    request("POST", group_path + "/role-mappings/clients/" + resource_identifier, token, body=[reader_role])
    request("PUT", realm_path + "/default-groups/" + groups[0]["id"], token)
    print("UI client configured; registration enabled; new users join Readers (READER).")
    print("Browser UI: " + API_URL + "/")


if __name__ == "__main__":
    try:
        configure()
    except (RuntimeError, OSError, KeyError, ValueError) as error:
        print("FAIL: " + str(error), file=sys.stderr)
        sys.exit(1)
