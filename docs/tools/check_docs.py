#!/usr/bin/env python3
"""Sprawdza, czy dokumentacja backendu zgadza się z kodem.

Uruchomienie (z głównego katalogu repozytorium):  python3 docs/tools/check_docs.py

Sprawdza tylko to, co da się porównać mechanicznie:
  1. endpointy: kontrolery w kodzie <-> tabela w docs/api/README.md i nagłówki w docs/api/*.md,
  2. baza: tabele i kolumny z migracji Flyway <-> opis w docs/02-baza-danych.md,
  3. odnośniki między plikami Markdown i kotwice nagłówków oraz składnia diagramów sekwencji (średnik w komunikacie),
  4. liczby: parametry FSRS, domyślne limity z application.yml, liczba migracji, endpointów i klas testowych.
Opisów słownych nie weryfikuje. Kod wyjścia: 0 = wszystko się zgadza, 1 = są rozbieżności.
Wymaga tylko Pythona 3 (bez zewnętrznych bibliotek).
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SRC = ROOT / "src" / "main" / "java"
MIGRATIONS = ROOT / "src" / "main" / "resources" / "db" / "migration"
DOCS = ROOT / "docs"
errors: list[str] = []


def fail(msg: str) -> None:
    errors.append(msg)


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


# ---------------------------------------------------------------------------------------------------------------------
# 1. Endpointy
# ---------------------------------------------------------------------------------------------------------------------
MAPPING = re.compile(r"@(Get|Post|Put|Delete|Patch|Request)Mapping(?:\(([^)]*)\))?")


def first_path(args: str | None) -> str:
    if not args:
        return ""
    m = re.search(r'"([^"]*)"', args)
    return m.group(1) if m else ""


def code_endpoints() -> set[tuple[str, str]]:
    found: set[tuple[str, str]] = set()
    for java in SRC.rglob("*Controller.java"):
        text = read(java)
        class_pos = text.find("public class")
        base = ""
        for m in MAPPING.finditer(text[:class_pos]):
            if m.group(1) == "Request":
                base = first_path(m.group(2))
        for m in MAPPING.finditer(text[class_pos:]):
            verb = m.group(1)
            if verb == "Request":
                continue
            found.add((verb.upper(), base + first_path(m.group(2))))
    return found


def check_endpoints() -> int:
    code = code_endpoints()
    # tabela w api/README.md
    table = set()
    for line in read(DOCS / "api" / "README.md").splitlines():
        m = re.match(r"\|\s*(GET|POST|PUT|DELETE|PATCH)\s*\|\s*`([^`]+)`", line)
        if m:
            table.add((m.group(1), m.group(2).split("?")[0]))
    # tabela obejmuje też endpointy spoza /api (te z sekcji "Poza /api" nie mają kolumny metody)
    for ep in sorted(code - table):
        fail(f"endpoint w kodzie, którego nie ma w docs/api/README.md: {ep[0]} {ep[1]}")
    for ep in sorted(table - code):
        fail(f"endpoint w docs/api/README.md, którego nie ma w kodzie: {ep[0]} {ep[1]}")

    # nagłówki w plikach grup: `METHOD /ścieżka` (ścieżka może mieć ?parametry)
    in_groups: set[tuple[str, str]] = set()
    for md in sorted((DOCS / "api").glob("0*.md")):
        for m in re.finditer(r"`(GET|POST|PUT|DELETE|PATCH) (/[^` ]*)`", read(md)):
            in_groups.add((m.group(1), m.group(2).split("?")[0]))
    for ep in sorted(code - in_groups):
        fail(f"endpoint w kodzie bez opisu w żadnym pliku docs/api/0*.md: {ep[0]} {ep[1]}")
    for ep in sorted(in_groups - code):
        # odnośniki do endpointów zewnętrznych (np. /actuator) pomijamy
        if ep[1].startswith("/api/"):
            fail(f"endpoint opisany w docs/api/0*.md, którego nie ma w kodzie: {ep[0]} {ep[1]}")

    stated = re.search(r"(\d+) endpoint", read(DOCS / "api" / "README.md"))
    if stated and int(stated.group(1)) != len(code):
        fail(f"docs/api/README.md podaje {stated.group(1)} endpointów, a w kodzie jest {len(code)}")
    return len(code)


# ---------------------------------------------------------------------------------------------------------------------
# 2. Baza danych
# ---------------------------------------------------------------------------------------------------------------------
def migrations() -> list[Path]:
    def num(p: Path) -> int:
        return int(re.match(r"V(\d+)__", p.name).group(1))

    return sorted(MIGRATIONS.glob("V*__*.sql"), key=num)


def strip_sql_comments(sql: str) -> str:
    return re.sub(r"--[^\n]*", "", sql)


def split_top_level(body: str) -> list[str]:
    parts, depth, cur = [], 0, []
    for ch in body:
        if ch == "(":
            depth += 1
        elif ch == ")":
            depth -= 1
        if ch == "," and depth == 0:
            parts.append("".join(cur))
            cur = []
        else:
            cur.append(ch)
    parts.append("".join(cur))
    return parts


CONSTRAINT_START = ("constraint", "primary", "foreign", "unique", "check")


def schema_from_migrations() -> tuple[dict[str, set[str]], set[str]]:
    tables: dict[str, set[str]] = {}
    names: set[str] = set()
    for mig in migrations():
        sql = strip_sql_comments(read(mig))
        names.update(re.findall(r"\b((?:chk|uq|idx|trg|fk)_\w+)", sql))
        for m in re.finditer(r"CREATE TABLE\s+(\w+)\s*\((.*?)\n?\)\s*;", sql, re.S | re.I):
            cols = set()
            for part in split_top_level(m.group(2)):
                tok = part.strip().split()
                if tok and tok[0].lower() not in CONSTRAINT_START:
                    cols.add(tok[0].strip('"'))
            tables[m.group(1)] = cols
        for m in re.finditer(r"ALTER TABLE\s+(\w+)\s+(.*?);", sql, re.S | re.I):
            table, rest = m.group(1), m.group(2)
            if table not in tables:
                continue
            for add in re.finditer(r"ADD COLUMN\s+(\w+)", rest, re.I):
                tables[table].add(add.group(1))
            for drop in re.finditer(r"DROP COLUMN\s+(\w+)", rest, re.I):
                tables[table].discard(drop.group(1))
    return tables, names


def sections_by_heading(text: str) -> list[tuple[str, str]]:
    """Dzieli Markdown na (nagłówek, treść do następnego nagłówka ## lub ###)."""
    out, head, buf = [], "", []
    for line in text.splitlines():
        if re.match(r"#{2,3} ", line):
            out.append((head, "\n".join(buf)))
            head, buf = line, []
        else:
            buf.append(line)
    out.append((head, "\n".join(buf)))
    return out


def check_database() -> tuple[int, int]:
    tables, names = schema_from_migrations()
    doc = read(DOCS / "02-baza-danych.md")
    sections = sections_by_heading(doc)
    for table, cols in sorted(tables.items()):
        owners = [body + head for head, body in sections if f"`{table}`" in head]
        if not owners:
            fail(f"tabela {table} z migracji nie ma własnego nagłówka w docs/02-baza-danych.md")
            continue
        text = "\n".join(owners)
        for col in sorted(cols):
            if f"`{col}`" not in text:
                fail(f"kolumna {table}.{col} z migracji nie jest opisana w sekcji tabeli w docs/02-baza-danych.md")
    for name in sorted(set(re.findall(r"`((?:chk|uq|idx|trg|fk)_\w+)`", doc))):
        if name not in names:
            fail(f"docs/02-baza-danych.md wspomina `{name}`, a w migracjach nie ma takiego obiektu")
    # liczba migracji
    nums = [int(re.match(r"V(\d+)__", p.name).group(1)) for p in migrations()]
    if nums != list(range(1, len(nums) + 1)):
        fail(f"numeracja migracji ma dziury: {nums}")
    last = f"V{max(nums)}"
    for md in ("README.md", "01-architektura.md", "02-baza-danych.md", "05-konfiguracja-i-uruchomienie.md"):
        if last not in read(DOCS / md):
            fail(f"docs/{md} nie wspomina ostatniej migracji {last}")
    return len(tables), len(nums)


# ---------------------------------------------------------------------------------------------------------------------
# 3. Odnośniki i kotwice
# ---------------------------------------------------------------------------------------------------------------------
def slug(heading: str) -> str:
    text = heading.lstrip("#").strip().lower()
    text = re.sub(r"[^\w\- ]", "", text)  # \w w Pythonie 3 obejmuje polskie litery
    return text.replace(" ", "-")


def anchors_of(path: Path) -> set[str]:
    seen: dict[str, int] = {}
    out: set[str] = set()
    in_code = False
    for line in read(path).splitlines():
        if line.startswith("```"):
            in_code = not in_code
        if not in_code and re.match(r"#{1,6} ", line):
            s = slug(line)
            n = seen.get(s, 0)
            seen[s] = n + 1
            out.add(s if n == 0 else f"{s}-{n}")
    return out


def check_links() -> int:
    files = sorted(DOCS.rglob("*.md")) + [ROOT / "README.md"]
    cache: dict[Path, set[str]] = {}
    total = 0
    for md in files:
        in_code = False
        for line in read(md).splitlines():
            if line.startswith("```"):
                in_code = not in_code
            if in_code:
                continue
            for m in re.finditer(r"\[[^\]]*\]\(([^)\s]+)\)", line):
                target = m.group(1)
                if re.match(r"[a-z]+://", target) or target.startswith("mailto:"):
                    continue
                total += 1
                path_part, _, anchor = target.partition("#")
                dest = (md.parent / path_part).resolve() if path_part else md
                if not dest.exists():
                    fail(f"{md.relative_to(ROOT)}: odnośnik do nieistniejącego pliku: {target}")
                    continue
                if anchor and dest.suffix == ".md":
                    if dest not in cache:
                        cache[dest] = anchors_of(dest)
                    if anchor not in cache[dest]:
                        fail(f"{md.relative_to(ROOT)}: brak kotwicy #{anchor} w {dest.relative_to(ROOT)}")
    return total


# ---------------------------------------------------------------------------------------------------------------------
# 3b. Diagramy Mermaid
# ---------------------------------------------------------------------------------------------------------------------
MERMAID_BLOCK = re.compile(r"```mermaid\n(.*?)```", re.S)


def check_mermaid() -> int:
    """Średnik w treści komunikatu diagramu sekwencji kończy instrukcję i psuje cały diagram (nie rysuje się)."""
    count = 0
    for md in sorted(DOCS.rglob("*.md")):
        text = read(md)
        for m in MERMAID_BLOCK.finditer(text):
            count += 1
            body = m.group(1)
            if not body.lstrip().startswith("sequenceDiagram"):
                continue
            first_line = text[: m.start(1)].count("\n") + 1
            for i, line in enumerate(body.split("\n")):
                if ":" in line and ";" in line.split(":", 1)[1]:
                    fail(f"{md.relative_to(ROOT)}:{first_line + i}: średnik w treści komunikatu diagramu sekwencji "
                         f"(Mermaid uzna go za koniec instrukcji i nie narysuje diagramu); użyj przecinka")
    return count


# ---------------------------------------------------------------------------------------------------------------------
# 4. Liczby
# ---------------------------------------------------------------------------------------------------------------------
def pl_number(value: str) -> str:
    return value.replace(".", ",")


def check_numbers() -> None:
    weights_src = read(SRC / "pl" / "tutlaw" / "backend" / "review" / "fsrs" / "FsrsWeights.java")
    body = re.search(r"new double\[\]\{(.*?)\}", weights_src, re.S).group(1)
    weights = re.findall(r"-?\d+\.\d+|-?\d+", body)
    if len(weights) != 21:
        fail(f"FsrsWeights.defaults() ma {len(weights)} parametrów, a FSRS-6 wymaga 21")
    fsrs_doc = read(DOCS / "04-nauka-testy-fiszki-fsrs.md")
    # Parametry są w dokumentacji w dwóch tabelach (w0–w10 i w11–w20) tuż pod nagłówkiem „Domyślne parametry”.
    start = fsrs_doc.find("**Domyślne parametry**")
    documented: list[str] = []
    if start < 0:
        fail("docs/04 nie ma akapitu „Domyślne parametry”")
    else:
        lines = fsrs_doc[start:].splitlines()[1:]
        header_seen = False
        for line in lines:
            stripped = line.strip()
            if not stripped:
                if documented or header_seen:
                    if len(documented) >= 21:
                        break
                continue
            if not stripped.startswith("|"):
                break
            cells = [c.strip() for c in stripped.strip("|").split("|")]
            if all(re.fullmatch(r"-+", c) for c in cells):
                continue
            if all(re.fullmatch(r"w\d+", c) for c in cells):
                header_seen = True
                continue
            documented.extend(cells)
        if len(documented) != 21:
            fail(f"docs/04: tabele parametrów FSRS mają {len(documented)} wartości, a powinny mieć 21")
        else:
            for i, (w, d) in enumerate(zip(weights, documented)):
                if pl_number(w) != d:
                    fail(f"parametr w{i}: w kodzie {w}, w docs/04 {d}")

    yml = read(ROOT / "src" / "main" / "resources" / "application.yml")
    expected = {
        "request-retention": ("0.9", "05-konfiguracja-i-uruchomienie.md"),
        "new-cards-per-day": ("20", "05-konfiguracja-i-uruchomienie.md"),
        "new-test-questions-per-day": ("40", "05-konfiguracja-i-uruchomienie.md"),
        "max-reviews-per-day": ("100", "05-konfiguracja-i-uruchomienie.md"),
        "leech-threshold": ("8", "05-konfiguracja-i-uruchomienie.md"),
    }
    for key, (doc_value, doc_file) in expected.items():
        m = re.search(rf"{key}:\s*([0-9.]+)", yml)
        if not m:
            fail(f"application.yml nie ma klucza {key}")
        elif m.group(1) != doc_value:
            fail(f"application.yml: {key} = {m.group(1)}, a dokumentacja zakłada {doc_value}")
        elif f"`{key}`" not in read(DOCS / doc_file) and f".{key}`" not in read(DOCS / doc_file):
            fail(f"docs/{doc_file} nie opisuje klucza {key}")

    # limity prób
    limiter = read(SRC / "pl" / "tutlaw" / "backend" / "auth" / "AuthRateLimiter.java")
    security_doc = read(DOCS / "03-bezpieczenstwo-i-konta.md")
    for const in ("LOGIN_PER_IP", "LOGIN_FAILURES", "REGISTER_PER_IP", "EMAIL_ACTION_PER_IP",
                  "EMAIL_ACTION_PER_ACCOUNT", "RESET_ATTEMPTS_PER_IP"):
        m = re.search(rf"{const} = (\d+);", limiter)
        if not m:
            fail(f"AuthRateLimiter nie ma stałej {const}")
        elif not re.search(rf"\|[^|\n]*\|\s*{m.group(1)}\s*\|", security_doc):
            fail(f"docs/03: tabela limitów nie zawiera wartości {m.group(1)} ({const})")

    # liczba klas testowych
    tests = [p for p in (ROOT / "src" / "test").rglob("*Test*.java")]
    m = re.search(r"\*\*(\d+) klasy testowe\*\*", read(DOCS / "01-architektura.md"))
    if m and int(m.group(1)) != len(tests):
        fail(f"docs/01 podaje {m.group(1)} klas testowych, a w src/test jest {len(tests)}")


def main() -> int:
    n_endpoints = check_endpoints()
    n_tables, n_migrations = check_database()
    n_links = check_links()
    n_diagrams = check_mermaid()
    check_numbers()
    print(f"endpointy w kodzie: {n_endpoints} | tabele z migracji: {n_tables} | migracje: {n_migrations} | "
          f"sprawdzone odnośniki: {n_links} | diagramy Mermaid: {n_diagrams}")
    if errors:
        print(f"\nROZBIEŻNOŚCI ({len(errors)}):")
        for e in errors:
            print("  -", e)
        return 1
    print("OK: dokumentacja zgadza się z kodem w sprawdzanych miejscach.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
