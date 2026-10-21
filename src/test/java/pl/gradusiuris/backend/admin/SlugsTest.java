package pl.gradusiuris.backend.admin;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SlugsTest {

    @Test
    void fold_dropsPolishDiacriticsAndCase() {
        assertThat(Slugs.fold("Zażółć GĘŚLĄ jaźń")).isEqualTo("zazolc gesla jazn");
        assertThat(Slugs.fold(null)).isEmpty();
    }

    @Test
    void of_buildsAReadableAddress() {
        assertThat(Slugs.of("Aplikacja uzupełniająca sędziowska", 60)).isEqualTo("aplikacja-uzupelniajaca-sedziowska");
        assertThat(Slugs.of("  Prawo karne (część ogólna)!  ", 100)).isEqualTo("prawo-karne-czesc-ogolna");
    }

    @Test
    void of_fallsBackWhenNothingUsableIsLeft_andRespectsTheLimit() {
        assertThat(Slugs.of("???", 60)).isEqualTo("pozycja");
        String slug = Slugs.of("a".repeat(30) + " " + "b".repeat(30), 31);
        assertThat(slug).hasSizeLessThanOrEqualTo(31).doesNotEndWith("-");
    }

    @Test
    void unique_addsTheFirstFreeNumber() {
        Set<String> taken = Set.of("notarialna", "notarialna-2");
        assertThat(Slugs.unique("Notarialna", 60, taken::contains)).isEqualTo("notarialna-3");
        assertThat(Slugs.unique("Komornicza", 60, taken::contains)).isEqualTo("komornicza");
    }

    @Test
    void unique_keepsTheNumberedAddressWithinTheLimit() {
        String base = "x".repeat(10);
        Set<String> taken = Set.of(base);
        String slug = Slugs.unique(base, 10, taken::contains);
        assertThat(slug).hasSize(10).endsWith("-2");
    }
}
