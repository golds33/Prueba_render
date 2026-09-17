# Informe: automatización de respaldos en la nube

## 1. Descripción

La aplicación `Triggers` es una aplicación Spring Boot con MVC, Thymeleaf, JPA y PostgreSQL. El despliegue se realiza con Docker en Render y los respaldos de PostgreSQL se almacenan en Backblaze B2 mediante su API compatible con Amazon S3.

El proyecto incluye:

- `Dockerfile`: compila la aplicación con Maven y la ejecuta con Java 21.
- `scripts/backup.sh`: genera un respaldo SQL y lo sube a Backblaze B2.
- `scripts/restore.sh`: descarga el respaldo más reciente y lo restaura con `psql`.
- `application.properties`: usa variables de entorno en producción y conserva valores locales por defecto.

## 2. Preparar PostgreSQL en Render

1. Crear una cuenta e iniciar sesión en [Render](https://render.com/).
2. Seleccionar **New > Postgres**.
3. Elegir un nombre para la base de datos, una región y el plan requerido.
4. Esperar a que la base de datos esté disponible.
5. Copiar la **Internal Database URL** para usarla desde otro servicio de Render en la misma región. Esta URL tiene la forma `postgresql://usuario:password@host:5432/base`.
6. Guardar la URL como `SPRING_DATASOURCE_URL` en el servicio web.

La base de datos puede inicializarse con Hibernate usando `SPRING_JPA_DDL_AUTO=update`. Para un entorno real se recomienda utilizar migraciones versionadas, como Flyway o Liquibase.

## 3. Desplegar la aplicación con Docker

1. Subir el proyecto a un repositorio Git accesible por Render.
2. En Render, seleccionar **New > Web Service** y conectar el repositorio.
3. Elegir **Docker** como entorno de ejecución. Render detectará el `Dockerfile` ubicado en la raíz.
4. Configurar la rama a desplegar y seleccionar el plan correspondiente.
5. Agregar las siguientes variables de entorno:

   | Variable | Valor |
   |---|---|
   | `SPRING_DATASOURCE_URL` | Internal Database URL de Render |
   | `SPRING_DATASOURCE_USERNAME` | Usuario de PostgreSQL |
   | `SPRING_DATASOURCE_PASSWORD` | Contraseña de PostgreSQL |
   | `SPRING_JPA_DDL_AUTO` | `update` |
   | `THYMELEAF_CACHE` | `false` |

6. Configurar el health check en `/` si la aplicación tiene una ruta disponible allí.
7. Crear el servicio. Render construirá la imagen, expondrá el puerto `8080` y ejecutará el JAR.
8. Verificar los registros de despliegue y abrir la URL pública del servicio.

El contenedor expone el puerto `8080` y usa `8080` como valor predeterminado. En Render, el `Dockerfile` respeta automáticamente la variable `PORT` si la plataforma asigna otro puerto.

## 4. Configurar Backblaze B2

1. Crear un bucket privado en Backblaze B2.
2. Crear una aplicación o clave de aplicación con permisos de lectura y escritura sobre el bucket.
3. Identificar el endpoint S3 de la región del bucket, por ejemplo `https://s3.us-west-004.backblazeb2.com`.
4. Configurar estas variables en el servicio que ejecute los scripts:

   ```text
   B2_KEY_ID=identificador_de_la_clave
   B2_APP_KEY=clave_secreta
   B2_ENDPOINT=https://s3.us-west-004.backblazeb2.com
   B2_BUCKET=nombre_del_bucket
   DB_URL=postgresql://usuario:password@host:5432/base
   ```

Las claves no deben escribirse en el repositorio ni compartirse en el informe público.

Antes de ejecutar los scripts, el entorno debe tener instalados `bash`, `postgresql-client` (`pg_dump` y `psql`) y AWS CLI.

## 5. Configurar Cron Jobs en Render

Crear un servicio **Cron Job** en Render para cada frecuencia. Cada Cron Job puede utilizar la misma imagen Docker del proyecto, que incluye Bash, PostgreSQL Client, AWS CLI y los scripts. Debe tener las variables de Backblaze B2 y `DB_URL`, y usar como comando:

```bash
bash scripts/backup.sh
```

Las expresiones solicitadas son:

| Frecuencia | Expresión Cron | Uso recomendado |
|---|---|---|
| Cada 5 minutos | `*/5 * * * *` | Pruebas o datos extremadamente críticos |
| Cada 1 hora | `0 * * * *` | Operación diaria con cambios frecuentes |
| Cada mañana | `0 6 * * *` | Sistemas con pocos cambios o respaldo diario |

En Render, seleccionar la zona horaria indicada por el servicio y confirmar que la hora `06:00` corresponde a la zona horaria elegida. Para la entrega académica se puede crear un Cron Job por frecuencia o documentar las tres configuraciones y activar una a la vez.

## 6. Ejecutar la simulación de pérdida de datos

> Realizar esta prueba solamente sobre la base de datos de práctica. El comando elimina todos los registros de la tabla `producto`.

1. Confirmar que existe un respaldo reciente en el bucket de Backblaze B2.
2. Abrir la consola de PostgreSQL o un cliente conectado a la base de datos de Render.
3. Ejecutar:

   ```sql
   DELETE FROM producto;
   ```

4. Comprobar la pérdida de datos:

   ```sql
   SELECT COUNT(*) FROM producto;
   ```

5. Registrar la fecha y hora de la prueba para compararla con el nombre del respaldo `backup_YYYYMMDD_HHMM.sql`.

## 7. Restaurar la información

### Opción A: usar el script

Desde un entorno con Bash, AWS CLI y `postgresql-client` instalados, exportar las variables y ejecutar:

```bash
export DB_URL='postgresql://usuario:password@host:5432/base'
export B2_KEY_ID='identificador_de_la_clave'
export B2_APP_KEY='clave_secreta'
export B2_ENDPOINT='https://s3.us-west-004.backblazeb2.com'
export B2_BUCKET='nombre_del_bucket'

bash scripts/restore.sh
```

El script lista los archivos con formato válido, selecciona el último por nombre, lo descarga en `backups/restore/` y ejecuta el SQL con `psql` sobre `DB_URL`.

### Opción B: restauración manual desde Backblaze B2

1. Listar los objetos del bucket:

   ```bash
   AWS_ACCESS_KEY_ID="$B2_KEY_ID" AWS_SECRET_ACCESS_KEY="$B2_APP_KEY" \
   aws s3 ls "s3://${B2_BUCKET}/" --endpoint-url "$B2_ENDPOINT"
   ```

2. Descargar el archivo elegido:

   ```bash
   AWS_ACCESS_KEY_ID="$B2_KEY_ID" AWS_SECRET_ACCESS_KEY="$B2_APP_KEY" \
   aws s3 cp "s3://${B2_BUCKET}/backup_YYYYMMDD_HHMM.sql" ./backup.sql \
   --endpoint-url "$B2_ENDPOINT"
   ```

3. Restaurar el archivo en PostgreSQL:

   ```bash
   psql "$DB_URL" --file=./backup.sql
   ```

4. Verificar que los datos volvieron:

   ```sql
   SELECT COUNT(*) FROM producto;
   ```

Si el respaldo contiene sentencias de creación o limpieza de tablas, revisar su contenido antes de ejecutarlo. En una restauración sobre una base existente, realizar primero una copia adicional para poder deshacer la prueba.

## 8. Comparación de frecuencias

| Frecuencia | Ventajas | Desventajas | Casos de uso |
|---|---|---|---|
| Cada 5 minutos | Reduce al mínimo la pérdida potencial de datos; permite recuperar casi cualquier cambio reciente. | Consume más CPU, almacenamiento y operaciones; aumenta el costo y la cantidad de archivos. | Transacciones críticas, demostraciones de alta disponibilidad y pruebas de recuperación frecuentes. |
| Cada 1 hora | Equilibra protección, costo y complejidad; es fácil de automatizar y monitorear. | Puede perderse hasta una hora de cambios si ocurre una falla antes del siguiente respaldo. | Aplicaciones con actividad continua y datos que cambian durante todo el día. |
| Cada mañana | Es simple, económico y suficiente para datos que cambian poco. | Puede perderse casi un día de trabajo; la recuperación es menos actual. | Catálogos, prototipos, aplicaciones académicas o sistemas con cambios concentrados durante el día. |

## 9. Conclusión

La frecuencia adecuada depende del valor de los datos y de la pérdida máxima aceptable. Para una demostración académica conviene documentar las tres expresiones y probar al menos una restauración. En producción, una frecuencia cada hora ofrece un equilibrio razonable para esta aplicación, mientras que cada 5 minutos se justifica cuando el costo de perder cambios recientes es alto. La frecuencia diaria es válida cuando los datos no son críticos o cambian poco.