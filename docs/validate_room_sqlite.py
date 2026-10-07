"""Validation SQLite des SQL Room exportés, sans appareil Android."""
import json
import re
import sqlite3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SCHEMAS = ROOT / "data/schemas/dev.endlesssea.data.db.EsDatabase"
old = json.loads((SCHEMAS / "7.json").read_text())["database"]
new = json.loads((SCHEMAS / "8.json").read_text())["database"]
actual_db, expected_db = sqlite3.connect(":memory:"), sqlite3.connect(":memory:")
for table in old["entities"]:
    actual_db.execute(table["createSql"].replace("${TABLE_NAME}", table["tableName"]))
    for index in table["indices"]:
        actual_db.execute(index["createSql"].replace("${TABLE_NAME}", table["tableName"]))
actual_db.execute("""INSERT INTO tracker_links
    (mediaId,service,remoteId,title,totalEpisodes,progress,status,lastEpisodeId,pendingSync,updatedAt)
    VALUES ('show','ANILIST','42','Title',12,3,'WATCHING','ep3',1,123)""")
source = (ROOT / "app/src/main/java/dev/endlesssea/app/di/AppModule.kt").read_text()
block = source.split("private val MIGRATION_7_8")[1].split("@Provides")[0]
for sql in re.findall(r'db.execSQL\("([^"]+)"\)', block):
    actual_db.execute(sql)
for table in new["entities"]:
    name = table["tableName"]
    expected_db.execute(table["createSql"].replace("${TABLE_NAME}", name))
    actual = {row[1]: row[2:] for row in actual_db.execute(f"PRAGMA table_info({name})")}
    expected = {row[1]: row[2:] for row in expected_db.execute(f"PRAGMA table_info({name})")}
    assert actual == expected, (name, actual, expected)
assert actual_db.execute("SELECT progress,pendingSync,autoMatchEpisodes,autoMatchSeason FROM tracker_links").fetchone() == (3, 1, 0, None)
actual_db.execute("""INSERT INTO watch_history (episodeId,mediaId,positionMs,durationMs,watched,updatedAt)
    VALUES ('ep','local:folder',0,0,1,123)""")
dao = (ROOT / "data/src/main/java/dev/endlesssea/data/db/Daos.kt").read_text()
sql = re.search(r'@Query\("(DELETE FROM watch_history WHERE watched = 1[^"]+)"\)', dao).group(1)
assert actual_db.execute(sql).rowcount == 1
print("SQLite : schéma v7→v8, préservation du rattachement et suppression des vus manuels validés.")
print("Ceci ne remplace pas un test instrumenté Android/Room.")
