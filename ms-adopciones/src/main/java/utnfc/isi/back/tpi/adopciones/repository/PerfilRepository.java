package utnfc.isi.back.tpi.adopciones.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import utnfc.isi.back.tpi.adopciones.model.Perfil;

public interface PerfilRepository extends JpaRepository<Perfil, String> {
}
