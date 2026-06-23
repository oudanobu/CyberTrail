import sqlite3
import struct
db = sqlite3.connect("android/app/src/main/assets/world.mbtiles")
# Total count
count = db.execute("SELECT COUNT(*) FROM tiles").fetchone()[0]
# Metadata
metadata = db.execute("SELECT name, value FROM metadata").fetchall()
# Length of 20 tiles
lengths = [r[0] for r in db.execute("SELECT LENGTH(tile_data) FROM tiles LIMIT 20").fetchall()]
# Extract z=0, x=0, y=0 tile_data
tile = db.execute("SELECT tile_data FROM tiles WHERE zoom_level=0 AND tile_column=0 AND tile_row=0").fetchone()
if tile:
    data = tile[0]
    with open("tile_0_0_0.png", "wb") as f:
        f.write(data)
    if data[:8] == b"\x89PNG\r\n\x1a\n" or data.startswith(b"\x89PNG"):
        w, h = struct.unpack(">II", data[16:24])
        img_ok = True
        detail = f"{w}x{h}"
    else:
        img_ok = False
        detail = "Not PNG format"
else:
    img_ok = False
    detail = "Tile z=0,x=0,y=0 not found"
print("COUNT:", count)
print("METADATA:", metadata)
print("LENGTHS:", lengths)
print("PNG_OK:", img_ok, detail)
