# Spring Boot + Thymeleaf + Keycloak: UI, регистрация и роли из Java

Рабочий пример структуры приложения: Java 21, Spring Boot 4.1.2 / Spring Security 7.1,
Keycloak 26.8.2. Версии закреплены, зависимостями Spring управляет Boot BOM.

UI реализован на Spring MVC + Thymeleaf. При открытии `/` анонимный пользователь
перенаправляется на форму входа Keycloak (Sign in). После регистрации или входа он возвращается
на основной экран. В меню всегда видны обзор, заказы, пользователи, аудит и профиль;
права проверяются на сервере, а недоступные разделы показывают страницу с объяснением.

Роли принадлежат клиенту `orders-api`. Источник описаний и состава ролей — Java-код.
При старте Spring Boot приложение обращается к Keycloak Admin REST API через отдельную
сервисную учётную запись и приводит роли к описанному состоянию.

У клиента `roles-sync` подключён встроенный default client scope `kc-admin-api`.
Он обязателен для обращения к Admin REST API в Keycloak 26.8.2: добавляет нужные
audience и scope в сервисный токен. При подключении к существующему realm назначь
его через `Clients` → `roles-sync` → `Client scopes` → `Add client scope` →
`kc-admin-api` → `Default`. См. [Keycloak migration guide](https://www.keycloak.org/docs/26.8.2/upgrading/).

## Быстрый запуск

Нужны Docker с Compose. Для проверочного скрипта нужен Python 3.

```bash
docker compose up --build -d
docker compose logs -f application
```

При чистом запуске Keycloak автоматически импортирует `keycloak/demo-realm.json`:
пользователей, UI-клиент, оператора `user-operator` и его ограниченные административные
права. Запускать `configure-*.py` вручную не требуется.

После сообщения `Synchronized 6 client roles for orders-api`:

```bash
python3 scripts/smoke-test.py
```

Если порт 8080 занят, можно выбрать другой порт API:

```bash
API_PORT=8280 docker compose up --build -d
API_URL=http://localhost:8280 python3 scripts/smoke-test.py
```

Скрипт проверяет реальные токены Keycloak, матрицу доступа, а также описания,
владельца и состав всех шести ролей через Admin REST API. Токены не выводятся.

- UI: http://localhost:8080/ (или http://localhost:8280/ при `API_PORT=8280`)
- Публичный REST endpoint: http://localhost:8080/api/public
- Keycloak Admin Console: http://localhost:8180/admin/
- Администратор Keycloak: `admin` / `admin-local-only`, realm `demo`.

Compose содержит локальные демонстрационные пароли и `start-dev`; порты опубликованы
только на loopback. Для реального окружения нужны собственные секреты, HTTPS,
Keycloak в production-режиме и внешняя БД. Пример не подключается к существующему Keycloak сам.

## Пользователи и доступ

| Пользователь | Пароль | Клиентская роль | Разрешения |
|---|---|---|---|
| `reader` | `reader-local-only` | `READER` | `order:read` |
| `manager` | `manager-local-only` | `MANAGER` | `order:read`, `order:write` |
| `administrator` | `administrator-local-only` | `ADMIN` | `order:read`, `order:write`, `user:read` |

| Метод и URL | Доступ |
|---|---|
| `GET /api/public` | Всем |
| `GET /api/profile` | Любому пользователю с валидным токеном для `orders-api` |
| `GET /api/orders` | `order:read` |
| `POST /api/orders` | `order:write` |
| `GET /api/users` | `user:read` |
| `GET /api/admin/audit` | Роль `ADMIN` |

`/api/users` возвращает статический демонстрационный список, не читает пользователей
через Keycloak Admin API. `/api/admin/audit` демонстрирует проверку роли, это не журнал аудита.
Заказы хранятся в памяти: до 100 последних записей на экземпляр, при перезапуске теряются.
Доступ к заказам общий для приложения; принадлежность владельцу/организации здесь не моделируется.

## Получить токен и вызвать API

```bash
TOKEN=$(curl --fail --silent --show-error \
  http://localhost:8180/realms/demo/protocol/openid-connect/token \
  --data-urlencode grant_type=password \
  --data-urlencode client_id=demo-cli \
  --data-urlencode username=manager \
  --data-urlencode password=manager-local-only \
  | python3 -c 'import json, sys; print(json.load(sys.stdin)["access_token"])')

curl --fail --silent --show-error http://localhost:8080/api/profile \
  -H "Authorization: Bearer $TOKEN"

curl --fail --silent --show-error http://localhost:8080/api/orders \
  -H "Authorization: Bearer $TOKEN"

curl --fail --silent --show-error http://localhost:8080/api/orders \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"item":"Книга"}'
```

Без токена защищённые endpoints возвращают `401`, без нужного разрешения — `403`.
Password grant включён только у локального `demo-cli` для воспроизводимых curl-проверок.
Браузерный UI использует Authorization Code + PKCE.

## UI: регистрация, вход и разделы

Открой главный URL в браузере. Spring Security начинает Authorization Code + PKCE
и открывает форму входа Keycloak. Приложение не принимает
и не хранит пользовательские пароли. OAuth2 state и OIDC nonce сохраняются штатным
механизмом Spring Security. После callback пользователь попадает на обзор.
Вход также доступен через `/oauth2/authorization/keycloak`.
Регистрацию можно открыть ссылкой Register на форме входа или через
`/oauth2/authorization/keycloak?screen=register`; во втором случае запрос содержит `prompt=create`.

Новые пользователи автоматически включаются в default group `Readers`, которой
назначена клиентская роль `READER`. Они могут просматривать заказы и профиль.
Роли `MANAGER` и `ADMIN` назначаются администратором в Keycloak.

| UI URL | Раздел | Доступ |
|---|---|---|
| `/` | Обзор со всеми разделами | Любой вошедший пользователь |
| `/ui/orders` | Таблица заказов и форма создания | Чтение: `order:read`; создание: `order:write` |
| `/ui/users` | Демонстрационная команда | `user:read` |
| `/ui/audit` | Проверка административного доступа | `ADMIN` |
| `/ui/profile` | Данные учётной записи, роли и разрешения | Любой вошедший пользователь |

Форма создания заказа недоступна читателю, но проверка прав выполняется и при POST.
Формы защищены CSRF. Выход выполняется POST-запросом с CSRF, завершает сессию
в приложении и Keycloak и возвращает пользователя к форме входа.
Секрет UI-клиента и OAuth-токены находятся на сервере; в localStorage они не записываются.

У UI и REST API две отдельные цепочки Spring Security: UI использует HttpSession,
а `/api/**` принимает только Bearer-заголовок. Cookie UI-сессии не авторизует запросы к API.
Сервисные методы с `@PreAuthorize` используются обоими интерфейсами.
Роли UI загружаются из проверенного access token при входе; после изменения ролей
нужно выйти и войти снова. Тайм-аут неактивной UI-сессии — 30 минут.

### Обновить существующий демонстрационный realm

Если Keycloak уже запускался с прежней версией JSON, импорт пропустит существующий
realm. Один раз запусти локальный скрипт, затем пересобери приложение:

```bash
API_URL=http://localhost:8280 python3 scripts/configure-ui.py
API_PORT=8280 docker compose up --build -d application
```

Скрипт включает регистрацию, создаёт/обновляет клиента `orders-ui`, настраивает
callback/logout URL и default group `Readers`. Существующие пользователи и роли
сохраняются. Повторный запуск допустим. Скрипт использует локальную bootstrap-запись
`admin` / `admin-local-only`; другие значения передаются через
`KEYCLOAK_BOOTSTRAP_ADMIN_USERNAME` и `KEYCLOAK_BOOTSTRAP_ADMIN_PASSWORD`.
Административные учётные данные не передаются в приложение.

В JSON разрешены callback URL для портов 8080 и 8280. Если выбран другой порт,
передай его в `API_URL` этому скрипту, чтобы добавить точные разрешённые адреса.
UI-клиент имеет обычный Authorization Code flow и PKCE S256, без password grant
и без административных полномочий. Его собственная аудитория проверяется при входе,
а для access token дополнительно проверяется аудитория `orders-api`.
Scope `basic` обеспечивает claim `sub` в access token. Приложение проверяет совпадение
пользователя в ID token и access token; скрипт также добавляет `basic` существующим
демонстрационным клиентам `demo-cli` и `roles-sync`.

## Где задаются роли

`security/ApplicationPermission.java` определяет разрешения, например `order:write`.
`security/ApplicationRole.java` собирает их в пользовательские роли:

```java
MANAGER("Manage orders", Set.of(
	ApplicationPermission.ORDER_READ, ApplicationPermission.ORDER_WRITE))
```

В Keycloak и разрешения, и пользовательские роли технически являются client roles.
`MANAGER` — составная роль: в неё входят роли `order:read` и `order:write`.
Keycloak раскрывает составные роли при выдаче токена. Spring получает:

```json
{
  "iss": "http://localhost:8180/realms/demo",
  "aud": ["orders-api"],
  "resource_access": {
    "orders-api": {
      "roles": ["MANAGER", "order:read", "order:write"]
    }
  }
}
```

`KeycloakAuthoritiesConverter` читает только `resource_access.orders-api.roles`.
Имена пользовательских ролей преобразуются в `ROLE_MANAGER`, разрешения сохраняются
как `order:write`. Realm roles и роли других клиентов не дают доступ к этому API.

Проверки расположены на публичных методах Spring-сервисов:

```java
@PreAuthorize("hasAuthority('order:write')")
public OrderResponse createOrder(CreateOrderRequest request) {
	return orderRepository.create(request.item());
}
```

Для проверки роли есть `hasRole('ADMIN')`. `@EnableMethodSecurity` включён в
`SecurityConfiguration`. Контроллер вызывает отдельный Spring bean, поэтому проверка
проходит через proxy. Вызов аннотированного метода через `this` не запускает новую проверку.
Сам по себе `ADMIN` не даёт обхода проверок разрешений: они приходят из состава роли.

## Как работает синхронизация

`RoleSyncStartupRunner` выполняет `KeycloakRoleSynchronizer` при каждом старте:

1. Получает service-account token через `client_credentials` клиента `roles-sync`.
2. Находит внутренний UUID клиента `orders-api` по `clientId`.
3. Создаёт отсутствующие роли, обновляет описания и атрибут `managed-by=orders-api`.
4. Сверяет состав всех управляемых ролей: удаляет лишние связи и добавляет недостающие.
5. При `KEYCLOAK_ROLE_SYNC_PRUNE=true` удаляет отсутствующие в Java роли,
   только если у них есть собственный маркер `managed-by=orders-api`.

Идентификаторы существующих ролей сохраняются. Существующие назначения этих ролей
пользователям и группам сохраняются, пока сама роль не удалена. Повторный запуск
без изменений не выполняет изменяющих запросов к ролям.

Существующая роль с совпавшим именем без `managed-by` принимается под управление.
Если маркер указывает другого владельца, синхронизация завершается ошибкой.
Другие атрибуты роли сохраняются. Для управляемой роли состав считается полностью
заданным Java-кодом: добавленные вручную связи будут удалены при следующем запуске,
включая связи с внешними ролями. Это касается и разрешений: они должны оставаться без вложенных ролей.

При ошибке Keycloak, отсутствии прав, клиента или секрета приложение завершает старт
с ошибкой. Connect timeout — 2 секунды, read timeout — 5 секунд на запрос.
Ошибки `401`/`403` не маскируются под отсутствие роли. Конфликт `409` при создании
обрабатывается повторным чтением: роль мог создать другой экземпляр той же версии.
Синхронизация состоит из нескольких запросов и не является транзакцией Keycloak;
при частичной ошибке повторный запуск доведёт конфигурацию до заданного состояния.

## Добавить роль или изменить права

Добавь разрешение в `ApplicationPermission`, если появляется новая операция;
для неё также нужна проверка в бизнес-коде. Затем добавь роль или поменяй её состав
в `ApplicationRole` и пересобери приложение:

```bash
docker compose up --build -d application
```

В Keycloak Console: `demo` → `Clients` → `orders-api` → `Roles`.
Назначить роль человеку: `Users` → пользователь → `Role mapping` → `Assign role` →
фильтр ролей клиента `orders-api`. После назначения получи новый токен.

Приложение синхронизирует определения ролей и их состав. Оно не назначает новые роли
всем пользователям и не восстанавливает вручную изменённые назначения при каждом старте.
Тестовые пользователи и начальные назначения создаются отдельным bootstrap-импортом.

Чтобы проверить исправление расхождений, измени описание `MANAGER` или убери из её
состава `order:write` в Console, выполни `docker compose restart application`,
затем `python3 scripts/smoke-test.py`. Java-описание и состав будут восстановлены.

Удаление enum-константы само по себе не удаляет роль из Keycloak, если `prune=false`.
Удаление роли с `prune=true` также удаляет её назначения. Переименование создаёт новую
роль: назначения старой роли автоматически на новую не переносятся.

## Оператор пользователей Keycloak

При первом импорте realm `demo` автоматически создаётся `user-operator` с демонстрационным паролем
`user-operator-local-only`. Вход: http://localhost:8180/admin/demo/console/.
В панели ему доступен раздел Users. Пользователь может создавать, просматривать,
изменять и удалять пользователей, задавать им пароли и назначать/снимать роли
`orders-api`: `READER`, `MANAGER`, `ADMIN`.

Настройка находится в `keycloak/demo-realm.json`: клиентская роль
`realm-management/query-users` открывает раздел Users, а Fine-Grained Admin Permissions
дают `Users: view, manage, map-roles` и `Roles: map-role` для трёх ролей приложения.
Широкие административные роли `manage-users` и `realm-admin` оператору не назначены.
Изменение realm, клиентов, назначение административных ролей Keycloak и impersonation
проверены как запрещённые в `scripts/check-user-operator.py`.

Оператор и его разрешения создаются штатным `--import-realm` и сохраняются в volume
Keycloak. Для чистого развёртывания достаточно `docker compose up --build -d`.
В JSON пользователь находится в `users`, политики и разрешения — в
`clients[clientId=admin-permissions].authorizationSettings`. Поле
`adminPermissionsClient` связывает realm с этим клиентом. Идентификаторы трёх ролей
`orders-api` зафиксированы: на них ссылаются ресурсы разрешений, поэтому изменять их
нужно согласованно с этими ссылками.

`scripts/configure-user-operator.py` оставлен только для обновления старого realm,
созданного до включения оператора в импорт. Он не участвует в запуске приложения.
`scripts/check-user-operator.py` — необязательная проверка прав с временным пользователем.

## Bootstrap Keycloak и повторные запуски

`keycloak/demo-realm.json` создаёт realm, клиентов, сервисную учётную запись,
тестовых пользователей и три роли-заготовки. Заготовки нужны, чтобы импорт мог сразу
назначить пользователям `READER`, `MANAGER`, `ADMIN`. Описания заготовок намеренно
отличаются от Java-каталога; разрешения и составные связи создаёт приложение.
JSON также включает саморегистрацию, клиента `orders-ui` и default group `Readers`.
Оператор `user-operator`, его роль `query-users` и Fine-Grained Admin Permissions
также полностью включены в импорт.

Keycloak `--import-realm` пропускает существующий realm. Поэтому изменение JSON
после первого запуска не обновляет существующую конфигурацию. Обновления ролей
выполняет именно Java-синхронизация. Данные Keycloak сохраняются в named volume.

Остановить, сохранив пользователей и роли:

```bash
docker compose down
```

Полностью пересоздать локальный демонстрационный realm с удалением его данных:

```bash
docker compose down -v
docker compose up --build -d
```

## Запуск Java без Docker для приложения

Нужны JDK 21 и Maven 3.6.3+. Keycloak можно поднять отдельно:

```bash
docker compose up -d --wait keycloak
export KEYCLOAK_SYNC_CLIENT_SECRET=demo-sync-secret-local-only
export KEYCLOAK_UI_CLIENT_SECRET=demo-ui-secret-local-only
mvn verify
mvn spring-boot:run
```

В Docker issuer остаётся `http://localhost:8180/realms/demo`, поскольку именно его
Keycloak записывает в токен. Admin API и JWKS доступны контейнеру приложения через
`http://keycloak:8080`. Эти адреса заданы отдельно; discovery не требуется.

## Настройки и развёртывание нескольких экземпляров

| Переменная | Значение по умолчанию |
|---|---|
| `API_PORT` | `8080`, порт API на хосте для Docker Compose |
| `KEYCLOAK_ISSUER_URI` | `http://localhost:8180/realms/demo` |
| `KEYCLOAK_JWK_SET_URI` | `http://localhost:8180/realms/demo/protocol/openid-connect/certs` |
| `KEYCLOAK_ADMIN_URL` | `http://localhost:8180` |
| `KEYCLOAK_REALM` | `demo` |
| `KEYCLOAK_RESOURCE_CLIENT_ID` | `orders-api` |
| `KEYCLOAK_UI_CLIENT_ID` | `orders-ui` |
| `KEYCLOAK_UI_CLIENT_SECRET` | Не задан; нужен web-приложению, задан в локальном Compose |
| `KEYCLOAK_ROLE_SYNC_ENABLED` | `true` |
| `KEYCLOAK_ROLE_SYNC_PRUNE` | `false` |
| `KEYCLOAK_SYNC_CLIENT_ID` | `roles-sync` |
| `KEYCLOAK_SYNC_CLIENT_SECRET` | Не задан; обязателен при включённой синхронизации |

Для простого приложения достаточно синхронизации при старте. Для rolling deployment
нескольких версий лучше запускать единственный migration Job до раскатки API,
используя этот же jar без web-сервера:

```bash
java -jar target/keycloak-roles-demo-1.0.0.jar --spring.main.web-application-type=none
```

Job получает секрет `roles-sync`. Обычные экземпляры API запускаются с
`KEYCLOAK_ROLE_SYNC_ENABLED=false` и без административного секрета.
Job должен завершиться успешно до переключения трафика; не запускай одновременно
писателей с разными версиями каталога. При совместной работе старой и новой версии
используй совместимые изменения, а удаление ролей выполняй после завершения перехода.

В демонстрационном realm сервисной учётной записи выдана роль
`realm-management/manage-clients`: это широкое право управления клиентами этого realm,
не глобальный `realm-admin`. Код изменяет только `orders-api`, но само полномочие
этим клиентом не ограничено. В рабочем окружении ограничь права через fine-grained
admin permissions своей версии Keycloak либо используй выделенный realm и отдельный Job.

Уже выданный JWT сохраняет старые роли до истечения срока: локальное демо выдаёт access
tokens на 120 секунд. Изменение ролей не отзывает их мгновенно.
API проверяет подпись RS256, issuer, audience, `exp` и `nbf` стандартным декодером Spring;
собственного JWT-фильтра здесь нет. CSRF отключён только для `/api/**` с Bearer
в заголовке. UI использует защищённую CSRF браузерную сессию.

## Проверки

```bash
mvn verify
python3 scripts/smoke-test.py
```

Maven-тесты не требуют Docker: проверяют REST-контракт Admin API на HTTP-заглушке,
создание/обновление ролей, состав и pruning, повторный запуск, конфликт создания,
ошибку старта, конвертацию authorities, HTTP-матрицу доступа через Spring Security
и декодирование JWT с реальной RSA-подписью, неверным issuer/audience и истёкшим сроком.
HTTP-тесты с mock `JwtDecoder` отделены от тестов реального декодирования.
Скрипт дополнительно проверяет интеграцию с настоящим Keycloak после запуска Compose.

Для проверки полного браузерного сценария нужны Python Playwright и Chromium:

```bash
python3 -m venv .browser-venv
.browser-venv/bin/pip install playwright
.browser-venv/bin/python -m playwright install chromium
API_URL=http://localhost:8280 .browser-venv/bin/python scripts/browser-smoke-test.py
```

Браузерный сценарий создаёт временного пользователя через форму регистрации Keycloak,
проверяет роль `READER`, вход/выход, создание заказа менеджером, административные
разделы и мобильную ширину. После проверки удаляет временного пользователя;
для очистки использует локальную bootstrap-запись администратора.
Скриншоты сохраняются в `artifacts/`. Путь к уже установленному Chromium можно
передать через `PLAYWRIGHT_CHROMIUM_EXECUTABLE`.

При подготовке примера успешно выполнен полный `mvn verify` (41 тест),
а также сборка Docker-образа с теми же тестами. Smoke-скрипт прошёл с настоящим
Keycloak 26.8.2 как для локального jar, так и для приложения в Docker.
Браузерный тест Chromium прошёл полный сценарий регистрации и возврата в UI,
входа/выхода, проверки прав, создания заказа и мобильной ширины 390 px.
Скриншоты: [читатель](artifacts/ui-reader-desktop.png),
[администратор](artifacts/ui-admin-desktop.png), [мобильная версия](artifacts/ui-mobile.png).

Дополнительно на живом Keycloak проверены восстановление изменённого описания
и состава `MANAGER` после перезапуска, сохранение идентификаторов ролей и назначений,
отключённый по умолчанию pruning, удаление только устаревшей управляемой роли
через migration Job, отказ старта при неверном секрете и запуск без секрета
при отключённой синхронизации. Проверочные роли удалены после проверки.
Исправленный JSON отдельно импортирован в новый временный realm: сервисный
токен сразу получил доступ к Admin API, первый migration Job создал разрешения
и составные связи, начальные назначения пользователей сохранились.
Временный realm удалён после проверки.

## Основные файлы

```text
src/main/java/com/example/keycloakroles/
  config/                         Конфигурация Security, HTTP-клиентов и properties
  security/                       Каталог ролей/разрешений и converter
  service/security/               Синхронизация и startup runner
  service/orders/                 Операции с проверкой permissions
  service/ui/                     Модель меню, профиля и доступности разделов
  controller/                     HTTP endpoints
  repository/orders/              Хранилище демонстрационных заказов
  model/dto/                      HTTP и Keycloak DTO
src/main/resources/
  templates/                      Thymeleaf: рабочее пространство и ошибка входа
  static/assets/                  Локальные CSS и иконка приложения
```

Документация:

- [Spring Boot requirements](https://docs.spring.io/spring-boot/system-requirements.html)
- [Spring Security Resource Server JWT](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html)
- [Spring Security Method Security](https://docs.spring.io/spring-security/reference/servlet/authorization/method-security.html)
- [Keycloak Admin REST API](https://www.keycloak.org/docs-api/latest/rest-api/index.html)
- [Keycloak realm import](https://www.keycloak.org/server/importExport)
- [Keycloak service accounts](https://www.keycloak.org/docs/latest/server_admin/index.html#_service_accounts)
