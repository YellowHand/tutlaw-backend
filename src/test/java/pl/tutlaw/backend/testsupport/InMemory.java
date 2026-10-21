package pl.tutlaw.backend.testsupport;

import pl.tutlaw.backend.common.GroupCount;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Repozytorium Spring Data "w pamięci" do testów serwisów, które czytają i zapisują encje (findAll, findById,
 * findAllById, save, saveAll, delete, count). Zapytania własne repozytorium obsługuje przekazany {@code extra}; metoda,
 * której nikt nie przewidział, kończy się wyjątkiem, więc nowe zapytanie w serwisie nie przejdzie niezauważone.
 */
public final class InMemory {

    private InMemory() {
    }

    public static <E, R> R repo(Class<R> type, List<E> store, Function<E, Long> getId, BiConsumer<E, Long> setId,
                                FakeRepos.Handler extra) {
        long[] next = {1000};
        return FakeRepos.proxy(type, (method, args) -> {
            switch (method) {
                case "findAll":
                    return new ArrayList<>(store);
                case "count":
                    return (long) store.size();
                case "findById":
                    return store.stream().filter(e -> getId.apply(e).equals(args[0])).findFirst();
                case "findAllById": {
                    List<E> found = new ArrayList<>();
                    for (Object id : (Iterable<?>) args[0]) {
                        store.stream().filter(e -> getId.apply(e).equals(id)).findFirst().ifPresent(found::add);
                    }
                    return found;
                }
                case "save":
                    return save(store, getId, setId, next, args[0]);
                case "saveAll": {
                    List<Object> saved = new ArrayList<>();
                    for (Object entity : (Iterable<?>) args[0]) {
                        saved.add(save(store, getId, setId, next, entity));
                    }
                    return saved;
                }
                case "delete":
                    store.remove(args[0]);
                    return null;
                default:
                    return extra.handle(method, args);
            }
        });
    }

    @SuppressWarnings("unchecked")
    private static <E> Object save(List<E> store, Function<E, Long> getId, BiConsumer<E, Long> setId, long[] next, Object entity) {
        E e = (E) entity;
        if (getId.apply(e) == null) {
            setId.accept(e, next[0]++);
        }
        if (!store.contains(e)) {
            store.add(e);
        }
        return e;
    }

    public static GroupCount group(long id, long total) {
        return new GroupCount() {
            @Override public Long getGroupId() { return id; }
            @Override public Long getTotal() { return total; }
        };
    }

    public static <T> Optional<T> none() {
        return Optional.empty();
    }
}
