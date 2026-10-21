# API: panel administratora (aplikacje, przedmioty, akty, rozdziały, arkusze)

[← Przegląd API](README.md) · [Spis treści](../README.md)

Endpointy panelu administratora (od Etapu 35). Wszystkie wymagają roli **ADMIN**: zwykły użytkownik dostaje `403`, a cała
ścieżka `/api/admin/**` jest dodatkowo zastrzeżona w konfiguracji zabezpieczeń. Zmiany są natychmiastowe i dotyczą wszystkich
użytkowników, więc po zmianie lista aktów, przedmiotów albo arkuszy w aplikacji odświeży się przy następnym wczytaniu.

Czego tu **nie ma**: dodawania, edycji i wczytywania **pytań** (lista, wyszukiwarka, edycja, import z CSV i JSON, kolejka
oznaczeń, obsługa zgłoszeń) to osobny etap panelu. Pytania nadal wczytuje się skryptami SQL (katalog `baza/`), a oznaczenia
pytań obsługuje [04. Pytania (administrator)](04-pytania-admin.md#oznaczenia-pytań-apiadminquestion-marks). Terminy egzaminów
ustawia się istniejącym [`PUT /api/exam-terms/{trackId}`](09-ustawienia.md#put-apiexam-termstrackid) (panel korzysta z niego,
nie ma drugiego adresu).

## Zasady wspólne

- **Ukrywanie zamiast usuwania.** Akt prawny i arkusz egzaminacyjny, który ma pytania (także ukryte), **nie może zostać
  usunięty** (`409`), można go tylko **ukryć**. Ukryty akt albo arkusz znika wszystkim użytkownikom: z puli (testy, fiszki,
  liczniki, kreator), ze statystyk źródeł, z egzaminu próbnego i z „Egzaminów MS”; pytania i historia odpowiedzi zostają, a
  przywrócenie (`hidden: false`) oddaje wszystko bez utraty postępów. Administrator widzi ukryte pozycje w panelu.
- **Adresy (`slug`)** powstają z nazwy przy dodawaniu (małe litery, bez polskich znaków, myślniki; przy zajętym adresie z
  numerem `-2`, `-3`…) i **nigdy się nie zmieniają**: używają ich skrypty SQL wczytujące pytania. Wyjątek: adres arkusza
  wyprowadzony z aplikacji i roku idzie za zmianą roku.
- **Nazwy są unikalne** w swoim zakresie, bez względu na wielkość liter, polskie znaki i nadmiarowe spacje (aplikacje i
  przedmioty: w całej bazie; akty: w dziedzinie; rozdziały: w akcie). Duplikat to `409`.
- **Puste nazwy** i nieistniejące numery to `400`, brak zasobu pod podanym numerem to `404`.
- Dodawanie zwraca `201` z utworzonym zasobem, usuwanie `204` bez ciała (z wyjątkiem rozdziałów: patrz niżej).
- Liczniki pytań w panelu obejmują także pytania ukryte (osobne pole `hiddenQuestionCount`), bo to widok administratora.

## Przegląd

### `GET /api/admin/overview`

Liczby na ekranie startowym panelu.

```json
{ "applications": 5, "courses": 12, "fields": 11, "acts": 43, "hiddenActs": 1, "sections": 37,
  "sheets": 28, "hiddenSheets": 0, "questions": 4210, "hiddenQuestions": 12, "newReports": 3 }
```

`applications` to aplikacje z egzaminem (bez ścieżki Student), `newReports` to zgłoszenia błędów w stanie `NEW`.

## Aplikacje

Aplikacja to ścieżka z egzaminem (`kind: "EXAM"`); ścieżka **Student** (`kind: "STUDENT"`) jest jedna i istnieje od początku.
Termin egzaminu ma tylko aplikacja.

### `GET /api/admin/tracks`

Wszystkie ścieżki w kolejności z bazy (`sortOrder`, potem numer).

```json
[ { "id": 1, "slug": "adwokacka-radcowska", "name": "Aplikacja adwokacka i radcowska",
    "shortName": "Adwokacka i radcowska", "kind": "EXAM", "sortOrder": 10,
    "actIds": [1, 2, 3], "sheetCount": 7, "userCount": 12, "examDate": "2027-03-14" } ]
```

`actIds` to akty aplikacji (także ukryte); dla Studenta suma aktów wszystkich przedmiotów (tylko do odczytu).
`sheetCount` liczy arkusze razem z ukrytymi, `userCount` to liczba użytkowników, którzy wybrali tę ścieżkę, `examDate` to
termin egzaminu albo `null` (nieogłoszony; Student zawsze `null`).

### `POST /api/admin/tracks`

Nowa aplikacja z egzaminem. Ciało: `{ "name": "Aplikacja testowa", "shortName": "Testowa", "sortOrder": 70 }`
(`sortOrder` opcjonalny: brak = na końcu listy; `name` do 255, `shortName` do 60 znaków). Powstaje też pusty termin egzaminu.
Zajęta nazwa albo krótka nazwa: `409`.

### `PUT /api/admin/tracks/{id}`

Zmiana nazwy, krótkiej nazwy i miejsca na liście (to samo ciało co przy dodawaniu; brak `sortOrder` zostawia obecne). Adres
się nie zmienia. Można też zmienić nazwę ścieżki Student.

### `DELETE /api/admin/tracks/{id}`

Usuwa **pustą** aplikację: bez arkuszy (nawet ukrytych) i taką, której nikt nie wybrał (inaczej `409` z liczbą). Ścieżki
Student nie da się usunąć (`400`). Razem z aplikacją znika lista jej aktów i termin egzaminu.

### `PUT /api/admin/tracks/{id}/acts`

Zastępuje listę aktów aplikacji: `{ "actIds": [1, 2, 3] }` (pusta lista zdejmuje wszystkie). Numer, którego nie ma w bazie:
`400`. Dla ścieżki Student `400`: jej akty ustawia się w przedmiotach. Pula aplikacji to pytania z jej arkuszy **lub** z tych
aktów, więc zmiana listy od razu zmienia, co widzą jej użytkownicy.

## Przedmioty Studenta

### `GET /api/admin/courses`

```json
[ { "id": 10, "slug": "prawo-karne", "name": "Prawo karne", "sortOrder": 10, "actIds": [4, 5], "userCount": 4 } ]
```

`userCount` to liczba studentów, którzy wybrali ten przedmiot.

### `POST /api/admin/courses`, `PUT /api/admin/courses/{id}`

Ciało: `{ "name": "Historia prawa", "sortOrder": 30 }` (`sortOrder` opcjonalny jak wyżej). Zajęta nazwa: `409`.

### `DELETE /api/admin/courses/{id}`

Usuwa przedmiot i zdejmuje go z wyborów studentów; akty i pytania zostają. Student, któremu po tym nie został żaden wybrany
przedmiot, wraca do puli ze wszystkich przedmiotów (tak jak przy braku wyboru).

### `PUT /api/admin/courses/{id}/acts`

Zastępuje listę aktów i zagadnień przedmiotu: `{ "actIds": [4, 5] }`. Ten sam akt może należeć do wielu przedmiotów.

## Akty prawne i zagadnienia

Akt (`kind: "ACT"`: kodeks, ustawa) albo zagadnienie (`kind: "TOPIC"`: np. prawoznawstwo, paremie łacińskie) należy do jednej
dziedziny. Dziedziny prawa są tylko do odczytu.

### `GET /api/admin/fields`

```json
[ { "id": 1, "name": "Prawo cywilne i postępowanie cywilne", "sortOrder": 10, "actCount": 6 } ]
```

### `GET /api/admin/acts`

Akty stronami (`page`, `size`: domyślnie 50, najwyżej 200), posortowane według dziedziny, kolejności i nazwy (po polsku).
Filtry (wszystkie opcjonalne): `query` (szuka w nazwie, skrócie i adresie, bez względu na wielkość liter i polskie znaki),
`fieldId`, `kind`, `hidden`, `trackId`, `courseId` oraz `unassigned=true` (tylko akty bez żadnego przypisania do aplikacji i
przedmiotów).

```json
{ "items": [ { "id": 3, "slug": "kodeks-karny", "name": "Kodeks karny", "shortName": "KK", "fieldId": 2,
               "fieldName": "Prawo karne i proces karny", "kind": "ACT", "sortOrder": 10, "hidden": false,
               "questionCount": 120, "hiddenQuestionCount": 5, "sectionCount": 37,
               "trackIds": [1], "courseIds": [10] } ],
  "page": 0, "size": 50, "totalElements": 43, "totalPages": 1 }
```

### `GET /api/admin/acts/options`

Wszystkie akty (także ukryte) w lekkiej postaci do list wyboru: `[ { "id", "name", "shortName", "fieldId", "fieldName",
"kind", "hidden" } ]`, w tej samej kolejności co lista.

### `GET /api/admin/acts/{id}`

Jeden akt w kształcie jak element listy. Brak: `404`.

### `POST /api/admin/acts`, `PUT /api/admin/acts/{id}`

```json
{ "fieldId": 2, "name": "Kodeks wykroczeń", "shortName": "KW", "kind": "ACT", "sortOrder": 40 }
```

`fieldId` i `name` (do 300 znaków) są wymagane; `shortName` (do 50 znaków) pusty albo brak oznacza akt bez skrótu; `kind`
brak = `ACT` przy dodaniu i bez zmiany przy edycji; `sortOrder` brak = na końcu dziedziny przy dodaniu i bez zmiany przy
edycji. Akt można przenieść do innej dziedziny. Ta sama nazwa w dziedzinie: `409`; nieznana dziedzina: `400`.

### `PUT /api/admin/acts/{id}/hidden`

`{ "hidden": true }` ukrywa akt, `{ "hidden": false }` go przywraca. Zwraca akt. Ukryty akt znika wszystkim użytkownikom razem
ze swoimi pytaniami (patrz zasady wspólne).

### `PUT /api/admin/acts/{id}/assignments`

Pełne przypisanie aktu: do jakich aplikacji i przedmiotów Studenta należy.

```json
{ "trackIds": [1, 2], "courseIds": [10] }
```

Zastępuje dotychczasowe przypisania (akt jest dokładnie w wybranych aplikacjach i przedmiotach, a w pozostałych nie).
Ścieżka Student w `trackIds` albo nieistniejący numer: `400`, bez żadnej zmiany.

### `DELETE /api/admin/acts/{id}`

Usuwa akt **bez pytań** (razem z jego rozdziałami i przypisaniami). Akt, który ma pytania (także ukryte): `409` z liczbą
pytań, wtedy trzeba go ukryć.

## Rozdziały aktów

Rozdział to podział aktu na działy (np. „Rozdział XIX. Przestępstwa przeciwko życiu i zdrowiu”), wybierany w kreatorze
testu. **Nazwa zawiera numer rozdziału**, dokładnie tak, jak ma się pokazać użytkownikowi. Każdy z poniższych adresów
zwraca **pełną, aktualną listę rozdziałów aktu** w kolejności wyświetlania:

```json
[ { "id": 12, "actId": 3, "name": "Rozdział I. Zasady odpowiedzialności karnej", "sortOrder": 10,
    "questionCount": 40, "hiddenQuestionCount": 1 } ]
```

### `GET /api/admin/acts/{actId}/sections`

Lista rozdziałów aktu. Brak aktu: `404`.

### `POST /api/admin/acts/{actId}/sections`

`{ "name": "Rozdział III. Wyłączenie odpowiedzialności" }` dodaje rozdział na końcu listy. Ta sama nazwa w akcie (bez względu na
wielkość liter i spacje): `409`.

### `POST /api/admin/acts/{actId}/sections/bulk`

Wiele rozdziałów naraz: `{ "names": ["Rozdział I. A", "Rozdział II. B"] }` (do 300 pozycji, nazwa do 300 znaków).
Dodawane na koniec listy w podanej kolejności; puste pozycje, nazwy, które akt już ma, i powtórki na liście są pomijane.
Gdy nic nie zostało do dodania: `400`. Służy do wklejenia całego spisu rozdziałów kodeksu jednym ruchem.

### `PUT /api/admin/acts/{actId}/sections/order`

`{ "ids": [14, 12, 13] }` ustawia kolejność: lista musi zawierać dokładnie numery wszystkich rozdziałów aktu, każdy raz
(inaczej `400`: lista jest nieaktualna).

### `PUT /api/admin/sections/{id}`

`{ "name": "Rozdział II. Formy zjawiskowe" }` zmienia nazwę rozdziału (duplikat w akcie: `409`).

### `DELETE /api/admin/sections/{id}`

Usuwa rozdział **bez pytań** i zwraca listę rozdziałów aktu. Rozdział z pytaniami (także ukrytymi): `409` z liczbą pytań,
bo zdjęcie z nich przypisania byłoby nieodwracalne. Przypisanie pytań do rozdziałów robi się skryptem SQL
(`baza/07-rozdzialy-szablon.sql`).

## Arkusze egzaminacyjne

Arkusz to egzamin jednej aplikacji z jednego roku. Nazwa i adres powstają jak w skryptach SQL: krótka nazwa aplikacji i rok
(„Notarialna 2024”, `notarialna-2024`). Źródło „Pytania autorskie” jest jedno i nie podlega zarządzaniu (pytania autorskie nie
mają arkusza).

### `GET /api/admin/sheets`

Arkusze wszystkich aplikacji (albo jednej: `?trackId=`), także ukryte, w kolejności aplikacji i od najnowszego roku.

```json
[ { "id": 7, "slug": "notarialna-2024", "name": "Notarialna 2024", "trackId": 4, "trackName": "Notarialna",
    "examYear": 2024, "hidden": false, "questionCount": 150, "hiddenQuestionCount": 2 } ]
```

### `POST /api/admin/sheets`

`{ "trackId": 4, "examYear": 2025 }` (rok od 1990 do 2100). Aplikacja ma najwyżej jeden arkusz z danego roku (także ukryty):
duplikat `409`. Ścieżka Student albo nieznana aplikacja: `400`.

### `PUT /api/admin/sheets/{id}`

`{ "examYear": 2026, "name": null }`. Pusta nazwa albo brak oznacza nazwę wyprowadzoną z aplikacji i roku; własna nazwa
(do 255 znaków) zostaje. Adres idzie za nowym rokiem tylko wtedy, gdy był wyprowadzony z aplikacji i dawnego roku. Rok zajęty
przez inny arkusz tej aplikacji: `409`. Zmiana nazwy nie zmienia podpisów starych podejść do egzaminu (są zapisane w historii
użytkowników).

### `PUT /api/admin/sheets/{id}/hidden`

`{ "hidden": true }` ukrywa arkusz, `{ "hidden": false }` go przywraca. Ukryty arkusz znika z puli, ze statystyk źródeł i z
„Egzaminów MS”; jego pytania nie wchodzą też do losowanego egzaminu.

### `DELETE /api/admin/sheets/{id}`

Usuwa arkusz **bez pytań**. Arkusz, który ma pytania (także ukryte): `409`, wtedy trzeba go ukryć.
