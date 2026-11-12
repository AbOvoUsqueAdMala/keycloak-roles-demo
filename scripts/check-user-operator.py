#!/usr/bin/env python3
"""Check delegated admin permissions with a disposable user in the local demo."""

import os
from pathlib import Path
import runpy
import uuid
from urllib.parse import urlencode

HELPERS = runpy.run_path(str(Path(__file__).with_name("configure-user-operator.py")))
request = HELPERS["request"]
find_client = HELPERS["find_client"]
REALM_PATH = HELPERS["REALM_PATH"]


def require_forbidden(method, path, token, body=None):
    try:
        request(method, path, token, body=body)
    except RuntimeError as error:
        if str(error).endswith("HTTP 403"):
            print("PASS denied " + method + " " + path)
            return
        raise
    raise AssertionError("Expected HTTP 403: " + method + " " + path)


def check():
    admin_token = request("POST", "/realms/master/protocol/openid-connect/token", form={
        "grant_type": "password", "client_id": "admin-cli",
        "username": os.environ.get("KEYCLOAK_BOOTSTRAP_ADMIN_USERNAME", "admin"),
        "password": os.environ.get("KEYCLOAK_BOOTSTRAP_ADMIN_PASSWORD", "admin-local-only"),
    })["access_token"]
    operator_token = request("POST", "/realms/" + HELPERS["REALM"] + "/protocol/openid-connect/token", form={
        "grant_type": "password", "client_id": "admin-cli",
        "username": HELPERS["USERNAME"], "password": HELPERS["PASSWORD"],
    })["access_token"]
    application_identifier = find_client(admin_token, "orders-api")
    management_identifier = find_client(admin_token, "realm-management")
    temporary_username = "operator-check-" + uuid.uuid4().hex[:12]
    query = REALM_PATH + "/users?" + urlencode({"username": temporary_username, "exact": "true"})
    try:
        request("POST", REALM_PATH + "/users", operator_token, body={
            "username": temporary_username, "enabled": True,
        })
        users = request("GET", query, operator_token)
        assert len(users) == 1
        user_path = REALM_PATH + "/users/" + users[0]["id"]
        request("PUT", user_path, operator_token, body={"firstName": "Operator permission check"})
        assert request("GET", user_path, operator_token)["firstName"] == "Operator permission check"
        request("PUT", user_path + "/reset-password", operator_token, body={
            "type": "password", "value": "Check-" + uuid.uuid4().hex, "temporary": True,
        })
        print("PASS create, list, view, edit user and set temporary password")
        application_mapping = user_path + "/role-mappings/clients/" + application_identifier
        for role_name in ("READER", "MANAGER", "ADMIN"):
            role = request("GET", REALM_PATH + "/clients/" + application_identifier + "/roles/" + role_name,
                           admin_token)
            request("POST", application_mapping, operator_token, body=[role])
            assigned = request("GET", application_mapping, operator_token)
            assert role_name in {item["name"] for item in assigned}
            request("DELETE", application_mapping, operator_token, body=[role])
            print("PASS assign and unassign " + role_name)
        management_mapping = user_path + "/role-mappings/clients/" + management_identifier
        for role_name in ("realm-admin", "manage-users", "query-users"):
            role = request("GET", REALM_PATH + "/clients/" + management_identifier + "/roles/" + role_name,
                           admin_token)
            require_forbidden("POST", management_mapping, operator_token, [role])
        require_forbidden("PUT", REALM_PATH, operator_token, {})
        require_forbidden("PUT", REALM_PATH + "/clients/" + application_identifier, operator_token, {})
        require_forbidden("POST", user_path + "/impersonation", operator_token)
        request("DELETE", user_path, operator_token)
        assert not request("GET", query, admin_token)
        print("PASS delete disposable user")
    finally:
        for user in request("GET", query, admin_token):
            request("DELETE", REALM_PATH + "/users/" + user["id"], admin_token)
    print("All user-operator checks passed; disposable user removed.")


if __name__ == "__main__":
    check()
