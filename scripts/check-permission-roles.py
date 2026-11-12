#!/usr/bin/env python3
"""Prove that new roles and changed composites work without changing the application."""

import os
from pathlib import Path
import runpy
import uuid
from urllib.parse import urlencode

HELPERS = runpy.run_path(str(Path(__file__).with_name("configure-user-operator.py")))
request = HELPERS["request"]
REALM_PATH = HELPERS["REALM_PATH"]
HTTP = runpy.run_path(str(Path(__file__).with_name("smoke-test.py")))


def verify_user(username, password, expected_permission, expected_statuses):
    token = request("POST", "/realms/" + HELPERS["REALM"] + "/protocol/openid-connect/token", form={
        "grant_type": "password", "client_id": "demo-cli", "username": username, "password": password,
    })["access_token"]
    status, profile = HTTP["request"]("GET", HTTP["API_URL"] + "/api/profile", token)
    assert status == 200 and profile["authorities"] == [expected_permission], profile
    for path, expected in zip(("/api/admin/audit", "/api/orders", "/api/users"), expected_statuses):
        actual, _ = HTTP["request"]("GET", HTTP["API_URL"] + path, token)
        assert actual == expected, (path, actual, expected)
    print("PASS custom realm role produces only " + expected_permission + "; API access follows permission")


def check():
    token = request("POST", "/realms/master/protocol/openid-connect/token", form={
        "grant_type": "password", "client_id": "admin-cli",
        "username": os.environ.get("KEYCLOAK_BOOTSTRAP_ADMIN_USERNAME", "admin"),
        "password": os.environ.get("KEYCLOAK_BOOTSTRAP_ADMIN_PASSWORD", "admin-local-only"),
    })["access_token"]
    client_identifier = HELPERS["find_client"](token, "orders-api")
    roles_path = REALM_PATH + "/clients/" + client_identifier + "/roles"
    suffix = uuid.uuid4().hex[:12]
    role_name = "custom-reviewer-" + suffix
    username = "permissions-check-" + suffix
    password = "Permission-check-" + uuid.uuid4().hex
    user_query = REALM_PATH + "/users?" + urlencode({"username": username, "exact": "true"})
    role_created = False
    try:
        request("POST", roles_path, token, body={"name": role_name})
        role_created = True
        role_path = roles_path + "/" + role_name
        custom_role = request("GET", role_path, token)
        audit_permission = request("GET", roles_path + "/audit%3Aread", token)
        request("POST", role_path + "/composites", token, body=[audit_permission])
        request("POST", REALM_PATH + "/users", token, body={
            "username": username, "enabled": True, "emailVerified": True,
            "email": username + "@example.test", "firstName": "Permission", "lastName": "Check",
            "credentials": [{"type": "password", "value": password, "temporary": False}],
        })
        user = request("GET", user_query, token)[0]
        user_path = REALM_PATH + "/users/" + user["id"]
        for group in request("GET", user_path + "/groups", token):
            request("DELETE", user_path + "/groups/" + group["id"], token)
        request("POST", user_path + "/role-mappings/clients/" + client_identifier, token, body=[custom_role])
        verify_user(username, password, "audit:read", [200, 403, 403])
        request("DELETE", role_path + "/composites", token, body=[audit_permission])
        read_permission = request("GET", roles_path + "/order%3Aread", token)
        request("POST", role_path + "/composites", token, body=[read_permission])
        verify_user(username, password, "order:read", [403, 200, 403])
    finally:
        for user in request("GET", user_query, token):
            request("DELETE", REALM_PATH + "/users/" + user["id"], token)
        if role_created:
            request("DELETE", roles_path + "/" + role_name, token)
    print("No application restart or rebuild was needed; temporary user and role removed.")


if __name__ == "__main__":
    check()
