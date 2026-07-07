// Dev-only styling preview: renders a canned report of real finding shapes so the report view
// can be inspected without uploading anything. Not part of the production bundle.
import "@fontsource/chakra-petch/600.css";
import "@fontsource/chakra-petch/700.css";
import "@fontsource/ibm-plex-sans/400.css";
import "@fontsource/ibm-plex-sans/500.css";
import "@fontsource/ibm-plex-sans/600.css";
import "@fontsource/ibm-plex-mono/400.css";
import "@fontsource/ibm-plex-mono/500.css";
import "./styles.css";

import { renderReport } from "./render";
import type { Report } from "./types";

const theme = new URLSearchParams(location.search).get("theme");
if (theme) document.documentElement.dataset.theme = theme;

const sample: Report = {
  jars: 100,
  fabricMods: 97,
  minecraftVersion: "1.20.1",
  findings: [
    {
      type: "missing-dependency",
      severity: "HIGH",
      mods: ["iris", "sodium"],
      problem: "Iris requires sodium [0.5.x], but no installed mod provides it.",
      fix: "Install sodium in a version matching [0.5.x].",
    },
    {
      type: "resource-override",
      severity: "MEDIUM",
      mods: ["displaydelight", "kilt", "lootr"],
      problem:
        "displaydelight, kilt, lootr ship 1 identical resource file (assets/minecraft/atlases/blocks.json); whichever loads last silently wins.",
      fix: "Check the files match intent; if the override is deliberate, suppress this finding.",
    },
    {
      type: "access-widener-conflict",
      severity: "LOW",
      mods: ["seasons", "kilt", "supplementaries"],
      problem:
        "Several mods widen 'field net/minecraft/class_793 field_4251 Ljava/util/Map;' divergently: kilt widens it as accessible+mutable, seasons widens it as mutable, supplementaries widens it as accessible.",
      fix: "Usually harmless (widenings are additive), but verify all 3 mods behave; then suppress.",
    },
    {
      type: "mixin-overlap",
      severity: "POTENTIAL",
      mods: ["amendments", "immersive_weathering", "modernfix"],
      problem:
        "Intrusive Mixin injections from several mods hit net.minecraft.class_2246#<clinit>: amendments @Redirect, immersive_weathering @Redirect, modernfix @Redirect. Whichever applies last wins, and the rest may misbehave.",
      fix: "Test these 3 mods together in game; if they coexist fine, suppress this finding via the ignore file.",
    },
  ],
};

const root = document.getElementById("report") as HTMLElement;
if (new URLSearchParams(location.search).get("clear") === "1") {
  renderReport(root, { jars: 100, fabricMods: 97, minecraftVersion: "1.20.1", findings: [] });
} else {
  renderReport(root, sample);
  document.querySelector("details.finding")?.setAttribute("open", "");
}
