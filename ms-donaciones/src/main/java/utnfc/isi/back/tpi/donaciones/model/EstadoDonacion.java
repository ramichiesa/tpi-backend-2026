package utnfc.isi.back.tpi.donaciones.model;

/**
 * PENDIENTE hasta que el proveedor informa un pago aprobado; CONFIRMADA es final (D-02, D-03).
 * No hay estado de rechazo: un pago rechazado puede reintentarse con el mismo enlace.
 */
public enum EstadoDonacion {
    PENDIENTE,
    CONFIRMADA
}
