package utnfc.isi.back.tpi.adopciones.model;

import org.junit.jupiter.api.Test;
import utnfc.isi.back.tpi.common.exception.ConflictoException;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Reglas del agregado Solicitud: transiciones, R-08 a R-12, columnas de bloqueo e historial. */
class SolicitudTest {

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 10, 1, 10, 0);
    private static final int PLAZO = 7;
    private static final int PERIODO = 30;

    private static Solicitud pendiente(boolean requiereExcepcion) {
        return Solicitud.pendiente(7L, "Luna", "ana", T0, 150.0, requiereExcepcion, false, List.of());
    }

    @Test
    void unaSolicitudPendienteBloqueaAnimalYAdoptante() throws Exception {
        Solicitud s = pendiente(false);

        assertThat(s.getEstado()).isEqualTo(EstadoSolicitud.PENDIENTE);
        assertThat(campo(s, "animalPendiente")).isEqualTo(7L);
        assertThat(campo(s, "adoptanteActivo")).isEqualTo("ana");
        assertThat(s.getEventos()).hasSize(1);
    }

    @Test
    void alAprobarseLiberaElBloqueoDelAnimalPeroNoElDelAdoptante() throws Exception {
        Solicitud s = pendiente(false);
        s.aprobar(null, T0.plusHours(1), "coord");

        assertThat(s.getEstado()).isEqualTo(EstadoSolicitud.APROBADA);
        assertThat(campo(s, "animalPendiente")).isNull();
        assertThat(campo(s, "adoptanteActivo")).isEqualTo("ana");
    }

    @Test
    void r08AprobarConMarcaDeDistanciaExigeJustificacion() {
        Solicitud s = pendiente(true);

        assertThatThrownBy(() -> s.aprobar("  ", T0, "coord"))
                .isInstanceOf(ConflictoException.class).hasMessageContaining("R-08");

        s.aprobar("Se compromete a traer al animal a los controles", T0, "coord");
        assertThat(s.getJustificacionExcepcion()).startsWith("Se compromete");
    }

    @Test
    void r07SoloSeApruebaUnaSolicitudPendiente() {
        Solicitud s = pendiente(false);
        s.rechazar("Hogar no adecuado", T0, "coord");

        assertThatThrownBy(() -> s.aprobar(null, T0, "coord")).isInstanceOf(ConflictoException.class);
    }

    @Test
    void r04LaIncompatibilidadQuedaRegistradaComoRechazoSinBloquear() throws Exception {
        Solicitud s = Solicitud.rechazadaPorIncompatibilidad(7L, "Michi", "ana", T0, "Incompatibilidad (R-04)");

        assertThat(s.getEstado()).isEqualTo(EstadoSolicitud.RECHAZADA);
        assertThat(s.getMotivoRechazo()).contains("R-04");
        assertThat(campo(s, "animalPendiente")).isNull();
        assertThat(campo(s, "adoptanteActivo")).isNull();
        assertThat(s.getEventos().get(0).getActor()).isEqualTo(Solicitud.ACTOR_SISTEMA);
    }

    @Test
    void r10ConfirmarEntregaCalculaLaFechaDeControl() {
        Solicitud s = pendiente(false);
        s.aprobar(null, T0, "coord");
        s.confirmarEntrega(T0.plusDays(2), PLAZO, PERIODO, "coord");

        assertThat(s.getEstado()).isEqualTo(EstadoSolicitud.EN_ADAPTACION);
        assertThat(s.getFechaEntrega()).isEqualTo(T0.plusDays(2));
        assertThat(s.getFechaControl()).isEqualTo(T0.plusDays(32));
    }

    @Test
    void r09ConfirmarFueraDePlazoSeRechazaYElVencimientoCancela() throws Exception {
        Solicitud s = pendiente(false);
        s.aprobar(null, T0, "coord");
        LocalDateTime tarde = T0.plusDays(PLAZO).plusMinutes(1);

        assertThat(s.plazoEntregaVencido(tarde, PLAZO)).isTrue();
        assertThatThrownBy(() -> s.confirmarEntrega(tarde, PLAZO, PERIODO, "coord"))
                .isInstanceOf(ConflictoException.class).hasMessageContaining("R-09");

        s.vencerPlazoEntrega(tarde, PLAZO);
        assertThat(s.getEstado()).isEqualTo(EstadoSolicitud.CANCELADA);
        assertThat(campo(s, "adoptanteActivo")).isNull();
    }

    @Test
    void r09DentroDelPlazoNoSePuedeVencer() {
        Solicitud s = pendiente(false);
        s.aprobar(null, T0, "coord");

        assertThatThrownBy(() -> s.vencerPlazoEntrega(T0.plusDays(3), PLAZO)).isInstanceOf(ConflictoException.class);
    }

    @Test
    void r11ElControlAnticipadoSeRechazaYElConformeFinaliza() {
        Solicitud s = enAdaptacion();

        assertThatThrownBy(() -> s.registrarControl(ResultadoControl.CONFORME, null, T0.plusDays(10), "coord"))
                .isInstanceOf(ConflictoException.class).hasMessageContaining("R-11");

        s.registrarControl(ResultadoControl.CONFORME, "Bien adaptado", T0.plusDays(PERIODO), "coord");
        assertThat(s.getEstado()).isEqualTo(EstadoSolicitud.FINALIZADA);
    }

    @Test
    void r12ControlDesfavorableRevierte() throws Exception {
        Solicitud s = enAdaptacion();
        s.registrarControl(ResultadoControl.DESFAVORABLE, "No se adapto", T0.plusDays(PERIODO + 1), "coord");

        assertThat(s.getEstado()).isEqualTo(EstadoSolicitud.REVERTIDA);
        assertThat(s.getMotivoCierre()).isEqualTo("No se adapto");
        assertThat(campo(s, "adoptanteActivo")).isNull();
    }

    @Test
    void r12DevolucionSoloDuranteLaAdaptacion() {
        Solicitud pendiente = pendiente(false);
        assertThatThrownBy(() -> pendiente.devolver("Me mudo", T0, "ana")).isInstanceOf(ConflictoException.class);

        Solicitud s = enAdaptacion();
        s.devolver("Me mudo", T0.plusDays(5), "ana");
        assertThat(s.getEstado()).isEqualTo(EstadoSolicitud.REVERTIDA);
    }

    @Test
    void elHistorialRegistraCadaTransicionConSuActor() {
        Solicitud s = enAdaptacion();
        s.registrarControl(ResultadoControl.CONFORME, null, T0.plusDays(PERIODO), "coord");

        assertThat(s.getEventos()).extracting(EventoSolicitud::getEstadoNuevo).containsExactly(
                EstadoSolicitud.PENDIENTE, EstadoSolicitud.APROBADA, EstadoSolicitud.EN_ADAPTACION,
                EstadoSolicitud.FINALIZADA);
        assertThat(s.getEventos()).extracting(EventoSolicitud::getActor)
                .containsExactly("ana", "coord", "coord", "coord");
    }

    private static Solicitud enAdaptacion() {
        Solicitud s = pendiente(false);
        s.aprobar(null, T0, "coord");
        s.confirmarEntrega(T0, PLAZO, PERIODO, "coord");
        return s;
    }

    /** Lee una columna de bloqueo (privada, sin getter a proposito). */
    private static Object campo(Solicitud s, String nombre) throws Exception {
        var f = Solicitud.class.getDeclaredField(nombre);
        f.setAccessible(true);
        return f.get(s);
    }
}
