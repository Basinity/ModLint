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

const form = document.getElementById("upload-form") as HTMLFormElement;
const dropZone = document.getElementById("drop-zone") as HTMLElement;
const dzNote = document.getElementById("dz-note") as HTMLElement;
const fileInput = document.getElementById("file-input") as HTMLInputElement;
const pickButton = document.getElementById("pick-files") as HTMLButtonElement;
const mcVersionInput = document.getElementById("mc-version") as HTMLInputElement;
const analyzeButton = document.getElementById("analyze") as HTMLButtonElement;
const status = document.getElementById("status") as HTMLElement;
const reportRoot = document.getElementById("report") as HTMLElement;
const themeToggle = document.getElementById("theme-toggle") as HTMLButtonElement;

const IDLE_NOTE = dzNote.textContent ?? "";
let staged: File[] = [];
let analyzing = false;

function acceptable(name: string): boolean {
  const lower = name.toLowerCase();
  return lower.endsWith(".jar") || lower.endsWith(".zip");
}

function setStaged(files: File[]): void {
  staged = files.filter((file) => acceptable(file.name));
  analyzeButton.disabled = staged.length === 0 || analyzing;
  if (staged.length === 0) {
    dzNote.textContent = IDLE_NOTE;
    return;
  }
  const jars = staged.filter((file) => file.name.toLowerCase().endsWith(".jar")).length;
  const zips = staged.length - jars;
  const parts: string[] = [];
  if (jars > 0) parts.push(`${jars} ${jars === 1 ? "jar" : "jars"}`);
  if (zips > 0) parts.push(`${zips} ${zips === 1 ? "zip" : "zips"}`);
  dzNote.textContent = `${parts.join(" and ")} staged, ready to analyze`;
}

function showError(message: string): void {
  status.textContent = message;
  status.dataset.kind = "error";
}

function clearStatus(): void {
  status.textContent = "";
  delete status.dataset.kind;
}

/** Walks dropped directory entries so a whole mods folder can be dropped as-is. */
async function collectDropped(dataTransfer: DataTransfer): Promise<File[]> {
  const entries = Array.from(dataTransfer.items)
    .map((item) => item.webkitGetAsEntry?.())
    .filter((entry): entry is FileSystemEntry => entry !== null && entry !== undefined);
  if (entries.length === 0) return Array.from(dataTransfer.files);
  const files: File[] = [];
  for (const entry of entries) await walk(entry, files);
  return files;
}

async function walk(entry: FileSystemEntry, out: File[]): Promise<void> {
  if (entry.isFile) {
    if (!acceptable(entry.name)) return;
    const file = await new Promise<File>((resolve, reject) =>
      (entry as FileSystemFileEntry).file(resolve, reject)
    );
    out.push(file);
  } else if (entry.isDirectory) {
    const reader = (entry as FileSystemDirectoryEntry).createReader();
    for (;;) {
      const batch = await new Promise<FileSystemEntry[]>((resolve, reject) =>
        reader.readEntries(resolve, reject)
      );
      if (batch.length === 0) break;
      for (const child of batch) await walk(child, out);
    }
  }
}

dropZone.addEventListener("dragover", (event) => {
  event.preventDefault();
  dropZone.classList.add("dragging");
});
dropZone.addEventListener("dragleave", () => dropZone.classList.remove("dragging"));
dropZone.addEventListener("drop", async (event) => {
  event.preventDefault();
  dropZone.classList.remove("dragging");
  if (analyzing || !event.dataTransfer) return;
  clearStatus();
  setStaged(await collectDropped(event.dataTransfer));
  if (staged.length === 0) showError("Nothing usable in that drop. ModLint takes .jar files or a .zip of a mods folder.");
});
dropZone.addEventListener("keydown", (event) => {
  if (event.key === "Enter" || event.key === " ") {
    event.preventDefault();
    fileInput.click();
  }
});
pickButton.addEventListener("click", () => fileInput.click());
fileInput.addEventListener("change", () => {
  clearStatus();
  setStaged(Array.from(fileInput.files ?? []));
});

form.addEventListener("submit", (event) => {
  event.preventDefault();
  if (staged.length === 0 || analyzing) return;
  analyze();
});

function analyze(): void {
  analyzing = true;
  analyzeButton.disabled = true;
  document.body.dataset.state = "analyzing";
  clearStatus();
  reportRoot.hidden = true;

  const body = new FormData();
  for (const file of staged) body.append("files", file, file.name);
  const mcVersion = mcVersionInput.value.trim();
  if (mcVersion) body.append("mcVersion", mcVersion);

  const request = new XMLHttpRequest();
  request.open("POST", "/api/analyze");
  request.responseType = "text";
  request.upload.addEventListener("progress", (event) => {
    if (event.lengthComputable && event.total > 0) {
      const percent = Math.round((event.loaded / event.total) * 100);
      dzNote.textContent = percent < 100 ? `uploading ${percent}%` : "analyzing…";
    }
  });
  request.addEventListener("load", () => finish(request));
  request.addEventListener("error", () => fail("The upload failed. Check your connection and try again."));
  request.addEventListener("timeout", () => fail("The upload timed out. Try again, or try a smaller pack."));
  dzNote.textContent = "uploading…";
  request.send(body);
}

function finish(request: XMLHttpRequest): void {
  analyzing = false;
  document.body.dataset.state = "done";
  dzNote.textContent = IDLE_NOTE;
  analyzeButton.disabled = staged.length === 0;
  if (request.status === 200) {
    const report = JSON.parse(request.response) as Report;
    renderReport(reportRoot, report);
    reportRoot.hidden = false;
    reportRoot.scrollIntoView({ behavior: "smooth", block: "start" });
    return;
  }
  let message = `The server answered with HTTP ${request.status}.`;
  try {
    const parsed = JSON.parse(request.response) as { error?: string };
    if (parsed.error) message = parsed.error;
  } catch {
    // Non-JSON body (proxy error page); keep the generic message.
  }
  showError(message);
}

function fail(message: string): void {
  analyzing = false;
  document.body.dataset.state = "done";
  dzNote.textContent = IDLE_NOTE;
  analyzeButton.disabled = staged.length === 0;
  showError(message);
}

function currentTheme(): string {
  return (
    document.documentElement.dataset.theme ??
    (matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light")
  );
}

themeToggle.addEventListener("click", () => {
  const next = currentTheme() === "dark" ? "light" : "dark";
  document.documentElement.dataset.theme = next;
  localStorage.setItem("modlint-theme", next);
});
