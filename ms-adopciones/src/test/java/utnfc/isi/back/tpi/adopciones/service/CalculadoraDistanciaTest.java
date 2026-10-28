package utnfc.isi.back.tpi.adopciones.service;

import org.junit.jupiter.api.Test;
import utnfc.isi.back.tpi.adopciones.client.Coordenadas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class CalculadoraDistanciaTest {

    private static final Coordenadas REFUGIO = new Coordenadas(-31.4334, -64.1918);

    @Test
    void mismoPuntoEsCero() {
        assertThat(CalculadoraDistancia.km(REFUGIO, REFUGIO)).isZero();
    }

    @Test
    void cordobaBuenosAiresRondaLos650Km() {
        assertThat(CalculadoraDistancia.km(REFUGIO, new Coordenadas(-34.6037, -58.3816))).isCloseTo(650.0, within(15.0));
    }

    @Test
    void cordobaVillaCarlosPazRondaLos30Km() {
        assertThat(CalculadoraDistancia.km(REFUGIO, new Coordenadas(-31.4241, -64.4978))).isCloseTo(29.0, within(3.0));
    }

    @Test
    void esSimetrica() {
        Coordenadas otra = new Coordenadas(-32.9468, -60.6393);
        assertThat(CalculadoraDistancia.km(REFUGIO, otra)).isEqualTo(CalculadoraDistancia.km(otra, REFUGIO));
    }
}
