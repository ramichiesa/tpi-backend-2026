package utnfc.isi.back.tpi.donaciones.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import utnfc.isi.back.tpi.donaciones.model.Donacion;
import utnfc.isi.back.tpi.donaciones.model.EstadoDonacion;

import java.math.BigDecimal;
import java.util.List;

public interface DonacionRepository extends JpaRepository<Donacion, Long> {

    List<Donacion> findAllByOrderByIdDesc();

    List<Donacion> findByDonanteIdOrderByIdDesc(String donanteId);

    long countByEstado(EstadoDonacion estado);

    @Query("select coalesce(sum(d.monto), 0) from Donacion d where d.estado = :estado")
    BigDecimal sumarMontoPorEstado(EstadoDonacion estado);
}
