#!/usr/bin/env python3
"""Migrate the legacy per-album SQLite databases into the unified MySQL schema."""

import argparse
import sqlite3
from pathlib import Path

import pymysql

ALBUMS = {
    "peach": "peach.db",
    "wuhu": "wuhu.db",
    "peachwuhu": "peachwuhu.db",
    "pingpang": "pingpang.db",
    "qita": "qita.db",
    "shushu": "shushu.db",
    "wangzhe": "wangzhe.db",
    "zhengshu": "zhengshu.db",
}


def normalize(path):
    value = (path or "").replace("\\", "/").lstrip("/")
    return value[len("photos/"):] if value.startswith("photos/") else value


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=3307)
    parser.add_argument("--database", default="peachwuhu_dev")
    parser.add_argument("--user", default="root")
    parser.add_argument("--password", required=True)
    parser.add_argument("--existing-files-only", action="store_true")
    args = parser.parse_args()
    photos = args.root / "photos"
    mysql = pymysql.connect(
        host=args.host, port=args.port, user=args.user, password=args.password,
        database=args.database, charset="utf8mb4", autocommit=False
    )
    imported_days = imported_images = 0
    try:
        with mysql.cursor() as cursor:
            cursor.execute("SET FOREIGN_KEY_CHECKS=0")
            cursor.execute("TRUNCATE TABLE images")
            cursor.execute("TRUNCATE TABLE album_days")
            cursor.execute("TRUNCATE TABLE recycled_images")
            cursor.execute("SET FOREIGN_KEY_CHECKS=1")
            for album_key, db_name in ALBUMS.items():
                cursor.execute("SELECT id FROM albums WHERE album_key=%s", (album_key,))
                album_id = cursor.fetchone()[0]
                source = sqlite3.connect(str(args.root / db_name))
                source.row_factory = sqlite3.Row
                valid_dates = set()
                for image in source.execute("SELECT * FROM images ORDER BY date, sort_order, id"):
                    raw_path = normalize(image["raw_path"])
                    preview_path = normalize(image["preview_path"])
                    if args.existing_files_only and not (
                        (photos / raw_path).is_file() and (photos / preview_path).is_file()
                    ):
                        continue
                    filename = Path(raw_path).name
                    file_size = (photos / raw_path).stat().st_size if (photos / raw_path).is_file() else 0
                    cursor.execute(
                        """INSERT INTO images
                           (album_id,photo_date,raw_path,preview_path,original_filename,
                            sort_order,photo_time,is_cover,file_size)
                           VALUES(%s,%s,%s,%s,%s,%s,%s,%s,%s)""",
                        (album_id, image["date"], raw_path, preview_path, filename,
                         image["sort_order"] or 0, image["photo_time"] or "",
                         image["is_cover"] or 0, file_size)
                    )
                    valid_dates.add(image["date"])
                    imported_images += 1
                for day in source.execute("SELECT * FROM days ORDER BY date"):
                    if args.existing_files_only and day["date"] not in valid_dates:
                        continue
                    cursor.execute(
                        "INSERT INTO album_days(album_id,photo_date,info) VALUES(%s,%s,%s)",
                        (album_id, day["date"], day["info"])
                    )
                    imported_days += 1
                source.close()
            recycle_db = args.root / "recycle.db"
            if recycle_db.exists():
                source = sqlite3.connect(str(recycle_db))
                source.row_factory = sqlite3.Row
                for row in source.execute("SELECT * FROM recycled_images ORDER BY id"):
                    raw_path, preview_path = normalize(row["raw_path"]), normalize(row["preview_path"])
                    if args.existing_files_only and not (photos / raw_path).is_file():
                        continue
                    cursor.execute(
                        """INSERT INTO recycled_images
                           (origin_album_key,origin_date,filename,raw_path,preview_path,deleted_at)
                           VALUES(%s,%s,%s,%s,%s,%s)""",
                        (row["origin_user"], row["origin_date"], row["filename"],
                         raw_path, preview_path, row["deleted_at"])
                    )
                source.close()
        mysql.commit()
        print(f"migration complete: {imported_days} days, {imported_images} images")
    except Exception:
        mysql.rollback()
        raise
    finally:
        mysql.close()


if __name__ == "__main__":
    main()
