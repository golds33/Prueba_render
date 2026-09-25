# Configuración en Render

## Variables de entorno

En el servicio web de Render, abre **Environment** y agrega:

- `DATABASE_URL`: la URL interna o externa de PostgreSQL de Render.
- `GDRIVE_FOLDER_ID`: el ID de la carpeta destino de Google Drive.
- `GOOGLE_CREDENTIALS_JSON`: el JSON completo de la cuenta de servicio de Google Cloud, en una sola variable.

Comparte la carpeta de Drive con el correo `client_email` que aparece dentro de `GOOGLE_CREDENTIALS_JSON` y dale permiso de **Editor**.

La aplicación Java expone `GET /api/cron-backup`, `POST /api/delete-db` y `POST /api/restore-db`. El cron de Render puede llamar al primer endpoint con una tarea programada.

Los scripts `backup_gdrive.py` y `restore_gdrive.py` son una alternativa para un Render Cron Job basado en Python. Instala `requirements.txt` y asegúrate de que el entorno tenga los binarios `pg_dump` y `psql` disponibles.

No guardes el JSON en el repositorio. Configúralo únicamente como variable secreta en Render.