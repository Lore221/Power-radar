import { readFile, mkdir, writeFile } from "node:fs/promises";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const scriptDirectory = dirname(fileURLToPath(import.meta.url));
const projectDirectory = resolve(scriptDirectory, "..");
const sourcePath = resolve(
    projectDirectory,
    "src/main/resources/assets/power_radar/models/block/radar_display.json"
);
const outputDirectory = resolve(
    projectDirectory,
    "src/main/resources/assets/power_radar/models/block/radar_display"
);
const blockstatePath = resolve(
    projectDirectory,
    "src/main/resources/assets/power_radar/blockstates/radar_display.json"
);
const MODEL_Z_OFFSET_PIXELS = 3;

const SHAPES = {
    single: ["top", "bottom", "left", "right"],
    center: [],
    top: ["top"],
    bottom: ["bottom"],
    left: ["left"],
    right: ["right"],
    top_left: ["top", "left"],
    top_right: ["top", "right"],
    bottom_left: ["bottom", "left"],
    bottom_right: ["bottom", "right"],
    vertical: ["left", "right"],
    vertical_top: ["top", "left", "right"],
    vertical_bottom: ["bottom", "left", "right"],
    horizontal: ["top", "bottom"],
    horizontal_left: ["top", "bottom", "left"],
    horizontal_right: ["top", "bottom", "right"]
};

const OUTER_FACE = {
    top: "up",
    bottom: "down",
    // В исходной north-facing модели экранная левая сторона находится у X=16.
    left: "east",
    right: "west"
};

const CORNER_EDGES = {
    top_left: ["top", "left"],
    top_right: ["top", "right"],
    bottom_left: ["bottom", "left"],
    bottom_right: ["bottom", "right"]
};

const source = JSON.parse(await readFile(sourcePath, "utf8"));
const elementsByName = new Map(source.elements.map(element => [element.name, element]));
for (const requiredName of ["body", "top", "bottom", "left", "right", ...Object.keys(CORNER_EDGES)]) {
    if (!elementsByName.has(requiredName)) {
        throw new Error(`Radar display source model is missing element '${requiredName}'`);
    }
}

await mkdir(outputDirectory, { recursive: true });
for (const [shapeName, edges] of Object.entries(SHAPES)) {
    const presentEdges = new Set(edges);
    const model = structuredClone(source);
    shiftElementsAlongZ(model, MODEL_Z_OFFSET_PIXELS);
    model.elements = model.elements
        .filter(element => shouldKeepElement(element.name, presentEdges))
        .map(element => removeInternalOuterFaces(element, presentEdges))
        .map(element => normalizeConnectedTopFrame(element, presentEdges));
    await writeFile(
        resolve(outputDirectory, `${shapeName}.json`),
        `${formatLikeBlockbench(model)}\n`,
        "utf8"
    );
}

const multipart = [];
for (const [facing, rotation] of Object.entries({ north: 0, east: 90, south: 180, west: 270 })) {
    for (const shapeName of Object.keys(SHAPES)) {
        const apply = { model: `power_radar:block/radar_display/${shapeName}` };
        if (rotation !== 0) {
            apply.y = rotation;
        }
        multipart.push({
            when: { facing, frame_shape: shapeName },
            apply
        });
    }
}
await writeFile(blockstatePath, `${JSON.stringify({ multipart }, null, 2)}\n`, "utf8");

function shiftElementsAlongZ(model, offset) {
    for (const element of model.elements) {
        element.from[2] += offset;
        element.to[2] += offset;
        if (element.rotation?.origin !== undefined) {
            element.rotation.origin[2] += offset;
        }
    }
}

function shouldKeepElement(name, presentEdges) {
    if (name === "body") {
        return true;
    }
    if (Object.hasOwn(OUTER_FACE, name)) {
        return presentEdges.has(name);
    }
    const cornerEdges = CORNER_EDGES[name];
    return cornerEdges !== undefined && cornerEdges.some(edge => presentEdges.has(edge));
}

function removeInternalOuterFaces(element, presentEdges) {
    const copy = structuredClone(element);
    if (copy.name !== "body" || copy.faces === undefined) {
        return copy;
    }
    for (const [edge, face] of Object.entries(OUTER_FACE)) {
        if (!presentEdges.has(edge)) {
            delete copy.faces[face];
        }
    }
    return copy;
}

function normalizeConnectedTopFrame(element, presentEdges) {
    const hasHorizontalConnection = !presentEdges.has("left") || !presentEdges.has("right");
    if (!presentEdges.has("top") || !hasHorizontalConnection || element.faces?.up === undefined) {
        return element;
    }

    const copy = structuredClone(element);
    if (copy.name === "top_right") {
        copy.faces.up.uv = [16, 5, 14, 7];
        delete copy.faces.up.rotation;
    } else if (copy.name === "top_left") {
        copy.faces.up.uv = [2, 5, 0, 7];
        delete copy.faces.up.rotation;
    }
    return copy;
}

function formatLikeBlockbench(model) {
    return JSON.stringify(model, null, "\t")
        .replace(
            /\[\s*(-?\d+(?:\.\d+)?(?:\s*,\s*-?\d+(?:\.\d+)?)+)\s*\]/g,
            (_, values) => `[${values.replace(/\s+/g, " ")}]`
        )
        .replace(
            /\{\s*"angle": ([^,\n]+),\s*"axis": ([^,\n]+),\s*"origin": (\[[^\n]+\])\s*\}/g,
            (_, angle, axis, origin) => `{"angle": ${angle}, "axis": ${axis}, "origin": ${origin}}`
        )
        .replace(
            /\{\s*"uv": (\[[^\n]+\]),\s*(?:"rotation": ([^,\n]+),\s*)?"texture": ([^\n]+)\s*\}/g,
            (_, uv, rotation, texture) => rotation === undefined
                ? `{"uv": ${uv}, "texture": ${texture}}`
                : `{"uv": ${uv}, "rotation": ${rotation}, "texture": ${texture}}`
        );
}
