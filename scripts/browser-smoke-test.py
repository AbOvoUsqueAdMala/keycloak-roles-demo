#!/usr/bin/env python3
"""Exercise real Keycloak registration, OIDC login, UI permissions and CSRF with Chromium."""

import os
from pathlib import Path
import runpy
import uuid
from playwright.sync_api import sync_playwright, expect

PROJECT = Path(__file__).resolve().parents[1]
HTTP = runpy.run_path(str(PROJECT / "scripts/smoke-test.py"))
request = HTTP["request"]
API_URL = HTTP["API_URL"]
KEYCLOAK_URL = HTTP["KEYCLOAK_URL"]
USERNAME = "ui-check-" + uuid.uuid4().hex[:12]
PASSWORD = "Ui-check-pass-" + uuid.uuid4().hex[:16]
ARTIFACTS = PROJECT / "artifacts"


def login(page, username):
    page.goto(API_URL + "/oauth2/authorization/keycloak")
    page.locator("#username").fill(username)
    page.locator("#password").fill(username + "-local-only")
    page.locator("#kc-login").click()
    expect(page).to_have_url(API_URL + "/", timeout=30000)


def logout(page):
    page.get_by_role("button", name="Выйти", exact=True).click()
    expect(page.locator("#kc-form-login")).to_be_visible(timeout=30000)


def cleanup_user():
    status, result = request("POST", KEYCLOAK_URL + "/realms/master/protocol/openid-connect/token", form={
        "grant_type": "password", "client_id": "admin-cli",
        "username": os.environ.get("KEYCLOAK_BOOTSTRAP_ADMIN_USERNAME", "admin"),
        "password": os.environ.get("KEYCLOAK_BOOTSTRAP_ADMIN_PASSWORD", "admin-local-only"),
    })
    assert status == 200, "Cannot obtain local admin token to clean the test user"
    token = result["access_token"]
    users_url = KEYCLOAK_URL + "/admin/realms/demo/users?username=" + USERNAME + "&exact=true"
    status, users = request("GET", users_url, token)
    assert status == 200
    for user in users:
        assert user["username"] == USERNAME
        status, _ = request("DELETE", KEYCLOAK_URL + "/admin/realms/demo/users/" + user["id"], token)
        assert status == 204


def check_registration(page, context):
    page.goto(API_URL + "/")
    expect(page.locator("#kc-form-login")).to_be_visible()
    assert "prompt=create" not in page.url
    page.goto(API_URL + "/oauth2/authorization/keycloak?screen=register")
    expect(page.locator("#kc-register-form")).to_be_visible()
    assert "prompt=create" in page.url
    page.locator("#username").fill(USERNAME)
    page.locator("#email").fill(USERNAME + "@example.test")
    page.locator("#firstName").fill("Alex")
    page.locator("#lastName").fill("Reader")
    page.locator("#password").fill(PASSWORD)
    page.locator("#password-confirm").fill(PASSWORD)
    page.locator('#kc-register-form button[type="submit"], #kc-register-form input[type="submit"]').click()
    expect(page).to_have_url(API_URL + "/", timeout=30000)
    expect(page.locator("h1")).to_have_text("Обзор")
    assert page.locator("nav a").count() == 5
    assert page.locator(".section-card").count() == 4
    expect(page.locator(".permission-chip").first).to_have_text("order:read")
    page.screenshot(path=str(ARTIFACTS / "ui-reader-desktop.png"), full_page=True)
    page.get_by_role("navigation").get_by_role("link", name="Заказы", exact=True).click()
    expect(page.locator("#item")).to_be_disabled()
    assert page.goto(API_URL + "/ui/users").status == 403
    expect(page.locator("h2")).to_contain_text("Этот раздел пока недоступен")
    assert context.request.get(API_URL + "/api/orders").status == 401
    assert context.request.post(API_URL + "/ui/orders", form={"item": "No CSRF"}).status == 403
    print("PASS default sign-in; explicit registration → main UI; default READER; all sections; denied page; API/session separation", flush=True)


def check_manager(page):
    login(page, "manager")
    page.get_by_role("navigation").get_by_role("link", name="Заказы", exact=True).click()
    expect(page.locator("#item")).to_be_enabled()
    page.locator("#item").fill("Browser check " + USERNAME)
    page.get_by_role("button", name="Создать заказ").click()
    expect(page.get_by_role("status")).to_have_text("Заказ создан")
    expect(page.locator("tbody")).to_contain_text("Browser check " + USERNAME)
    assert page.goto(API_URL + "/ui/audit").status == 403
    print("PASS manager login; order form with CSRF; successful creation; audit denied", flush=True)


def check_administrator(page):
    login(page, "administrator")
    assert page.goto(API_URL + "/ui/users").status == 200
    expect(page.locator("tbody")).to_contain_text("reader")
    assert page.goto(API_URL + "/ui/audit").status == 200
    expect(page.locator(".confirmation-panel h2")).to_have_text("Доступ к аудиту подтверждён")
    assert page.goto(API_URL + "/ui/profile").status == 200
    expect(page.locator(".profile-card")).to_contain_text("administrator@example.test")
    page.goto(API_URL + "/")
    page.screenshot(path=str(ARTIFACTS / "ui-admin-desktop.png"), full_page=True)
    page.set_viewport_size({"width": 390, "height": 844})
    assert page.evaluate("document.documentElement.scrollWidth <= window.innerWidth")
    page.screenshot(path=str(ARTIFACTS / "ui-mobile.png"), full_page=True)
    print("PASS administrator sections; profile; mobile layout without horizontal overflow", flush=True)


def main():
    ARTIFACTS.mkdir(exist_ok=True)
    launch_options = {"headless": True, "args": ["--no-sandbox"]}
    if os.environ.get("PLAYWRIGHT_CHROMIUM_EXECUTABLE"):
        launch_options["executable_path"] = os.environ["PLAYWRIGHT_CHROMIUM_EXECUTABLE"]
    with sync_playwright() as playwright:
        browser = playwright.chromium.launch(**launch_options)
        context = browser.new_context(viewport={"width": 1440, "height": 1000})
        page = context.new_page()
        page.set_default_timeout(15000)
        try:
            check_registration(page, context)
            logout(page)
            check_manager(page)
            logout(page)
            check_administrator(page)
        finally:
            browser.close()
            cleanup_user()
    print("All browser checks passed; temporary registered user removed.")


if __name__ == "__main__":
    main()
