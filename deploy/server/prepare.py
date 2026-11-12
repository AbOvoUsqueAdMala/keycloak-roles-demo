#!/usr/bin/env python3
"""Prepare private bootstrap files; never change a running Keycloak database."""

import argparse
import json
import os
from pathlib import Path
import secrets


def prepare():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--credentials', type=Path, help='Reuse an existing credentials.json without rotating passwords')
    parser.add_argument('--image', default='keycloak-roles-demo-application:server')
    args = parser.parse_args()
    if not args.image or any(character.isspace() for character in args.image):
        parser.error('--image must be a Docker image reference without whitespace')
    os.umask(0o077)
    root = Path(__file__).resolve().parents[2]
    private = Path(__file__).resolve().parent / 'private'
    private.mkdir(mode=0o700, exist_ok=True)
    os.chmod(private, 0o700)
    credentials_path = args.credentials or private / 'credentials.json'
    names = ['admin', 'reader', 'manager', 'administrator', 'user-operator', 'ui-client-secret']
    if args.credentials or credentials_path.exists():
        credentials = json.loads(credentials_path.read_text(encoding='utf-8'))
    else:
        credentials = {name: secrets.token_urlsafe(24) for name in names}
    for name in names:
        value = credentials.get(name)
        if not isinstance(value, str) or not value or any(c not in 'abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_-' for c in value):
            raise ValueError('Expected a nonempty URL-safe generated secret for: ' + name)
    api = 'https://dark-soft-chocolate.chickenkiller.com:8081'
    kc = 'https://dark-soft-chocolate.chickenkiller.com:8180'
    realm = json.loads((root / 'keycloak/demo-realm.json').read_text(encoding='utf-8'))
    for user in realm['users']:
        user['credentials'] = [{'type': 'password', 'value': credentials[user['username']], 'temporary': False}]
    ui = next(client for client in realm['clients'] if client['clientId'] == 'orders-ui')
    ui.update(secret=credentials['ui-client-secret'], redirectUris=[api + '/login/oauth2/code/keycloak'],
              webOrigins=[api], rootUrl=api, baseUrl=api + '/')
    ui['attributes']['post.logout.redirect.uris'] = api + '/'
    files = {
        'credentials.json': json.dumps(credentials, indent=2) + '\n',
        'demo-realm.json': json.dumps(realm, indent=2) + '\n',
        'server.env': ('APPLICATION_IMAGE=' + args.image + '\nKC_ADMIN_PASSWORD=' + credentials['admin']
                       + '\nUI_CLIENT_SECRET=' + credentials['ui-client-secret'] + '\n'),
    }
    lines = ['# Server access', '', 'UI: ' + api + '/', '', '| User | Password |', '|---|---|']
    lines += [f'| `{name}` | `{credentials[name]}` |' for name in ['reader', 'manager', 'administrator']]
    lines += ['', 'Keycloak operator: ' + kc + '/admin/demo/console/',
              'Keycloak administrator: ' + kc + '/admin/master/console/', '', '| User | Password |', '|---|---|']
    lines += [f'| `{name}` | `{credentials[name]}` |' for name in ['user-operator', 'admin']]
    files['server-access.md'] = '\n'.join(lines) + '\n'
    for name, content in files.items():
        target = private / name
        target.write_text(content, encoding='utf-8')
        # Keycloak runs as a non-root UID and must read the mounted import file.
        # The containing private directory remains accessible only to the owner.
        os.chmod(target, 0o644 if name == 'demo-realm.json' else 0o600)
    print('Private deployment files prepared in ' + str(private))
    print('Existing Keycloak users and passwords are not modified by this command.')


if __name__ == '__main__':
    prepare()
