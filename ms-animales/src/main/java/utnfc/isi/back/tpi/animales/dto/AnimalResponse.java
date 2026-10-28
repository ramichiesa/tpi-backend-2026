package utnfc.isi.back.tpi.animales.dto;

import utnfc.isi.back.tpi.animales.model.Animal;
import utnfc.isi.back.tpi.animales.model.EstadoAnimal;
import utnfc.isi.back.tpi.animales.model.Especie;
import utnfc.isi.back.tpi.animales.model.Tamanio;

/** Representacion publica de un animal. Nunca se expone la entidad JPA. */
public record AnimalResponse(
        Long id,
        String nombre,
        Especie especie,
        int edadMeses,
        Tamanio tamanio,
        String necesidadesEspeciales,
        boolean aptoNinios,
        boolean aptoOtrosAnimales,
        boolean requiereEspacioExterior,
        EstadoAnimal estado,
        String motivoNoDisponible) {

    public static AnimalResponse desde(Animal a) {
        return new AnimalResponse(a.getId(), a.getNombre(), a.getEspecie(), a.getEdadMeses(), a.getTamanio(),
                a.getNecesidadesEspeciales(), a.isAptoNinios(), a.isAptoOtrosAnimales(),
                a.isRequiereEspacioExterior(), a.getEstado(), a.getMotivoNoDisponible());
    }
}
