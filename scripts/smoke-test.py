#!/usr/bin/env python3
"""Real HTTP checks against the local Docker Compose demo. No third-party Python packages."""

import json
import os
import sys
import time
from urllib.error import HTTPError
from urllib.parse import urlencode, quote
from urllib.request import Request, urlopen

API_URL = os.environ.get("API_URL", "http://localhost:8080").rstrip("/")
KEYCLOAK_URL = os.environ.get("KEYCLOAK_URL", "http://localhost:8180").rstrip("/")
TOKEN_URL = KEYCLOAK_URL + "/realms/demo/protocol/openid-connect/token"


def request(method, url, token=None, body=None, form=None):
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
        with urlopen(Request(url, data=data, headers=headers, method=method), timeout=10) as response:
            raw_body = response.read()
            return response.status, json.loads(raw_body) if raw_body else None
    except HTTPError as error:
        return error.code, None


def access_token(username):
    status, response = request("POST", TOKEN_URL, form={
        "grant_type": "password", "client_id": "demo-cli", "username": username,
        "password": username + "-local-only",
    })
    assert status == 200, f"Token request for {username}: expected 200, got {status}"
    return response["access_token"]


def wait_until_ready():
    deadline = time.monotonic() + 120
    while time.monotonic() < deadline:
        try:
            token = access_token("administrator")
            status, profile = request("GET", API_URL + "/api/profile", token)
            if status == 200 and "user:read" in profile["authorities"]:
                return
        except (OSError, AssertionError):
            pass
        time.sleep(1)
    raise AssertionError("Application did not become ready with synchronized roles within 120 seconds")


def check_access():
    paths = ["/api/profile", "/api/orders", "/api/orders", "/api/users", "/api/admin/audit"]
    expected_by_user = {
        "reader": [200, 200, 403, 403, 403],
        "manager": [200, 200, 201, 403, 403],
        "administrator": [200, 200, 201, 200, 200],
    }
    for username, expected_statuses in expected_by_user.items():
        token = access_token(username)
        for index, (path, expected) in enumerate(zip(paths, expected_statuses)):
            method = "POST" if index == 2 else "GET"
            body = {"item": "Smoke test order"} if method == "POST" else None
            actual, response = request(method, API_URL + path, token, body=body)
            assert actual == expected, f"{username} {method} {path}: expected {expected}, got {actual}"
            if path == "/api/profile" and actual == 200:
                assert response["subject"], f"{username}: token must identify its subject (basic scope)"
            print(f"PASS {username:14} {method:4} {path:18} -> {actual}")
    assert request("GET", API_URL + "/api/public")[0] == 200
    assert request("GET", API_URL + "/api/orders")[0] == 401
    assert request("GET", API_URL + "/api/orders", "invalid-token")[0] == 401


def check_synchronized_roles():
    status, response = request("POST", TOKEN_URL, form={
        "grant_type": "client_credentials", "client_id": "roles-sync",
        "client_secret": os.environ.get("KEYCLOAK_SYNC_CLIENT_SECRET", "demo-sync-secret-local-only"),
    })
    assert status == 200, f"Service account token request: expected 200, got {status}"
    token = response["access_token"]
    status, clients = request("GET", KEYCLOAK_URL + "/admin/realms/demo/clients?clientId=orders-api", token)
    assert status == 200 and len(clients) == 1, "Cannot resolve orders-api client UUID"
    role_url = KEYCLOAK_URL + "/admin/realms/demo/clients/" + clients[0]["id"] + "/roles/"
    expected_roles = {
        "order:read": ("Read all demo orders", set()),
        "order:write": ("Create demo orders", set()),
        "user:read": ("Read the demo user directory", set()),
        "READER": ("Read-only access", {"order:read"}),
        "MANAGER": ("Manage orders", {"order:read", "order:write"}),
        "ADMIN": ("Administer the demo", {"order:read", "order:write", "user:read"}),
    }
    for name, (description, members) in expected_roles.items():
        url = role_url + quote(name, safe="")
        status, role = request("GET", url, token)
        assert status == 200, f"Role {name} is missing"
        assert role["description"] == description, f"Role {name}: description drift"
        assert role["attributes"]["managed-by"] == ["orders-api"], f"Role {name}: wrong owner"
        status, composites = request("GET", url + "/composites", token)
        assert status == 200
        assert {member["name"] for member in composites} == members, f"Role {name}: composite drift"
        print(f"PASS synchronized role {name}")


if __name__ == "__main__":
    try:
        wait_until_ready()
        check_access()
        check_synchronized_roles()
        print("All real Keycloak / API smoke checks passed.")
    except (AssertionError, OSError, KeyError) as error:
        print(f"FAIL: {error}", file=sys.stderr)
        sys.exit(1)
