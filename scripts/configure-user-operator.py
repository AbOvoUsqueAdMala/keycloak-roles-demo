#!/usr/bin/env python3
"""Migrate an older demo realm; fresh deployments import the operator from realm JSON."""

import os
from pathlib import Path
import runpy
from urllib.parse import urlencode

HELPERS = runpy.run_path(str(Path(__file__).with_name("configure-ui.py")))
request = HELPERS["request"]
REALM = HELPERS["REALM"]
REALM_PATH = "/admin/realms/" + REALM
USERNAME = "user-operator"
PASSWORD = os.environ.get("KEYCLOAK_OPERATOR_PASSWORD", "user-operator-local-only")
POLICY_NAME = "demo-user-operator-policy"


def find_client(token, client_name):
    clients = request("GET", REALM_PATH + "/clients?" + urlencode({"clientId": client_name}), token)
    if len(clients) != 1:
        raise RuntimeError("Expected one client: " + client_name)
    return clients[0]["id"]


def upsert_authorization(token, base_path, kind, definition):
    collection = "/policy" if kind == "policy/user" else "/permission/scope"
    existing = request("GET", base_path + collection + "?" + urlencode({"name": definition["name"]}), token)
    matches = [item for item in existing if item["name"] == definition["name"]]
    if matches:
        identifier = matches[0]["id"]
        request("PUT", base_path + "/" + kind + "/" + identifier, token,
                body={**definition, "id": identifier})
    else:
        request("POST", base_path + "/" + kind, token, body=definition)


def configure():
    token = request("POST", "/realms/master/protocol/openid-connect/token", form={
        "grant_type": "password", "client_id": "admin-cli",
        "username": os.environ.get("KEYCLOAK_BOOTSTRAP_ADMIN_USERNAME", "admin"),
        "password": os.environ.get("KEYCLOAK_BOOTSTRAP_ADMIN_PASSWORD", "admin-local-only"),
    })["access_token"]
    users_path = REALM_PATH + "/users?" + urlencode({"username": USERNAME, "exact": "true"})
    users = request("GET", users_path, token)
    if not users:
        request("POST", REALM_PATH + "/users", token, body={
            "username": USERNAME, "enabled": True, "emailVerified": True,
            "firstName": "User", "lastName": "Operator", "email": USERNAME + "@example.test",
            "credentials": [{"type": "password", "value": PASSWORD, "temporary": False}],
        })
        users = request("GET", users_path, token)
    if len(users) != 1:
        raise RuntimeError("Expected one operator user")
    operator_path = REALM_PATH + "/users/" + users[0]["id"]
    management_identifier = find_client(token, "realm-management")
    management_roles = REALM_PATH + "/clients/" + management_identifier + "/roles/"
    mappings_path = operator_path + "/role-mappings/clients/" + management_identifier
    effective_roles = request("GET", mappings_path + "/composite", token)
    unexpected = [role["name"] for role in effective_roles if role["name"] != "query-users"]
    if unexpected:
        raise RuntimeError("Existing operator has broader admin roles; review first: " + ", ".join(unexpected))
    query_role = request("GET", management_roles + "query-users", token)
    request("POST", mappings_path, token, body=[query_role])
    request("PUT", REALM_PATH, token, body={"adminPermissionsEnabled": True})
    permissions_identifier = find_client(token, "admin-permissions")
    permissions_path = REALM_PATH + "/clients/" + permissions_identifier + "/authz/resource-server"
    upsert_authorization(token, permissions_path, "policy/user", {
        "name": POLICY_NAME, "logic": "POSITIVE", "users": [users[0]["id"]],
    })
    upsert_authorization(token, permissions_path, "permission/scope", {
        "name": "demo-user-operator-manage-users", "resourceType": "Users",
        "scopes": ["view", "manage", "map-roles"], "policies": [POLICY_NAME],
    })
    application_identifier = find_client(token, "orders-api")
    application_roles = REALM_PATH + "/clients/" + application_identifier + "/roles/"
    allowed_roles = [request("GET", application_roles + name, token)["id"]
                     for name in ("READER", "MANAGER", "ADMIN")]
    upsert_authorization(token, permissions_path, "permission/scope", {
        "name": "demo-user-operator-assign-application-roles", "resourceType": "Roles",
        "resources": allowed_roles, "scopes": ["map-role"], "policies": [POLICY_NAME],
    })
    print("Configured user-operator: Users view/manage/map-roles; assign READER, MANAGER, ADMIN.")
    print("Existing user passwords are preserved on subsequent runs.")


if __name__ == "__main__":
    configure()
