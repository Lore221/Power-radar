import { readFile, writeFile } from "node:fs/promises";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const scriptDirectory = dirname(fileURLToPath(import.meta.url));
const projectDirectory = dirname(scriptDirectory);
const modelDirectory = join(
  projectDirectory,
  "src",
  "main",
  "resources",
  "assets",
  "power_radar",
  "models",
  "block",
);

for (const orientation of ["horizontal", "vertical"]) {
  const sourcePath = join(modelDirectory, `radar_link_${orientation}_for_editing.json`);
  const targetPath = join(modelDirectory, `radar_link_${orientation}.json`);
  const source = JSON.parse(await readFile(sourcePath, "utf8"));
  const bulbGroup = source.groups?.find(group => group.name === "bulb");

  if (!bulbGroup) {
    throw new Error(`В ${sourcePath} отсутствует группа bulb`);
  }

  const bulbIndices = new Set(bulbGroup.children);
  const baseElements = source.elements.filter((_, index) => !bulbIndices.has(index));
  const bulbElements = source.elements.filter((_, index) => bulbIndices.has(index));
  const textures = source.textures;

  // Blockbench редактирует цельную модель, а рабочий composite разделяет
  // непрозрачный корпус и полупрозрачные колпаки по разным RenderType.
  const runtimeModel = {
    format_version: source.format_version,
    credit: source.credit,
    loader: "neoforge:composite",
    parent: "block/block",
    ambientocclusion: false,
    texture_size: source.texture_size,
    textures: {
      particle: textures.particle,
    },
    children: {
      base: {
        render_type: "minecraft:cutout",
        textures,
        elements: baseElements,
      },
      bulbs: {
        render_type: "minecraft:translucent",
        textures,
        elements: bulbElements,
      },
    },
    ...(source.display ? { display: source.display } : {}),
  };

  await writeFile(targetPath, `${JSON.stringify(runtimeModel, null, 2)}\n`, "utf8");
}
