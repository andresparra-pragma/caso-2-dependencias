# Caso 2 · Dependencias vulnerables + CI Gate

Demo de la charla **"Tu código tiene vulnerabilidades, y tus dependencias también"** (PragmaTalk).

Un proyecto **sin una sola línea de código propio malo**, pero con dependencias vulnerables. La idea: *no puedes arreglar
lo que no sabes que tienes*. Un escáner de dependencias (SCA) las detecta y un **CI Gate** hace fallar el build.

> ⚠️ **Proyecto solo para escanear.** Declara a propósito versiones con vulnerabilidades conocidas; no tiene código que
> ejecute esas librerías. No reutilices estas versiones en ningún proyecto real.

| Dependencia | Versión | Por qué está aquí |
|---|---|---|
| `org.apache.logging.log4j:log4j-core` | 2.14.1 | **Log4Shell** · CVE-2021-44228 · CVSS 10.0 |
| `org.springframework.data:spring-data-jpa` | 3.1.11 | Trae CVE **heredados de dependencias transitivas** (por ejemplo, CVE-2025-41249 en `spring-core 6.0.19`) |

## 1. El inventario

```bash
./mvnw dependency:tree        # .\mvnw.cmd en Windows
```

Muestra las dependencias directas y las **transitivas** que traen. Ahí se ve la *ubicación* de cada vulnerabilidad:
`spring-core` nadie lo puso a mano, llega por `spring-data-jpa`.

## 2. El escaneo y el CI Gate

### Opción A · OWASP Dependency-Check

Requiere una API key gratuita de la NVD: <https://nvd.nist.gov/developers/request-an-api-key>.
La primera ejecución descarga la base de CVE y puede tardar; después tarda segundos.

```bash
./mvnw dependency-check:check -DnvdApiKey=TU_KEY
```

El umbral del gate está en el `pom.xml` (`failBuildOnCVSS=7`): con hallazgos de CVSS ≥ 7 el build termina en
**BUILD FAILURE**. Reporte en `target/dependency-check-report.html`. Se puede cambiar con `-DfailBuildOnCVSS=N`.

### Opción B · Trivy (sin API key)

```bash
trivy fs --scanners vuln --severity HIGH,CRITICAL --exit-code 1 .
```

Resultado (noviembre de 2026): **4 hallazgos HIGH/CRITICAL**, entre ellos CVE-2021-44228 (Log4Shell) en
`log4j-core 2.14.1` y CVE-2025-41249 en `spring-core 6.0.19`. El comando termina con **código de salida 1**: para un
pipeline, eso es rojo.

## 3. La corrección: actualizar

El perfil `corregido` sube las versiones (`log4j-core 2.26.1`, `spring-data-jpa 3.5.13`):

```bash
./mvnw -Pcorregido dependency:tree
./mvnw -Pcorregido dependency-check:check -DnvdApiKey=TU_KEY
```

Trivy lee las versiones por defecto del `pom.xml` (no activa perfiles): para ver la corrección con Trivy, cambia
`log4j.version` y `spring-data-jpa.version` en `<properties>`. Con esas versiones: **0 hallazgos**.

## 4. El gate en GitHub Actions

[`.github/workflows/sca.yml`](.github/workflows/sca.yml) corre el mismo escaneo en el pipeline. Se ejecuta a mano
(**Actions → SCA → Run workflow**) y en cada pull request:

- **Trivy:** no necesita configuración. Falla a propósito, porque las dependencias de este repo son vulnerables: ese
  es el punto de la demo.
- **Dependency-Check:** necesita el secreto `NVD_API_KEY` en el repo (*Settings → Secrets and variables → Actions*).

Requisitos para correrlo en local: **Java 17+** (el proyecto trae el Maven Wrapper) y, para la opción B, Trivy.
