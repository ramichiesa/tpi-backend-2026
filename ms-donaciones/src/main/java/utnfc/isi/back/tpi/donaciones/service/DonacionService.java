package utnfc.isi.back.tpi.donaciones.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import utnfc.isi.back.tpi.common.exception.AccesoDenegadoException;
import utnfc.isi.back.tpi.common.exception.RecursoNoEncontradoException;
import utnfc.isi.back.tpi.common.exception.ServicioNoDisponibleException;
import utnfc.isi.back.tpi.common.exception.SolicitudInvalidaException;
import utnfc.isi.back.tpi.donaciones.client.MercadoPagoClient;
import utnfc.isi.back.tpi.donaciones.config.DonacionesProperties;
import utnfc.isi.back.tpi.donaciones.dto.DonacionDtos.DonacionResponse;
import utnfc.isi.back.tpi.donaciones.dto.DonacionDtos.TotalResponse;
import utnfc.isi.back.tpi.donaciones.model.Donacion;
import utnfc.isi.back.tpi.donaciones.model.EstadoDonacion;
import utnfc.isi.back.tpi.donaciones.repository.DonacionRepository;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Proceso P5: recepcion de donaciones (reglas D-01 a D-04).
 * Las llamadas a Mercado Pago se hacen fuera de las transacciones de base de datos.
 */
@Service
public class DonacionService {

    private static final Logger log = LoggerFactory.getLogger(DonacionService.class);

    private final DonacionRepository repository;
    private final MercadoPagoClient mercadoPago;
    private final DonacionesProperties props;
    private final TransactionTemplate tx;
    private final Clock clock;

    public DonacionService(DonacionRepository repository, MercadoPagoClient mercadoPago, DonacionesProperties props,
                           PlatformTransactionManager transactionManager, Clock clock) {
        this.repository = repository;
        this.mercadoPago = mercadoPago;
        this.props = props;
        this.tx = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    /**
     * D-01 y creacion del cobro. Si Mercado Pago falla, la excepcion (503) se propaga ANTES de
     * guardar: no queda ninguna donacion registrada sin enlace de pago.
     */
    public DonacionResponse crear(String donanteId, BigDecimal monto) {
        if (monto.compareTo(props.montoMinimo()) < 0) {
            throw new SolicitudInvalidaException("El monto minimo de una donacion es " + props.montoMinimo()
                    + " ARS (D-01)");
        }
        String referencia = UUID.randomUUID().toString();
        MercadoPagoClient.Cobro cobro = mercadoPago.crearCobro(referencia, monto, props.mercadoPago().tituloItem());
        Donacion donacion = new Donacion(referencia, donanteId, monto, cobro.preferenciaId(), cobro.linkPago(),
                LocalDateTime.now(clock));
        DonacionResponse respuesta = tx.execute(st -> DonacionResponse.desde(repository.save(donacion), true));
        log.info("Donacion {} creada PENDIENTE por {} ARS", respuesta.id(), monto);
        return respuesta;
    }

    /** D-04: el donante ve las suyas; el Coordinador, todas. No consulta al proveedor. */
    public List<DonacionResponse> listar(String usuario, boolean coordinador) {
        return tx.execute(st -> (coordinador ? repository.findAllByOrderByIdDesc()
                : repository.findByDonanteIdOrderByIdDesc(usuario))
                .stream()
                .map(d -> DonacionResponse.desde(d, d.getEstado() == EstadoDonacion.CONFIRMADA))
                .toList());
    }

    /**
     * D-04 y D-02: si la donacion esta PENDIENTE, pregunta a Mercado Pago si hay un pago aprobado
     * con su referencia. Si el proveedor no responde, devuelve la donacion PENDIENTE con
     * verificado = false (se puede volver a consultar mas tarde).
     */
    public DonacionResponse obtener(String usuario, boolean coordinador, Long id) {
        Donacion donacion = tx.execute(st -> buscar(id));
        if (!coordinador && !donacion.perteneceA(usuario)) {
            throw new AccesoDenegadoException("La donacion " + id + " pertenece a otro donante (D-04)");
        }
        if (donacion.getEstado() == EstadoDonacion.CONFIRMADA) {
            return DonacionResponse.desde(donacion, true);
        }

        Optional<String> pagoAprobado;
        try {
            pagoAprobado = mercadoPago.buscarPagoAprobado(donacion.getReferencia());
        } catch (ServicioNoDisponibleException e) {
            log.warn("No se pudo verificar la donacion {}: queda PENDIENTE", id);
            return DonacionResponse.desde(donacion, false);
        }
        if (pagoAprobado.isEmpty()) {
            return DonacionResponse.desde(donacion, true);
        }
        return confirmar(id, pagoAprobado.get());
    }

    /** D-03: CONFIRMADA es final y un pago (pago_id UNIQUE) no puede confirmar dos donaciones. */
    private DonacionResponse confirmar(Long id, String pagoId) {
        try {
            return tx.execute(st -> {
                Donacion d = buscar(id);
                if (d.getEstado() == EstadoDonacion.PENDIENTE) {
                    d.confirmar(pagoId, LocalDateTime.now(clock));
                    repository.saveAndFlush(d);
                    log.info("Donacion {} CONFIRMADA con el pago {}", id, pagoId);
                }
                return DonacionResponse.desde(d, true);
            });
        } catch (DataIntegrityViolationException e) {
            log.error("El pago {} ya estaba asociado a otra donacion; la donacion {} sigue PENDIENTE", pagoId, id);
            return tx.execute(st -> DonacionResponse.desde(buscar(id), false));
        }
    }

    public TotalResponse total() {
        return tx.execute(st -> new TotalResponse(
                repository.sumarMontoPorEstado(EstadoDonacion.CONFIRMADA),
                repository.countByEstado(EstadoDonacion.CONFIRMADA)));
    }

    private Donacion buscar(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe la donacion " + id));
    }
}
