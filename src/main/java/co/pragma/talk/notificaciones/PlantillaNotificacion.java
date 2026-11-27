package co.pragma.talk.notificaciones;

import java.util.Map;

import org.apache.commons.text.StringSubstitutor;

/**
 * Plantillas para las notificaciones que se envían a los afiliados.
 *
 * <pre>
 * new PlantillaNotificacion("Hola ${nombre}, tu aporte al plan ${plan} fue registrado.")
 *     .generar(Map.of("nombre", "Ana", "plan", "Ahorro 2026"));
 * </pre>
 */
public class PlantillaNotificacion {

    private final String plantilla;

    public PlantillaNotificacion(String plantilla) {
        this.plantilla = plantilla;
    }

    /** Reemplaza las variables ${...} de la plantilla con los valores dados. */
    public String generar(Map<String, String> valores) {
        return new StringSubstitutor(valores).replace(plantilla);
    }
}
