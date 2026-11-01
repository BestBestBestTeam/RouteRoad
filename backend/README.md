# RouteRoad Backend

Минимальный Spring Boot-проект. Требуется JDK 25 LTS; `JAVA_HOME` должен указывать на него.
Maven скачивается автоматически через Maven Wrapper при первом запуске
(необходим доступ к Maven Central).

Команды из корня репозитория:

```sh
cd backend
./mvnw clean verify
./mvnw spring-boot:run
```

В Windows используйте `mvnw.cmd` вместо `./mvnw`.

Приложение запускается на порту `8080`. Прикладных HTTP-эндпоинтов пока нет,
поэтому запрос к `/` возвращает `404`.

Исполняемый JAR после сборки:

```sh
java -jar target/routeroad-backend-0.0.1-SNAPSHOT.jar
```

Подключение БД и организация пакетов по фичам выполняются на следующих этапах.
