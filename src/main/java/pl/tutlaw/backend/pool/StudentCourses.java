package pl.tutlaw.backend.pool;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Przedmioty studenta w jego puli. Pulą pytań studenta są akty wybranych przedmiotów (akty mogą się powtarzać między
 * przedmiotami, np. Kodeks karny w dwóch przedmiotach, a pytanie i tak liczy się raz).
 *
 * @param actsByCourse akty każdego przedmiotu, który ma co najmniej jeden akt (tylko takie da się wybrać)
 * @param inPool       przedmioty w puli: wybrane przez studenta albo, gdy niczego nie wybrał, wszystkie
 * @param narrowed     czy student wybrał tylko część przedmiotów
 */
public record StudentCourses(Map<Long, List<Long>> actsByCourse, Set<Long> inPool, boolean narrowed) {
}
