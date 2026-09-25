import json
import os
import subprocess
from pathlib import Path

from google.oauth2 import service_account
from googleapiclient.discovery import build
from googleapiclient.http import MediaFileUpload


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


def create_dump(path):
    subprocess.run(
        ["pg_dump", "--dbname", require_environment("DATABASE_URL"), "--file", str(path)],
        check=True,
    )


def upload_or_update(drive, path):
    folder_id = require_environment("GDRIVE_FOLDER_ID")
    query = (
        f"'{folder_id}' in parents and trashed = false "
        f"and name = '{BACKUP_NAME}'"
    )
    matches = drive.files().list(q=query, fields="files(id)", pageSize=10).execute()["files"]
    media = MediaFileUpload(str(path), mimetype="application/sql", resumable=True)

    if matches:
        return drive.files().update(
            fileId=matches[0]["id"], media_body=media, fields="id,webViewLink"
        ).execute()

    metadata = {"name": BACKUP_NAME, "parents": [folder_id]}
    return drive.files().create(
        body=metadata, media_body=media, fields="id,webViewLink"
    ).execute()


def backup():
    path = Path(BACKUP_NAME)
    try:
        create_dump(path)
        result = upload_or_update(drive_client(), path)
        print(f"Respaldo actualizado: {result.get('webViewLink', result['id'])}")
    finally:
        path.unlink(missing_ok=True)


if __name__ == "__main__":
    backup()