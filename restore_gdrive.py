import json
import os
import subprocess
import tempfile
from pathlib import Path

from google.oauth2 import service_account
from googleapiclient.discovery import build


BACKUP_NAME = "backup_latest.sql"
SCOPES = ["https://www.googleapis.com/auth/drive"]


def require_environment(name):
    value = os.environ.get(name, "").strip()
    if not value:
        raise RuntimeError(f"Falta la variable de entorno {name}.")
    return value


def drive_client():
    credentials = service_account.Credentials.from_service_account_info(
        json.loads(require_environment("GOOGLE_CREDENTIALS_JSON")),
        scopes=SCOPES,
    )
    return build("drive", "v3", credentials=credentials, cache_discovery=False)


def download_backup(drive, path):
    folder_id = require_environment("GDRIVE_FOLDER_ID")
    query = (
        f"'{folder_id}' in parents and trashed = false "
        f"and name = '{BACKUP_NAME}'"
    )
    files = drive.files().list(q=query, fields="files(id)", pageSize=1).execute()["files"]
    if not files:
        raise RuntimeError("No existe backup_latest.sql en Google Drive.")

    with path.open("wb") as output:
        request = drive.files().get(fileId=files[0]["id"], alt="media")
        from googleapiclient.http import MediaIoBaseDownload

        downloader = MediaIoBaseDownload(output, request)
        finished = False
        while not finished:
            _, finished = downloader.next_chunk()


def restore():
    database_url = require_environment("DATABASE_URL")
    with tempfile.NamedTemporaryFile(prefix="restore-", suffix=".sql", delete=False) as temporary:
        path = Path(temporary.name)
    try:
        download_backup(drive_client(), path)
        subprocess.run(
            [
                "psql",
                "--dbname",
                database_url,
                "--command",
                "DROP SCHEMA IF EXISTS public CASCADE; CREATE SCHEMA public;",
            ],
            check=True,
        )
        subprocess.run(
            ["psql", "--dbname", database_url, "--file", str(path)],
            check=True,
        )
        print("Base de datos restaurada desde Google Drive.")
    finally:
        path.unlink(missing_ok=True)


if __name__ == "__main__":
    restore()