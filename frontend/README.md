# RouteRoad · frontend

Интерфейс диспетчера и выездного представителя: React, TypeScript, Vite,
Tailwind CSS, Zustand, React Router, Leaflet и Lucide.

Сейчас работает демо на синтетических данных. Заявки загружаются по кнопке,
маршруты строятся отдельным действием; изменения исчезают при перезагрузке.
Java API, настоящая авторизация и офлайн-PWA пока не подключены.
Подложке карты нужен интернет; линии демомаршрутов соединяют точки напрямую.

## Разработка

Нужны Node.js 24.x и npm. Из папки `frontend`:

```sh
npm ci
npm run dev
```

Открыть **http://localhost:5173**

**Ctrl+Alt+клик** по элементу открывает исходник в редакторе через Code Inspector.
При необходимости задайте `CODE_EDITOR=code` или `CODE_EDITOR=cursor`
в `.env.local`. Плагин работает только при локальной разработке.

### Проверка UI без бэкенда

`npm run dev` уже использует асинхронный demo API в памяти. Для медленных ответов
и проверки ошибки/повтора добавьте в личный `.env.local` и перезапустите Vite:

```dotenv
VITE_DEMO_LATENCY_MS=1000
VITE_DEMO_FAIL_ONCE=loadRequests,listRequests,getVisitDetails
```

Первая попытка каждой указанной операции завершится ошибкой, следующая пройдёт.
Проверьте загрузку дня, вкладку заявок и открытие карточки; кнопки повтора
работают с тем же источником. «Очистить день» заново включает первые ошибки.
Другие имена операций — в `.env.example`. В production эти настройки не действуют.
DevTools Offline/Throttling не замедляет Promise-методы демо; сетевые ошибки
Java и офлайн-PWA проверяются отдельно после появления HTTP-клиента.

## Docker

Общий [compose.yaml](../compose.yaml) находится в корне `RouteRoad`.
Из этой папки:

```sh
docker compose up --build -d
docker compose down
```

Фронтенд доступен на **http://localhost:8080**. Dockerfile собирает `dist`,
Nginx раздаёт готовые файлы. Для разработки используется локальный Vite.
После изменения исходников контейнер нужно пересобрать.

## Проверки

Из папки `frontend`:

```sh
npm run typecheck
npm run lint
npm run format:check
npm run build
```

`npm run format` применяет форматирование, `npm run preview` показывает сборку.

## Документация

- [Требования к MVP](docs/mvp-requirements.md)
- [Архитектура и структура проекта](docs/frontend-architecture.md)
- [Единый контракт для Java: сущности и HTTP API](docs/frontend-api.md)
- [Внутренний контракт Java/Python](docs/entity.md)
- [Требования](docs/vision.md) и [решения](docs/decisions.md)
