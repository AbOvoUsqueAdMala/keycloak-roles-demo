# Серверное развёртывание

Конфигурация для `root@45.83.27.166`:

| Сервис | Публичный HTTPS-адрес | Docker-порт на сервере |
|---|---|---|
| Приложение | https://dark-soft-chocolate.chickenkiller.com:8081/ | `127.0.0.1:18181` |
| Keycloak | https://dark-soft-chocolate.chickenkiller.com:8180/admin/demo/console/ | `127.0.0.1:18180` |

Nginx завершает TLS и передаёт доверенные forwarded-заголовки. Приложение учитывает
их при OAuth-переходах и использует Secure session cookie. Docker-порты доступны
только с сервера. Сертификат и ключ уже находятся в
`/etc/letsencrypt/live/dark-soft-chocolate.chickenkiller.com/`; в Git их нет.
Порт `8080` занят другим сервисом, поэтому для UI выбран `8081`.

## Файлы

- `compose.server.yml` в корне — оба контейнера, автозапуск, лимиты памяти,
  внутренние порты, внешний issuer и существующий том Keycloak.
- `deploy/server/nginx.conf` — два HTTPS-входа; соответствует установленному Nginx-конфигу.
- `server.env.example` — имена переменных без секретных значений.
- `prepare.py` — создаёт приватные файлы для импорта realm при чистом запуске.

Keycloak сохраняет текущий режим демо: `start-dev` и H2 в постоянном Docker volume.
Пароли, секрет OAuth-клиента и сгенерированный realm находятся вне Git и Docker build context.
Исходный `keycloak/demo-realm.json` содержит только настройки и пароли локального демо;
генератор заменяет все пользовательские пароли и секрет `orders-ui` перед серверным импортом.

## Чистое развёртывание

Требуются Linux, Python 3, Docker Compose, Nginx, настроенный DNS и действующий
сертификат по указанному пути. Команды выполняются из корня checkout под root:

```bash
python3 deploy/server/prepare.py
docker volume create keycloak-roles-demo_keycloak-data
docker compose --env-file deploy/server/private/server.env -f compose.server.yml up --build -d

install -m 644 deploy/server/nginx.conf /etc/nginx/sites-available/keycloak-roles-demo
ln -sfn /etc/nginx/sites-available/keycloak-roles-demo /etc/nginx/sites-enabled/keycloak-roles-demo
nginx -t && systemctl reload nginx
ufw allow 8081/tcp comment 'Keycloak demo UI HTTPS'
ufw allow 8180/tcp comment 'Keycloak admin HTTPS'
```

Генератор один раз создаёт случайные секреты. Повторный запуск использует сохранённые
`deploy/server/private/credentials.json`. Он создаёт `server.env`, серверный
`demo-realm.json` и `server-access.md` в той же исключённой из Git директории.
Не удаляйте `private/` при обновлении: там находятся действующие секреты.
Пароли не выводятся в терминал. При первом запуске Keycloak импортирует роли,
пользователей и ограниченного оператора автоматически.

Том объявлен `external`, поэтому Compose не удаляет его даже через `down -v`.
Импорт пропускает существующий realm: генерация файлов не меняет пароли в работающей базе.
Не используйте новые случайные секреты поверх существующего тома — пароль клиента
в файле окружения должен совпадать с его секретом в Keycloak.

## Уже работающий сервер

Развёрнутый код: `518ff13`. Текущий каталог:
`/root/workspace/keycloak-roles-demo-deploy/current`, ссылка на `releases/518ff13`.
Секреты хранятся в `shared/credentials.json` и `shared/server-access.md` под тем же
корневым каталогом; доступ к ним есть только у root.
Действующий release использует `.env`, `keycloak/demo-realm.server.json` и образ
`keycloak-roles-demo-application:518ff13`. Смена расположения файлов в Git не требует
перезапуска этого release.

Чтобы подготовить новый checkout с теми же секретами, выполните в нём:

```bash
python3 deploy/server/prepare.py \
  --credentials /root/workspace/keycloak-roles-demo-deploy/shared/credentials.json \
  --image keycloak-roles-demo-application:server
```

Перед обновлением сохраните резервную копию остановленного тома. Затем используйте
команду `docker compose ... up --build -d` из раздела выше. Сохраняются имя Compose-проекта
`keycloak-roles-demo`, volume и пользователи. Скрипты миграции существующего realm
запускаются отдельно только при изменении его схемы/настроек; публикация конфигурации
не запускает миграцию и не меняет пароли.

Резервные копии перед первоначальным обновлением сохранены на сервере в
`/root/workspace/keycloak-roles-demo-deploy/backups/`.

## Проверка и обслуживание

Для нового checkout:

```bash
docker compose --env-file deploy/server/private/server.env -f compose.server.yml config --quiet
docker compose --env-file deploy/server/private/server.env -f compose.server.yml ps
curl -fsS https://dark-soft-chocolate.chickenkiller.com:8081/api/public
curl -fsS https://dark-soft-chocolate.chickenkiller.com:8180/realms/demo/.well-known/openid-configuration
```

Для уже работающего release:

```bash
cd /root/workspace/keycloak-roles-demo-deploy/current
docker compose -f compose.server.yml ps
docker compose -f compose.server.yml logs --tail 100
```

При первоначальном деплое проверены HTTPS снаружи, вход в UI и панель оператора,
матрица API-разрешений, ограничения оператора и отказ входа со старыми паролями.
Скрипты тестирования локального демо используют локальные пароли по умолчанию;
не запускайте их на сервере без адаптации к приватным учётным данным.
