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
  "radar_link",
);

function effectElement(element, expanded) {
  const from = element.from.map(value => expanded ? value - 0.5 : value);
  const to = element.to.map(value => expanded ? value + 0.5 : value);
  const faces = Object.fromEntries(Object.entries(element.faces).map(([direction, face]) => [
    direction,
    { ...face, texture: "#0", shade: false },
  ]));

  return { from, to, shade: false, faces };
}

function effectModel(source, element, expanded) {
  return {
    ambientocclusion: false,
    texture_size: source.texture_size,
    textures: {
      0: source.textures[0] ?? source.textures[1],
    },
    elements: [effectElement(element, expanded)],
  };
}

for (const orientation of ["horizontal", "vertical"]) {
  const sourcePath = join(modelDirectory, `${orientation}_for_editing.json`);
  const targetPath = join(modelDirectory, `${orientation}.json`);
  const source = JSON.parse(await readFile(sourcePath, "utf8"));
  const bulbGroup = source.groups?.find(group => group.name === "bulb");

  if (!bulbGroup) {
    throw new Error(`В ${sourcePath} отсутствует группа bulb`);
  }

  const bulbIndices = new Set(bulbGroup.children);
  const baseElements = source.elements.filter((_, index) => !bulbIndices.has(index));
  const bulbElements = source.elements.filter((_, index) => bulbIndices.has(index));
  const textures = source.textures;
  const redBulb = bulbElements.find(element => element.name === "bulb 1");
  const greenBulb = bulbElements.find(element => element.name === "bulb 2");

  if (!redBulb || !greenBulb || redBulb.from[0] >= greenBulb.from[0]) {
    throw new Error(`В ${sourcePath} красная колба должна находиться левее зелёной`);
  }

  // Blockbench редактирует цельную модель, а рабочий composite отделяет
  // полупрозрачные колбы от непрозрачного корпуса.
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

  for (const [color, bulb] of [["red", redBulb], ["green", greenBulb]]) {
    const tubePath = join(modelDirectory, `tube_${orientation}_${color}.json`);
    const glowPath = join(modelDirectory, `glow_${orientation}_${color}.json`);
    await writeFile(
      tubePath,
      `${JSON.stringify(effectModel(source, bulb, false), null, 2)}\n`,
      "utf8",
    );
    await writeFile(
      glowPath,
      `${JSON.stringify(effectModel(source, bulb, true), null, 2)}\n`,
      "utf8",
    );
  }
}
