export type Severity = "HIGH" | "MEDIUM" | "LOW" | "POTENTIAL";

export interface Finding {
  type: string;
  severity: Severity;
  mods: string[];
  problem: string;
  fix: string;
}

export type Loader = "fabric" | "forge" | "neoforge";

export interface Report {
  jars: number;
  mods: number;
  loader: Loader;
  minecraftVersion?: string;
  findings: Finding[];
}
