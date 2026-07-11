import os
import struct
import zlib


ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
TEXTURE_DIR = os.path.join(ROOT, "src", "main", "resources", "assets", "power_radar", "textures", "block")


def rgba(hex_color):
    hex_color = hex_color.lstrip("#")
    if len(hex_color) == 6:
        hex_color += "ff"
    return tuple(int(hex_color[i:i + 2], 16) for i in range(0, 8, 2))


def write_png(path, pixels):
    height = len(pixels)
    width = len(pixels[0])
    raw = bytearray()
    for row in pixels:
        raw.append(0)
        for pixel in row:
            raw.extend(pixel)

    def chunk(kind, data):
        return (
            struct.pack(">I", len(data))
            + kind
            + data
            + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)
        )

    data = b"\x89PNG\r\n\x1a\n"
    data += chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
    data += chunk(b"IDAT", zlib.compress(bytes(raw), 9))
    data += chunk(b"IEND", b"")
    with open(path, "wb") as handle:
        handle.write(data)


def canvas(size, color):
    return [[rgba(color) for _ in range(size)] for _ in range(size)]


def rect(img, x0, y0, x1, y1, color):
    color = rgba(color)
    size = len(img)
    for y in range(max(0, y0), min(size, y1)):
        for x in range(max(0, x0), min(size, x1)):
            img[y][x] = color


def line_h(img, y, x0, x1, color):
    rect(img, x0, y, x1, y + 1, color)


def line_v(img, x, y0, y1, color):
    rect(img, x, y0, x + 1, y1, color)


def controller_front():
    img = canvas(16, "#302d28")
    rect(img, 1, 1, 15, 15, "#5b5a50")
    rect(img, 3, 3, 13, 13, "#282822")
    rect(img, 4, 4, 12, 8, "#363a30")
    rect(img, 5, 5, 7, 7, "#c68b42")
    rect(img, 9, 5, 11, 7, "#d4b15d")
    line_h(img, 10, 4, 12, "#8a8777")
    line_h(img, 12, 4, 12, "#4b5661")
    return img


def controller_side():
    img = canvas(16, "#302d28")
    rect(img, 1, 1, 15, 15, "#4a4a42")
    for y in (4, 7, 10, 13):
        line_h(img, y, 3, 13, "#1b1d1a")
        line_h(img, y + 1, 4, 12, "#777365")
    rect(img, 6, 2, 10, 5, "#8a5a3b")
    return img


def controller_top():
    img = canvas(16, "#35322c")
    rect(img, 1, 1, 15, 15, "#625f54")
    rect(img, 3, 3, 13, 13, "#302d28")
    line_h(img, 5, 4, 12, "#c68b42")
    line_v(img, 8, 5, 12, "#d4b15d")
    rect(img, 6, 10, 10, 12, "#7f8a72")
    return img


def display_active():
    img = canvas(32, "#20221e")
    rect(img, 2, 2, 30, 30, "#30372d")
    for x in (8, 16, 24):
        line_v(img, x, 3, 29, "#445044")
    for y in (8, 16, 24):
        line_h(img, y, 3, 29, "#445044")
    rect(img, 13, 13, 19, 19, "#5f735d")
    return img


def display_off():
    img = canvas(32, "#24231f")
    rect(img, 2, 2, 30, 30, "#2f3029")
    for x in (8, 16, 24):
        line_v(img, x, 3, 29, "#3d3b34")
    for y in (8, 16, 24):
        line_h(img, y, 3, 29, "#3d3b34")
    return img


def display_edge():
    img = canvas(32, "#4a4a42")
    rect(img, 0, 0, 32, 32, "#625f54")
    rect(img, 3, 3, 29, 29, "#8a8777")
    rect(img, 5, 5, 27, 27, "#4a4a42")
    return img


def display_back():
    img = canvas(32, "#302d28")
    rect(img, 2, 2, 30, 30, "#4a4a42")
    rect(img, 8, 8, 24, 24, "#25231e")
    line_h(img, 12, 9, 23, "#8a8777")
    line_h(img, 20, 9, 23, "#8a8777")
    return img


TEXTURES = {
    "radar_monitor_controller_front.png": controller_front,
    "radar_monitor_controller_side.png": controller_side,
    "radar_monitor_controller_top.png": controller_top,
    "radar_monitor_controller_bottom.png": controller_side,
    "radar_display_active.png": display_active,
    "radar_display_off.png": display_off,
    "radar_display_edge.png": display_edge,
    "radar_display_back.png": display_back,
}


def main():
    os.makedirs(TEXTURE_DIR, exist_ok=True)
    for name, factory in TEXTURES.items():
        write_png(os.path.join(TEXTURE_DIR, name), factory())


if __name__ == "__main__":
    main()
