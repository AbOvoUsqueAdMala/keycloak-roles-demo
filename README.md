# Spring Boot + Keycloak: роли в realm, разрешения в приложении

Java 21, Spring Boot 4.1.2 / Spring Security 7.1, Keycloak 26.8.2, Spring MVC + Thymeleaf.

Keycloak хранит пользователей, роли, назначения и состав разрешений каждой роли.
Приложение знает только `order:read`, `order:write`, `user:read`, `audit:read`.
Оно проверяет разрешения из подписанного access token и не обращается к Keycloak
Admin REST API. Синхронизации ролей при старте и административного service account
у приложения нет.

## Запуск

```bash
docker compose up --build -d
docker compose logs -f application
```

При чистом запуске Keycloak автоматически импортирует `keycloak/demo-realm.json`:
клиентов, пользователей, составные роли, разрешения, default group `Readers` и
оператора пользователей. Настроечные скрипты запускать не нужно.

- Приложение: http://localhost:8080/.
- Keycloak: http://localhost:8180/admin/ — `admin` / `admin-local-only`.
- Панель оператора: http://localhost:8180/admin/demo/console/.

Главная страница без сессии открывает Sign in. Ссылка Register открывает регистрацию;
новый пользователь входит в группу `Readers` и получает `order:read` через роль `READER`.
В интерфейсе приложения отображаются разрешения, а не названия ролей.

Если 8080 занят, в PowerShell задай `$env:API_PORT = '8280'` перед запуском Compose.
Callback/logout URL для 8080 и 8280 уже включены в realm. Для другого порта необходимо
добавить соответствующие адреса клиенту `orders-ui` в Keycloak.

Порты опубликованы только на loopback. Пароли и секреты демонстрационные, Keycloak
работает в `start-dev`. Это конфигурация для локального просмотра.

## Пользователи и роли

| Пользователь | Пароль | Роль `orders-api` | Разрешения |
|---|---|---|---|
| `reader` | `reader-local-only` | `READER` | `order:read` |
| `manager` | `manager-local-only` | `MANAGER` | `order:read`, `order:write` |
| `administrator` | `administrator-local-only` | `ADMIN` | `order:read`, `order:write`, `user:read`, `audit:read` |

Роли и разрешения технически представлены client roles клиента `orders-api` внутри
realm `demo`. `READER`, `MANAGER`, `ADMIN` — составные роли. Их определения полностью
заданы в `keycloak/demo-realm.json`, секция `roles.client.orders-api`:

```json
{
  "name": "MANAGER",
  "composite": true,
  "composites": {
    "client": {"orders-api": ["order:read", "order:write"]}
  }
}
```

Keycloak раскрывает составные роли в access token:

```json
{
  "resource_access": {
    "orders-api": {"roles": ["MANAGER", "order:read", "order:write"]}
  }
}
```

`KeycloakAuthoritiesConverter` принимает только известные коды из `ApplicationPermission`.
Названия бизнес-ролей, неизвестные разрешения, realm roles и роли других клиентов
игнорируются. Имя `ADMIN` без `audit:read` не открывает аудит.
`/api/profile` возвращает только прикладные разрешения в поле `authorities`.

Состав роли меняется в Keycloak: Clients → orders-api → Roles → нужная роль →
Associated roles. Можно создать новую составную роль и включить в неё существующие
разрешения без изменения Java-кода и пересборки приложения. Для повторяемого чистого
развёртывания внеси такое же изменение в JSON realm.

После изменения прав получи новый access token. Для UI выйди и войди снова:
разрешения браузерной сессии фиксируются при входе. Новый тип операции требует
добавления разрешения в `ApplicationPermission`, проверки этого разрешения у операции
и соответствующей client role в realm.

## API и интерфейс

| Метод и URL | Необходимое разрешение |
|---|---|
| `GET /api/public` | Доступен без входа |
| `GET /api/profile` | Валидный токен для `orders-api` |
| `GET /api/orders` | `order:read` |
| `POST /api/orders` | `order:write` |
| `GET /api/users` | `user:read` |
| `GET /api/admin/audit` | `audit:read` |

UI использует те же защищённые сервисы: `/ui/orders`, `/ui/users`, `/ui/audit`,
`/ui/profile`. Серверная проверка аудита — `@PreAuthorize("hasAuthority('audit:read')")`.

UI использует Authorization Code + PKCE и HttpSession, формы защищены CSRF.
API принимает только заголовок `Authorization: Bearer`; UI-cookie не авторизует API.
JWT проверяется по подписи, сроку действия, issuer и audience. Для UI дополнительно
сверяются пользователи из ID token и access token. Токены и секрет OAuth-клиента
остаются на сервере, в localStorage они не записываются.

