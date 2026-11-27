# Caso 2: librerías con vulnerabilidades y un pipeline que las detiene

Este proyecto es una de las demos de la charla "Tu código tiene vulnerabilidades, y tus dependencias también" (PragmaTalk). Muestra cómo una librería que usas puede traer una vulnerabilidad grave, cómo detectarla automáticamente y cómo evitar que entre al proyecto.

> **Importante:** este proyecto usa a propósito versiones viejas de librerías con vulnerabilidades conocidas. No copies estas versiones en ningún proyecto real.

## Qué hay en este proyecto

No tiene código propio con errores. Solo usa dos librerías:

- **log4j-core 2.14.1.** Tiene Log4Shell (CVE-2021-44228), una de las vulnerabilidades más graves de los últimos años: puntaje 10 sobre 10.
- **spring-data-jpa 3.1.11.** Por sí sola no es el problema. Pero trae otra librería, spring-core 6.0.19, que sí tiene una vulnerabilidad (CVE-2025-41249).

Ese segundo caso es importante. Las librerías que usas traen otras librerías, que a su vez traen otras. A esas se les llama dependencias transitivas. Nadie escribió "spring-core 6.0.19" en este proyecto, y aun así está ahí.

Un CVE es el número con el que se identifica públicamente una vulnerabilidad conocida. El puntaje (CVSS) va de 0 a 10: desde 7 se considera alta y desde 9, crítica.

## Paso 1: saber qué librerías tienes

No puedes arreglar lo que no sabes que tienes. Este comando muestra todas las librerías del proyecto, incluidas las que llegan de forma indirecta:

```bash
./mvnw dependency:tree        # en Windows: .\mvnw.cmd dependency:tree
```

## Paso 2: buscar vulnerabilidades

Hay herramientas que revisan cada librería contra bases de datos públicas de vulnerabilidades. Aquí van dos.

**Con Trivy** (la más sencilla, no pide registro):

```bash
trivy fs --scanners vuln --severity HIGH,CRITICAL --exit-code 1 .
```

Encuentra 4 vulnerabilidades altas o críticas, entre ellas Log4Shell en log4j-core y la de spring-core. El comando termina con código 1. Para un pipeline, eso significa "detente": el paso falla y el cambio no avanza.

**Con OWASP Dependency-Check:**

```bash
./mvnw dependency-check:check -DnvdApiKey=TU_KEY
```

Necesita una clave gratuita de la NVD, la base de datos de vulnerabilidades del gobierno de Estados Unidos. Se pide aquí: <https://nvd.nist.gov/developers/request-an-api-key>. La primera vez descarga toda la base y tarda un rato; las siguientes tardan segundos.

El proyecto está configurado para fallar si encuentra algo con puntaje 7 o más (`failBuildOnCVSS` en el `pom.xml`). El detalle queda en `target/dependency-check-report.html`.

## Paso 3: corregir

La solución casi siempre es la misma: actualizar a una versión sin el problema. El proyecto trae un perfil llamado `corregido` con log4j-core 2.26.1 y spring-data-jpa 3.5.13:

```bash
./mvnw -Pcorregido dependency-check:check -DnvdApiKey=TU_KEY
```

Con esas versiones no aparece ninguna vulnerabilidad alta ni crítica. Al actualizar spring-data-jpa también llega una versión nueva de spring-core, y por eso desaparece la vulnerabilidad indirecta.

Trivy no lee los perfiles de Maven. Para ver la corrección con Trivy, cambia `log4j.version` y `spring-data-jpa.version` en la sección `<properties>` del `pom.xml`.

## En GitHub: revisiones automáticas

Lo útil no es correr estas herramientas a mano un día, sino que corran solas en cada cambio. El repositorio tiene dos flujos en GitHub Actions.

### Revisión completa (se corre a mano)

Está en [`.github/workflows/sca.yml`](.github/workflows/sca.yml). Se lanza desde **Actions**, en "SCA", con el botón **Run workflow**. Tiene cuatro pasos:

1. **Trivy con las versiones del repositorio.** Falla, porque las librerías tienen vulnerabilidades.
2. **Trivy con las versiones corregidas.** Pasa. Así se ve el antes y el después.
3. **Fluid Attacks.** Usa la herramienta gratuita de [Fluid Attacks](https://github.com/fluidattacks/sca-action), que no pide cuenta. Falla y publica lo que encontró en la pestaña **Security**, en "Code scanning".
4. **OWASP Dependency-Check.** Falla. Para que funcione hay que guardar la clave de la NVD como secreto del repositorio, con el nombre `NVD_API_KEY` (Settings, luego Secrets and variables, luego Actions).

Que estos pasos salgan en rojo es buena señal: significa que el control funciona.

Un detalle sobre Fluid Attacks: en la versión usada aquí (1.2.0), la herramienta avisa que encontró vulnerabilidades, pero no hace fallar el paso por sí sola. Por eso el flujo tiene un paso extra que revisa el resultado y falla si hubo hallazgos.

### Revisión de cada pull request

Está en [`.github/workflows/pr-gate.yml`](.github/workflows/pr-gate.yml) y usa Dependency Review, de GitHub. Corre sola en cada pull request y revisa solo las librerías que ese cambio agrega o modifica. Si alguna tiene una vulnerabilidad alta o crítica, la revisión falla y deja un comentario en el pull request explicando cuál es.

Que revise solo lo nuevo es clave. Este repositorio ya tiene Log4Shell en la rama principal; si la revisión mirara todo, fallaría en cualquier cambio y la gente aprendería a ignorarla. Así, cada cambio responde solo por lo que agrega.

## Pruébalo con un pull request

La rama `feature/plantillas-notificaciones` agrega una funcionalidad sencilla: plantillas para los mensajes que se envían a los afiliados. Para hacerlo usa la librería commons-text en su versión 1.9, que tiene Text4Shell (CVE-2022-42889, puntaje 9.8). Es un problema parecido a Log4Shell.

Al abrir un pull request de esa rama hacia `main`:

1. La revisión "Dependency Review" falla.
2. Aparece un comentario en el pull request: una librería vulnerable, commons-text 1.9, ejecución de código arbitrario, crítica.
3. Si la rama `main` tiene la regla que exige pasar esa revisión, el botón para unir los cambios queda bloqueado.

La corrección es cambiar la versión a 1.15.0 en la misma rama. La revisión pasa y el pull request ya se puede unir.

Para que esto funcione en tu copia del repositorio hay que activar tres cosas una sola vez:

1. **Dependency graph** encendido, en Settings, luego Code security. Sin esto la revisión falla con el mensaje "Dependency review is not supported on this repository".
2. Una regla en la rama `main` que exija pasar la revisión "Dependency Review", en Settings, luego Branches. Esa revisión aparece en la lista después de haber corrido al menos una vez.
3. El repositorio debe ser público. En uno privado, Dependency Review y las reglas de rama necesitan planes de pago.

## Qué necesitas

- Java 17 o más nuevo. No hace falta instalar Maven: el proyecto ya trae lo necesario.
- Trivy, si quieres la opción que no pide registro.
