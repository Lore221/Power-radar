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


def canvas(color):
    return [[rgba(color) for _ in range(16)] for _ in range(16)]


def rect(img, x0, y0, x1, y1, color):
    color = rgba(color)
    for y in range(max(0, y0), min(16, y1)):
        for x in range(max(0, x0), min(16, x1)):
            img[y][x] = color


def line_h(img, y, x0, x1, color):
    rect(img, x0, y, x1, y + 1, color)


def line_v(img, x, y0, y1, color):
    rect(img, x, y0, x + 1, y1, color)


def frame(img, outer, inner, color):
    x0, y0, x1, y1 = outer
    ix0, iy0, ix1, iy1 = inner
    rect(img, x0, y0, x1, y1, color)
    rect(img, ix0, iy0, ix1, iy1, "#00000000")


def controller_front():
    img = canvas("#302d28")
    rect(img, 1, 1, 15, 15, "#4a4a42")
    rect(img, 3, 3, 13, 9, "#272620")
    rect(img, 4, 4, 12, 8, "#32362c")
    rect(img, 5, 5, 7, 7, "#c68b42")
    rect(img, 9, 5, 11, 7, "#d4b15d")
    for y in (11, 13):
        line_h(img, y, 3, 13, "#8c99a3")
    rect(img, 1, 1, 15, 2, "#59636d")
    return img


def controller_side():
    img = canvas("#302d28")
    rect(img, 1, 1, 15, 15, "#4a4a42")
    for y in (4, 7, 10, 13):
        line_h(img, y, 3, 13, "#151b21")
        line_h(img, y + 1, 4, 12, "#4b5661")
    return img


def controller_top():
    img = canvas("#35322c")
    rect(img, 1, 1, 15, 15, "#55544b")
    rect(img, 3, 3, 13, 13, "#302d28")
    line_h(img, 5, 4, 12, "#c68b42")
    line_v(img, 8, 5, 12, "#d4b15d")
    rect(img, 6, 10, 10, 12, "#7f8a72")
    return img


def monitor_front():
    img = canvas("#302d28")
    rect(img, 1, 1, 15, 15, "#55544b")
    rect(img, 2, 2, 14, 14, "#25231e")
    rect(img, 3, 3, 13, 13, "#273026")
    line_h(img, 4, 4, 12, "#6f7b65")
    line_h(img, 8, 4, 12, "#515d4e")
    line_v(img, 8, 4, 12, "#515d4e")
    rect(img, 12, 12, 14, 14, "#c68b42")
    return img


def monitor_side():
    img = canvas("#302d28")
    rect(img, 1, 1, 15, 15, "#4a4a42")
    rect(img, 3, 2, 13, 5, "#68675c")
    rect(img, 3, 11, 13, 14, "#25231e")
    for x in (5, 8, 11):
        line_v(img, x, 6, 10, "#8a8777")
    return img


def monitor_back():
    img = canvas("#302d28")
    rect(img, 1, 1, 15, 15, "#4a4a42")
    rect(img, 4, 4, 12, 12, "#25231e")
    line_h(img, 6, 5, 11, "#8a8777")
    line_h(img, 9, 5, 11, "#8a8777")
    rect(img, 7, 13, 9, 15, "#c68b42")
    return img


def link_front():
    img = canvas("#00000000")
    rect(img, 2, 2, 14, 14, "#302d28")
    rect(img, 3, 3, 13, 13, "#625f54")
    rect(img, 5, 5, 11, 11, "#2f3029")
    rect(img, 7, 4, 9, 6, "#c68b42")
    rect(img, 6, 7, 10, 9, "#d4b15d")
    rect(img, 4, 12, 6, 14, "#8a5a3b")
    rect(img, 10, 12, 12, 14, "#8a5a3b")
    line_h(img, 2, 4, 12, "#8a8777")
    return img


def link_side():
    img = canvas("#00000000")
    rect(img, 2, 2, 14, 14, "#302d28")
    rect(img, 3, 3, 13, 13, "#4a4a42")
    line_h(img, 5, 4, 12, "#25231e")
    line_h(img, 9, 4, 12, "#25231e")
    rect(img, 6, 12, 10, 14, "#c68b42")
    return img


def link_back():
    img = canvas("#00000000")
    rect(img, 2, 2, 14, 14, "#302d28")
    rect(img, 4, 4, 12, 12, "#25231e")
    rect(img, 6, 6, 10, 10, "#8a8777")
    return img


def panel_front():
    img = canvas("#302d28")
    rect(img, 1, 1, 15, 15, "#625f54")
    rect(img, 2, 2, 14, 14, "#2f3029")
    for x in (4, 8, 12):
        line_v(img, x, 2, 14, "#8a8777")
    for y in (4, 8, 12):
        line_h(img, y, 2, 14, "#8a8777")
    for y in (3, 7, 11):
        for x in (3, 7, 11):
            rect(img, x, y, x + 2, y + 2, "#c68b42")
    rect(img, 1, 1, 15, 2, "#a9a28d")
    return img


def panel_back():
    img = canvas("#302d28")
    rect(img, 1, 1, 15, 15, "#4a4a42")
    line_v(img, 3, 2, 14, "#8a8777")
    line_v(img, 12, 2, 14, "#8a8777")
    line_h(img, 8, 2, 14, "#8a8777")
    rect(img, 6, 6, 10, 10, "#25231e")
    return img