Password grant включён у демонстрационного `demo-cli` для проверок API.
Без токена API отвечает `401`, без разрешения — `403`.
Заказы хранятся в памяти, до 100 последних записей, и теряются после перезапуска.
Список `/api/users` статический; аудит демонстрирует проверку доступа, не хранение событий.

## Оператор пользователей Keycloak

`user-operator` / `user-operator-local-only` входит по адресу
http://localhost:8180/admin/demo/console/ и видит раздел Users.
Он может создавать, изменять и удалять пользователей, задавать пароли и назначать
только `READER`, `MANAGER`, `ADMIN` клиента `orders-api`.

Начальный импорт задаёт клиентскую роль `realm-management/query-users`, разрешения
`Users: view, manage, map-roles` и `Roles: map-role` для трёх составных ролей приложения.
Изменение realm и клиентов, назначение административных ролей Keycloak и impersonation
оператору запрещены. Роль `ADMIN` приложения не является ролью администратора Keycloak.

Политики находятся в `clients[clientId=admin-permissions].authorizationSettings`.
Идентификаторы трёх составных ролей зафиксированы, поскольку на них ссылаются политики;
изменять идентификаторы нужно вместе со ссылками.

## Существующие данные и обновление

`--import-realm` пропускает существующий realm. Изменение JSON применяется автоматически
только к чистой базе Keycloak. Пользователи и настройки сохраняются в volume.

Для перехода со старой версии, где приложение синхронизировало роли, предусмотрена
однократная миграция (Python 3, доступ к локальному Keycloak):

```bash
docker compose stop application
python3 scripts/migrate-to-realm-roles.py
docker compose up --build -d application
```

Миграция берёт роли из JSON, обновляет их состав, сохраняет ID ролей и назначения
пользователей, удаляет устаревший клиент `roles-sync`. Для чистого развёртывания она
не нужна. `configure-ui.py` и `configure-user-operator.py` оставлены для старых realm;
в обычном запуске они не участвуют.

Остановка с сохранением данных: `docker compose down`.
`docker compose down -v` удаляет данные Keycloak; следующий запуск импортирует realm заново.

## Проверки

Dockerfile выполняет `mvn -B verify` перед упаковкой приложения. Тесты проверяют
JWT, фильтрацию разрешений, доступ к API и UI, CSRF и сервисные проверки.

На запущенном демо, при наличии Python 3:

```bash
python3 scripts/smoke-test.py
python3 scripts/check-user-operator.py
python3 scripts/check-permission-roles.py
```

Первый скрипт проверяет матрицу доступа и соответствие ролей JSON. Второй проверяет
оператора на временном пользователе. Третий создаёт временную составную роль,
меняет её разрешения и проверяет изменение доступа без перезапуска приложения.
Временные роли и пользователи удаляются. Smoke-тест добавляет демонстрационные заказы.

С Playwright и Chromium можно выполнить `scripts/browser-smoke-test.py`:
Sign in, регистрация, default `order:read`, вход пользователей, создание заказа,
страницы отказа, профиль, выход и мобильная ширина. Настройки URL: `API_URL`, `KEYCLOAK_URL`.
Административные данные для проверок: `KEYCLOAK_BOOTSTRAP_ADMIN_USERNAME` и
`KEYCLOAK_BOOTSTRAP_ADMIN_PASSWORD`; в приложение они не передаются.

## Настройки приложения

Серверная конфигурация Docker Compose, Nginx и порядок развёртывания по HTTPS:
[deploy/server/README.md](deploy/server/README.md). Серверные секреты генерируются
отдельно и исключены из Git.

| Переменная | По умолчанию |
|---|---|
| `KEYCLOAK_ISSUER_URI` | `http://localhost:8180/realms/demo` |
| `KEYCLOAK_JWK_SET_URI` | `http://localhost:8180/realms/demo/protocol/openid-connect/certs` |
| `KEYCLOAK_INTERNAL_URL` | `http://localhost:8180` |
| `KEYCLOAK_REALM` | `demo` |
| `KEYCLOAK_RESOURCE_CLIENT_ID` | `orders-api` |
| `KEYCLOAK_UI_CLIENT_ID` | `orders-ui` |
| `KEYCLOAK_UI_CLIENT_SECRET` | Обязателен для браузерного входа |

`KEYCLOAK_INTERNAL_URL` используется для OAuth-запросов внутри Docker и заменяет
прежнюю переменную `KEYCLOAK_ADMIN_URL`. Внешний issuer должен совпадать с URL Keycloak,
доступным браузеру. Секрет `orders-ui` — секрет OAuth-клиента без административных прав;
административный секрет синхронизации больше не используется.
