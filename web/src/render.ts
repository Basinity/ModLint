import type { Finding, Report, Severity } from "./types";

const SEVERITY_ORDER: Severity[] = ["HIGH", "MEDIUM", "LOW", "POTENTIAL"];

const SEVERITY_LABEL: Record<Severity, string> = {
  HIGH: "High",
  MEDIUM: "Medium",
  LOW: "Low",
  POTENTIAL: "Potential",
};

const SEVERITY_MEANING: Record<Severity, string> = {
  HIGH: "very likely to break the game",
  MEDIUM: "can misbehave or surprise you",
  LOW: "worth knowing, rarely breaking",
  POTENTIAL: "heuristic: an overlap is a risk, not a certainty",
};

export function renderReport(root: HTMLElement, report: Report): void {
  root.replaceChildren();
  root.append(summary(report));
  if (report.findings.length === 0) {
    root.append(allClear());
    return;
  }
  const bySeverity = new Map<Severity, Finding[]>();
  for (const finding of report.findings) {
    const group = bySeverity.get(finding.severity) ?? [];
    group.push(finding);
    bySeverity.set(finding.severity, group);
  }
  root.append(tiles(bySeverity, root));
  for (const severity of SEVERITY_ORDER) {
    const group = bySeverity.get(severity);
    if (group) root.append(severitySection(severity, group));
  }
}

function summary(report: Report): HTMLElement {
  const line = el("p", "report-summary");
  const target = report.minecraftVersion ? ` for Minecraft ${report.minecraftVersion}` : "";
  const count = report.findings.length;
  const verdict =
    count === 0 ? "no conflicts found" : `${count} ${count === 1 ? "finding" : "findings"}`;
  line.append(
    `Scanned ${report.jars} ${report.jars === 1 ? "jar" : "jars"}`,
    ` (${report.fabricMods} Fabric ${report.fabricMods === 1 ? "mod" : "mods"})${target}: `,
    strong(verdict, count === 0 ? "ok" : "")
  );
  return line;
}

function allClear(): HTMLElement {
  const box = el("div", "all-clear");
  box.append(
    el("p", "ac-head", "Ready to launch."),
    el("p", "ac-body", "Every dependency is present, every version range holds, and no pass found a conflict.")
  );
  return box;
}

/** One count tile per severity present; clicking hides or shows that severity's section. */
function tiles(bySeverity: Map<Severity, Finding[]>, root: HTMLElement): HTMLElement {
  const row = el("div", "tiles");
  for (const severity of SEVERITY_ORDER) {
    const count = bySeverity.get(severity)?.length ?? 0;
    const tile = document.createElement("button");
    tile.type = "button";
    tile.className = "tile";
    tile.dataset.severity = severity;
    tile.disabled = count === 0;
    tile.setAttribute("aria-pressed", "true");
    tile.append(
      el("span", "tile-count", String(count)),
      el("span", "tile-label", SEVERITY_LABEL[severity])
    );
    if (count > 0) {
      tile.title = `Hide or show ${SEVERITY_LABEL[severity].toLowerCase()} findings`;
      tile.addEventListener("click", () => {
        const shown = tile.getAttribute("aria-pressed") === "true";
        tile.setAttribute("aria-pressed", String(!shown));
        root
          .querySelectorAll<HTMLElement>(`section.severity-group[data-severity="${severity}"]`)
          .forEach((section) => (section.hidden = shown));
      });
    }
    row.append(tile);
  }
  return row;
}

function severitySection(severity: Severity, findings: Finding[]): HTMLElement {
  const section = el("section", "severity-group");
  section.dataset.severity = severity;
  const head = el("h2", "group-head");
  head.append(
    chip(severity),
    ` ${findings.length} ${findings.length === 1 ? "finding" : "findings"} `,
    el("span", "group-meaning", SEVERITY_MEANING[severity])
  );
  section.append(head);
  for (const finding of findings) section.append(findingRow(finding));
  return section;
}

function findingRow(finding: Finding): HTMLElement {
  const details = document.createElement("details");
  details.className = "finding";
  details.dataset.severity = finding.severity;
  const summaryEl = document.createElement("summary");
  summaryEl.append(el("code", "finding-type", finding.type), el("span", "finding-mods", finding.mods.join(", ")));
  const body = el("div", "finding-body");
  body.append(el("p", "finding-problem", finding.problem), fixLine(finding.fix));
  details.append(summaryEl, body);
  return details;
}

function fixLine(fix: string): HTMLElement {
  const line = el("p", "finding-fix");
  line.append(el("span", "fix-label", "Fix"), ` ${fix}`);
  return line;
}

function chip(severity: Severity): HTMLElement {
  const chipEl = el("span", "chip", SEVERITY_LABEL[severity]);
  chipEl.dataset.severity = severity;
  return chipEl;
}

function strong(text: string, className: string): HTMLElement {
  const node = document.createElement("strong");
  if (className) node.className = className;
  node.textContent = text;
  return node;
}

function el(tag: string, className: string, text?: string): HTMLElement {
  const node = document.createElement(tag);
  node.className = className;
  if (text !== undefined) node.textContent = text;
  return node;
}
