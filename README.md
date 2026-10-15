# DevSecOps Management Portal

Projekt zarządzania DevSecOps wydzielony z projektu `DSOMgmtPortal`.
Stanowi ujednolicony projekt Gradle z dwoma modułami:
- **`backend`**: Serwis Spring Boot (Java 21) łączący logikę domenową portalu oraz moduł współdzielony (wcześniejszy `common-backend` + `dso-backend`).
- **`frontend`**: Interfejs Angular łączący komponenty interfejsu (wcześniejszy `common-gui` + `dso-gui`).

---

## Struktura projektu

```
DevSecOpsManagementPortal/
├── backend/                  # Moduł backendu w Javie (Spring Boot)
│   ├── src/main/java/        # Kod aplikacji (połączony common + dso)
│   ├── src/main/resources/   # Konfiguracja (application.yml, changelogi Liquibase)
│   ├── src/test/             # Testy jednostkowe i integracyjne
│   └── build.gradle
├── frontend/                 # Moduł frontendu w Angularze
│   ├── src/                  # Kod aplikacji Angular
│   │   ├── app/              # Główne widoki i trasy portalu
│   │   └── common/           # Współdzielone komponenty UI, style, modele
│   ├── angular.json
│   ├── package.json
│   └── build.gradle
├── gradle/                   # Konfiguracja wersji i wrapper Gradle
├── build.gradle              # Główny skrypt budowania
└── settings.gradle           # Definicja modułów (backend, frontend)
```

---

## Uruchamianie i budowanie

### Całość (Gradle):
- Budowanie projektu:
  ```bash
  ./gradlew build
  ```
- Uruchomienie backendu (zintegrowanego z frontendem):
  ```bash
  ./gradlew :backend:bootRun
  ```

### Moduł Frontend (Angular):
```bash
cd frontend
npm ci
npm start
```
Aplikacja uruchomi się pod adresem: `http://localhost:4200` (z proxy na backend pod `http://localhost:8080`).

### Moduł Backend (Spring Boot):
```bash
cd backend
../gradlew bootRun
```
Serwis nasłuchuje na porcie `8080`.
