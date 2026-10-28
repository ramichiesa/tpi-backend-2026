package utnfc.isi.back.tpi.animales.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import utnfc.isi.back.tpi.common.exception.ConflictoException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Maquina de estados del animal: transiciones validas, invalidas y regla R-13. */
class AnimalTest {

    private static Animal nuevo() {
        return new Animal("Luna", Especie.PERRO, 24, Tamanio.MEDIANO, null, true, true, false);
    }

    private static Animal enEstado(EstadoAnimal estado) {
        Animal a = nuevo();
        switch (estado) {
            case DISPONIBLE -> { }
            case RESERVADO -> a.cambiarEstadoPorProceso(EstadoAnimal.RESERVADO);
            case EN_ADAPTACION -> {
                a.cambiarEstadoPorProceso(EstadoAnimal.RESERVADO);
                a.cambiarEstadoPorProceso(EstadoAnimal.EN_ADAPTACION);
            }
            case ADOPTADO -> {
                a.cambiarEstadoPorProceso(EstadoAnimal.RESERVADO);
                a.cambiarEstadoPorProceso(EstadoAnimal.EN_ADAPTACION);
                a.cambiarEstadoPorProceso(EstadoAnimal.ADOPTADO);
            }
            case NO_DISPONIBLE -> a.marcarNoDisponible("Cuarentena");
        }
        return a;
    }

    @Test
    void unAnimalNuevoEmpiezaDisponible() {
        assertThat(nuevo().getEstado()).isEqualTo(EstadoAnimal.DISPONIBLE);
    }

    @ParameterizedTest(name = "{0} -> {1} es valida")
    @CsvSource({
            "DISPONIBLE, RESERVADO",
            "RESERVADO, EN_ADAPTACION",
            "RESERVADO, DISPONIBLE",
            "EN_ADAPTACION, ADOPTADO",
            "EN_ADAPTACION, DISPONIBLE"
    })
    void transicionesDelProcesoPermitidas(EstadoAnimal desde, EstadoAnimal hacia) {
        Animal a = enEstado(desde);
        a.cambiarEstadoPorProceso(hacia);
        assertThat(a.getEstado()).isEqualTo(hacia);
    }

    @ParameterizedTest(name = "{0} -> {1} se rechaza")
    @CsvSource({
            "DISPONIBLE, EN_ADAPTACION",
            "DISPONIBLE, ADOPTADO",
            "RESERVADO, RESERVADO",
            "RESERVADO, ADOPTADO",
            "ADOPTADO, DISPONIBLE",
            "NO_DISPONIBLE, RESERVADO",
            "DISPONIBLE, NO_DISPONIBLE"
    })
    void transicionesDelProcesoInvalidasLanzanConflicto(EstadoAnimal desde, EstadoAnimal hacia) {
        Animal a = enEstado(desde);
        assertThatThrownBy(() -> a.cambiarEstadoPorProceso(hacia)).isInstanceOf(ConflictoException.class);
        assertThat(a.getEstado()).isEqualTo(desde);
    }

    @ParameterizedTest(name = "R-13: {0} no puede pasar a NO_DISPONIBLE")
    @CsvSource({"RESERVADO", "EN_ADAPTACION", "ADOPTADO", "NO_DISPONIBLE"})
    void r13NoDisponibleSoloDesdeDisponible(EstadoAnimal desde) {
        Animal a = enEstado(desde);
        assertThatThrownBy(() -> a.marcarNoDisponible("Tratamiento")).isInstanceOf(ConflictoException.class);
    }

    @Test
    void noDisponibleGuardaMotivoYSeRevierte() {
        Animal a = nuevo();
        a.marcarNoDisponible("Tratamiento veterinario");
        assertThat(a.getEstado()).isEqualTo(EstadoAnimal.NO_DISPONIBLE);
        assertThat(a.getMotivoNoDisponible()).isEqualTo("Tratamiento veterinario");

        a.marcarDisponible();
        assertThat(a.getEstado()).isEqualTo(EstadoAnimal.DISPONIBLE);
        assertThat(a.getMotivoNoDisponible()).isNull();
    }

    @Test
    void marcarDisponibleSoloDesdeNoDisponible() {
        Animal a = enEstado(EstadoAnimal.RESERVADO);
        assertThatThrownBy(a::marcarDisponible).isInstanceOf(ConflictoException.class);
    }
}