def panel_edge():
    img = canvas("#302d28")
    rect(img, 0, 0, 16, 16, "#4a4a42")
    for y in range(0, 16, 4):
        line_h(img, y, 0, 16, "#8a8777")
    return img


def advanced_panel_front():
    img = canvas("#10161a")
    rect(img, 1, 1, 15, 15, "#2c3740")
    rect(img, 2, 2, 14, 14, "#111d24")
    rect(img, 3, 3, 13, 13, "#18313a")
    for x in (5, 10):
        line_v(img, x, 3, 13, "#49d8d0")
    for y in (5, 10):
        line_h(img, y, 3, 13, "#49d8d0")
    rect(img, 6, 6, 10, 10, "#d6f7ff")
    rect(img, 7, 7, 9, 9, "#3ff0c8")
    rect(img, 1, 1, 15, 2, "#7ce7ff")
    rect(img, 2, 14, 14, 15, "#0b0f12")
    for x, y in ((3, 3), (11, 3), (3, 11), (11, 11)):
        rect(img, x, y, x + 2, y + 2, "#d7b15a")
    return img


def advanced_panel_back():
    img = canvas("#10161a")
    rect(img, 1, 1, 15, 15, "#27323a")
    rect(img, 3, 3, 13, 13, "#10181d")
    line_v(img, 4, 2, 14, "#49d8d0")
    line_v(img, 11, 2, 14, "#49d8d0")
    line_h(img, 4, 2, 14, "#49d8d0")
    line_h(img, 11, 2, 14, "#49d8d0")
    rect(img, 6, 6, 10, 10, "#d7b15a")
    rect(img, 7, 7, 9, 9, "#2d3940")
    return img


def advanced_panel_edge():
    img = canvas("#10161a")
    rect(img, 0, 0, 16, 16, "#27323a")
    for y in range(1, 16, 4):
        line_h(img, y, 0, 16, "#49d8d0")
    for y in range(3, 16, 4):
        line_h(img, y, 0, 16, "#d7b15a")
    return img


def mount_top():
    img = canvas("#302d28")
    rect(img, 1, 1, 15, 15, "#55544b")
    rect(img, 4, 4, 12, 12, "#25231e")
    rect(img, 6, 6, 10, 10, "#8a8777")
    for x, y in ((2, 2), (12, 2), (2, 12), (12, 12)):
        rect(img, x, y, x + 2, y + 2, "#c68b42")
    return img


def mount_side():
    img = canvas("#302d28")
    rect(img, 1, 10, 15, 15, "#625f54")
    rect(img, 5, 3, 11, 11, "#4a4a42")
    rect(img, 6, 1, 10, 4, "#8a8777")
    line_h(img, 9, 3, 13, "#a9a28d")
    return img


def core_front():
    img = canvas("#302d28")
    rect(img, 1, 1, 15, 15, "#55544b")
    rect(img, 3, 3, 13, 13, "#2f3029")
    rect(img, 6, 6, 10, 10, "#c68b42")
    line_h(img, 4, 5, 11, "#d4b15d")
    line_h(img, 11, 5, 11, "#d4b15d")
    line_v(img, 4, 5, 11, "#d4b15d")
    line_v(img, 11, 5, 11, "#d4b15d")
    return img


def core_side():
    img = canvas("#302d28")
    rect(img, 1, 1, 15, 15, "#4a4a42")
    for y in (3, 7, 11):
        rect(img, 3, y, 13, y + 2, "#25231e")
        rect(img, 4, y, 6, y + 2, "#c68b42")
        rect(img, 10, y, 12, y + 2, "#7f8a72")
    return img


def core_top():
    img = canvas("#3c3932")
    rect(img, 1, 1, 15, 15, "#625f54")
    rect(img, 3, 3, 13, 13, "#302d28")
    line_h(img, 8, 3, 13, "#8a8777")
    line_v(img, 8, 3, 13, "#8a8777")
    rect(img, 7, 7, 9, 9, "#c68b42")
    return img


TEXTURES = {
    "radar_controller_front.png": controller_front,
    "radar_controller_side.png": controller_side,
    "radar_controller_top.png": controller_top,
    "radar_controller_bottom.png": controller_side,
    "radar_monitor_front.png": monitor_front,
    "radar_monitor_side.png": monitor_side,
    "radar_monitor_top.png": monitor_side,
    "radar_monitor_back.png": monitor_back,
    "radar_link_front.png": link_front,
    "radar_link_side.png": link_side,
    "radar_link_back.png": link_back,
    "radar_panel_front.png": panel_front,
    "radar_panel_back.png": panel_back,
    "radar_panel_edge.png": panel_edge,
    "advanced_radar_panel_front.png": advanced_panel_front,
    "advanced_radar_panel_back.png": advanced_panel_back,
    "advanced_radar_panel_edge.png": advanced_panel_edge,
    "fixed_antenna_mount_top.png": mount_top,
    "fixed_antenna_mount_side.png": mount_side,
    "antenna_array_core_front.png": core_front,
    "antenna_array_core_side.png": core_side,
    "antenna_array_core_top.png": core_top,
}


def main():
    os.makedirs(TEXTURE_DIR, exist_ok=True)
    for name, factory in TEXTURES.items():
        write_png(os.path.join(TEXTURE_DIR, name), factory())


if __name__ == "__main__":
    main()
